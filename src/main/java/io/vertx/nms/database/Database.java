package io.vertx.nms.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.nms.util.Constants;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class Database extends AbstractVerticle
{
    private static class CacheEntry
    {
        final JsonObject response;

        final Set<String> tables;

        CacheEntry(JsonObject response, Set<String> tables)
        {
            this.response = response;

            this.tables = tables;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    private PgPool pgClient;

    private Cache<String, CacheEntry> cache;

    private final Map<String, Set<String>> tableToCacheKeys = new HashMap<>();

    @Override
    public void start(Promise<Void> startPromise)
    {
        var connectOptions = new PgConnectOptions()
                .setHost(Constants.DB_HOST)
                .setPort(Constants.DB_PORT)
                .setDatabase(Constants.DB_NAME)
                .setUser(Constants.DB_USER)
                .setPassword(Constants.DB_PASSWORD);

        var poolOptions = new PoolOptions().setMaxSize(10);

        pgClient = PgPool.pool(vertx, connectOptions, poolOptions);

        cache = Caffeine.newBuilder()
                .maximumSize(30)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .removalListener((key, value, cause) ->
                {
                    if (value != null)
                    {
                        var entry = (CacheEntry) value;

                        entry.tables.forEach(table ->
                        {
                            var keys = tableToCacheKeys.get(table);

                            if (keys != null)
                            {
                                keys.remove(key);

                                if (keys.isEmpty())
                                {
                                    tableToCacheKeys.remove(table);
                                }
                            }
                        });
                    }
                })
                .build();

        init().onComplete(ar ->
        {
            if (ar.succeeded())
            {
                setupEventBusConsumer();

                logger.info("DatabaseVerticle Eventbus is ready to listen");

                startPromise.complete();
            }
            else
            {
                startPromise.fail(ar.cause());
            }
        });
    }


    //Sets up an EventBus consumer to handle database queries, execute them using the PostgreSQL client,
    //cache SELECT queries, and invalidate cache on mutations
    private void setupEventBusConsumer()
    {
        var eventBus = vertx.eventBus();

        eventBus.<JsonObject>localConsumer(Constants.EVENTBUS_DATABASE_ADDRESS, message ->
        {
            var request = message.body();

            var query = request.getString(Constants.QUERY);

            logger.info("Executing query: {}", query);

            var params = request.getJsonArray(Constants.PARAMS);

            if (params == null)
            {
                params = new JsonArray();
            }

            var isInsertOrUpdate = query.trim().toLowerCase().startsWith(Constants.INSERT) || query.trim().toLowerCase().startsWith(Constants.UPDATE);

            var isSelect = query.toLowerCase().trim().startsWith(Constants.SELECT) || query.toLowerCase().trim().startsWith(Constants.WITH);

            var isMutation = isInsertOrUpdate || query.toLowerCase().trim().startsWith(Constants.DELETE);

            if (isInsertOrUpdate && !query.toLowerCase().contains("returning id"))
            {
                query += " RETURNING id";
            }

            if (isSelect)
            {
                var cacheKey = generateCacheKey(query, params);

                var cachedResponse = cache.getIfPresent(cacheKey);

                if (cachedResponse != null)
                {
                    logger.info("Cache hit for query: {}", query);

                    message.reply(cachedResponse.response);

                    return;
                }
            }

            var finalQuery = query;

            var tupleParams = Tuple.tuple();

            for (int i = 0; i < params.size(); i++)
            {
                var paramValue = params.getValue(i);

                if (query.toLowerCase().contains(Constants.POLLED_AT) && paramValue instanceof String)
                {
                    tupleParams.addLocalDateTime(LocalDateTime.parse((String) paramValue, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                }
                else
                {
                    tupleParams.addValue(paramValue);
                }
            }

            var finalParams = params;

            pgClient.preparedQuery(finalQuery).execute(tupleParams, ar ->
            {
                if (ar.succeeded())
                {
                    var rows = ar.result();

                    var response = new JsonObject().put(Constants.STATUS, Constants.SUCCESS);

                    if (isMutation)
                    {
                        var affectedTables = parseTablesForMutation(finalQuery);

                        affectedTables.forEach(table ->
                        {
                            logger.info("Invalidating cache for table: {}", table);

                            var keys = tableToCacheKeys.getOrDefault(table, Collections.emptySet());

                            new ArrayList<>(keys).forEach(cache::invalidate);
                        });
                    }

                    if (isSelect)
                    {
                        var rowJson = new JsonObject();

                        var resultData = new JsonArray();

                        rows.forEach(row ->
                        {
                            rowJson.clear();

                            IntStream.range(0, row.size()).forEach(i ->
                            {
                                var columnName = row.getColumnName(i);

                                var value = row.getValue(i);

                                if (Constants.POLLED_AT.equalsIgnoreCase(columnName) && value instanceof LocalDateTime)
                                {
                                    rowJson.put(columnName, ((LocalDateTime) value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                                }
                                else
                                {
                                    rowJson.put(columnName, value);
                                }
                            });

                            resultData.add(rowJson.copy());
                        });

                        var cacheKey = generateCacheKey(finalQuery, finalParams);

                        var tables = parseTablesForSelect(finalQuery);

                        var entry = new CacheEntry(response.put(Constants.DATA, resultData), tables);

                        logger.info("Inserting into cache for query: {}", finalQuery);

                        cache.put(cacheKey, entry);

                        tables.forEach(table ->
                        {
                            logger.info("Adding cache key for table: {}", table);

                            tableToCacheKeys.computeIfAbsent(table, k -> new HashSet<>()).add(cacheKey);
                        });
                    }
                    else if (isInsertOrUpdate)
                    {
                        if (rows.iterator().hasNext())
                        {
                            var id = rows.iterator().next().getInteger(Constants.ID);

                            response.put(Constants.ID, id);
                        }

                        response.put(Constants.MESSAGE, Constants.MESSAGE_QUERY_SUCCESSFUL);
                    }
                    else
                    {
                        response.put(Constants.MESSAGE, Constants.MESSAGE_QUERY_SUCCESSFUL);
                    }

                    message.reply(response);
                }
                else
                {
                    logger.error("Database query failed: {}", ar.cause().getMessage());

                    message.fail(1, new JsonObject()
                            .put(Constants.STATUS, Constants.FAIL)
                            .put(Constants.MESSAGE, ar.cause().getMessage())
                            .encode());
                }
            });
        });

        logger.info("DatabaseService is listening on eventbus address: database.query.execute");
    }


    // Initializes the database by ensuring required tables exist.
    // Creates tables if they do not already exist and sets up necessary constraints.
    private Future<Object> init() {

        var promise = Promise.promise();

        var createTablesQuery = """
            CREATE TABLE IF NOT EXISTS credential_profile (
                id SERIAL PRIMARY KEY,
                credential_profile_name TEXT UNIQUE NOT NULL,
                system_type TEXT NOT NULL,
                credentials JSONB NOT NULL,
                in_use_by INT DEFAULT 0
            );
            
            CREATE TABLE IF NOT EXISTS discovery_profiles (
                id SERIAL PRIMARY KEY,
                discovery_profile_name TEXT UNIQUE NOT NULL,
                credential_profile_id INT,
                ip TEXT NOT NULL,
                status BOOLEAN,
                FOREIGN KEY (credential_profile_id) REFERENCES credential_profile(id) ON DELETE SET NULL
            );
            
            CREATE TABLE IF NOT EXISTS provisioning_jobs (
                id SERIAL PRIMARY KEY,
                credential_profile_id INT,
                ip TEXT NOT NULL UNIQUE,
                FOREIGN KEY (credential_profile_id) REFERENCES credential_profile(id) ON DELETE SET NULL
            );
            
            CREATE TABLE IF NOT EXISTS provision_data (
                id SERIAL PRIMARY KEY,
                job_id INT NOT NULL REFERENCES provisioning_jobs(id) ON DELETE CASCADE,
                data JSONB NOT NULL,
                polled_at TIMESTAMP
            );
        """;

        pgClient.query(createTablesQuery).execute(ar ->
        {
            if (ar.succeeded())
            {
                logger.info("Checked and ensured tables exist.");

                promise.complete();
            }
            else
            {
                logger.error("Failed to create tables: {}", ar.cause().getMessage());

                promise.fail(ar.cause());
            }
        });

        return promise.future();
    }

    // Generates a cache key by hashing the query and parameters.
    // @param query  The SQL query string.
    // @param params The parameters used in the query as a JsonArray.
    // @return A SHA-1 hashed string representing the cache key.
    private String generateCacheKey(String query, JsonArray params)
    {
        var key = query + params.toString();

        return DigestUtils.sha1Hex(key);
    }

    // Extracts table names from a SELECT query using regex pattern matching.
    // @param query The SQL SELECT query string.
    // @return A set of table names found in the query.
    private Set<String> parseTablesForSelect(String query)
    {
        var tables = new HashSet<String>();

        var pattern = Pattern.compile("(?:from|join)\\s+([^\\s,)(]+)", Pattern.CASE_INSENSITIVE);

        var matcher = pattern.matcher(query.toLowerCase());

        while (matcher.find())
        {
            var table = matcher.group(1).replaceAll("\"", "");

            if (table.contains("."))
            {
                table = table.substring(table.lastIndexOf('.') + 1);
            }

            tables.add(table);
        }
        return tables;
    }

    // Extracts table names from an INSERT, UPDATE, or DELETE query using regex pattern matching.
    // @param query The SQL mutation query (INSERT, UPDATE, or DELETE).
    // @return A set of table names affected by the mutation.
    private Set<String> parseTablesForMutation(String query)
    {
        var lowerQuery = query.toLowerCase().trim();

       var tables = new HashSet<String>();

        Matcher matcher;

        if (lowerQuery.startsWith(Constants.INSERT))
        {
            var insertPattern = Pattern.compile("insert\\s+into\\s+((\"[^\"]+\"|[^\\s(]+))", Pattern.CASE_INSENSITIVE);

            matcher = insertPattern.matcher(lowerQuery);

            if (matcher.find())
            {
                var table = matcher.group(1).replaceAll("\"", "");

                if (table.contains("."))
                {
                    table = table.substring(table.lastIndexOf('.') + 1);
                }

                tables.add(table);
            }
        }
        else if (lowerQuery.startsWith(Constants.UPDATE))
        {
            matcher = Pattern.compile("update\\s+((\"[^\"]+\"|[^\\s]+))", Pattern.CASE_INSENSITIVE).matcher(lowerQuery);

            if (matcher.find())
            {
                var table = matcher.group(1).replaceAll("\"", "");

                if (table.contains("."))
                {
                    table = table.substring(table.lastIndexOf('.') + 1);
                }

                tables.add(table);
            }
        }
        else if (lowerQuery.startsWith(Constants.DELETE))
        {
            matcher = Pattern.compile("delete\\s+from\\s+((\"[^\"]+\"|[^\\s]+))", Pattern.CASE_INSENSITIVE).matcher(lowerQuery);

            if (matcher.find())
            {
                var table = matcher.group(1).replaceAll("\"", "");

                if (table.contains("."))
                {
                    table = table.substring(table.lastIndexOf('.') + 1);
                }

                tables.add(table);
            }
        }

        logger.debug("Parsed mutation tables for query '{}': {}", query, tables);

        return tables;
    }
}