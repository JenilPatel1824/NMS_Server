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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class Database extends AbstractVerticle
{
    private static final int MAX_CACHEABLE_ROWS = 10_000;

    private record CacheEntry(JsonObject response, Set<String> tables) {}

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
                .maximumSize(20)
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

                logger.info(" DatabaseVerticle Eventbus is ready to listen");

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

            boolean shouldCheckCache = isSelect && !queryInvolvesProvisionData(query);

            if (shouldCheckCache)
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

                        boolean shouldCache = resultData.size() <= MAX_CACHEABLE_ROWS && !queryInvolvesProvisionData(finalQuery);

                        if (shouldCache)
                        {
                            var cacheKey = generateCacheKey(finalQuery, finalParams);

                            var tables = parseTablesForSelect(finalQuery);

                            var entry = new CacheEntry(response.put(Constants.DATA, resultData), tables);

                            logger.info("Inserting into cache for query: {} with {} rows", finalQuery, resultData.size());

                            cache.put(cacheKey, entry);

                            tables.forEach(table ->
                            {
                                tableToCacheKeys.computeIfAbsent(table, k -> new HashSet<>()).add(cacheKey);
                            });
                        }
                        else
                        {
                            logger.info("Skipping cache for query: {} - either result too large ({} rows) or involves provision_data", finalQuery, resultData.size());

                            response.put(Constants.DATA, resultData);
                        }
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
    }

    // Initializes the database by ensuring required tables exist.
    // Creates tables if they do not already exist and sets up necessary constraints.
    private Future<Object> init()
    {

        var promise = Promise.promise();

        var createTablesAndIndexesQuery = """
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
                port INT NOT NULL,
                status BOOLEAN,
                FOREIGN KEY (credential_profile_id) REFERENCES credential_profile(id) ON DELETE SET NULL
            );

            CREATE TABLE IF NOT EXISTS provisioning_jobs (
                id SERIAL PRIMARY KEY,
                credential_profile_id INT,
                ip TEXT NOT NULL UNIQUE,
                port INT NOT NULL,
                deleted BOOLEAN DEFAULT FALSE,
                FOREIGN KEY (credential_profile_id) REFERENCES credential_profile(id) ON DELETE SET NULL
            );

            CREATE TABLE IF NOT EXISTS provision_data (
                id SERIAL PRIMARY KEY,
                job_id INT NOT NULL REFERENCES provisioning_jobs(id) ON DELETE CASCADE,
                data JSONB NOT NULL,
                polled_at TIMESTAMP
            );

            CREATE INDEX IF NOT EXISTS idx_credential_profile_in_use_by ON credential_profile (in_use_by);
            CREATE INDEX IF NOT EXISTS idx_discovery_profiles_credential_id ON discovery_profiles (credential_profile_id);
            CREATE INDEX IF NOT EXISTS idx_prov_jobs_cred_id ON provisioning_jobs (credential_profile_id);
            CREATE INDEX IF NOT EXISTS idx_provision_data_job_polled ON provision_data (job_id, polled_at);
            CREATE INDEX IF NOT EXISTS idx_provision_data_interface_errors ON provision_data ((COALESCE(((data -> 'interfaces'::text) ->> 'interface.sent.error.packets'::text)::integer, 0) + COALESCE(((data -> 'interfaces'::text) ->> 'interface.received.error.packets'::text)::integer, 0)));
            CREATE INDEX IF NOT EXISTS idx_provision_data_interface_speed ON provision_data (COALESCE((NULLIF(((data -> 'interfaces'::text) ->> 'interface.speed'::text), ''::text))::bigint, 0)) WHERE COALESCE((NULLIF(((data -> 'interfaces'::text) ->> 'interface.speed'::text), ''::text))::bigint, 0) > 0;
            CREATE INDEX IF NOT EXISTS idx_provision_data_system_uptime ON provision_data ((data->>'system.uptime'));
        """;

        pgClient.query(createTablesAndIndexesQuery).execute(ar ->
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

    // Checks if a query involves the provision_data table
    // @param query The SQL query string.
    // @return True if the query involves the provision_data table, false otherwise.
    private boolean queryInvolvesProvisionData(String query)
    {
        return query.toLowerCase().contains(Constants.DATABASE_TABLE_PROVISION_DATA);
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

        return tables;
    }

    @Override
    public void stop(Promise<Void> stopPromise)
    {
        if (pgClient != null)
        {
            pgClient.close().onComplete(ar ->
            {
                        if (ar.succeeded())
                        {
                            cache.invalidateAll();

                            stopPromise.complete();
                        }
                        else
                        {
                            stopPromise.fail(ar.cause());
                        }
                    });
        }
        else
        {
            cache.invalidateAll();

            stopPromise.complete();
        }
    }
}