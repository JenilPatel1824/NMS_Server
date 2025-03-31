package io.vertx.nms.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.nms.util.Constants;
import io.vertx.nms.util.Util;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

public class Database extends AbstractVerticle
{
    private static final int MAX_CACHEABLE_ROWS = 1000;

    private static final int MAX_CACHE_SIZE = 20;

    private static final long CACHE_EXPIRATION_MS = 30 * 60 * 1000;

    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000;

    private record CacheEntry(JsonObject response, Set<String> tables, long timestamp) {}

    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    private PgPool pgClient;

    private final Map<String, Set<String>> tableToCacheKeys = new ConcurrentHashMap<>();

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private final AtomicBoolean cleanupScheduled = new AtomicBoolean(false);

    private static final String INSERT_INTO_PROVISION_DATA = "insert into provision_data";

    private static final String RETURNING_ID = "returning id";

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

        scheduleCacheCleanup();

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

    // Custom cache implementation methods
    private void scheduleCacheCleanup()
    {
        if (cleanupScheduled.compareAndSet(false, true))
        {
            vertx.setPeriodic(CLEANUP_INTERVAL_MS, id ->
            {
                var now = System.currentTimeMillis();

                cache.entrySet().removeIf(entry ->
                {
                    var expired = now - entry.getValue().timestamp() > CACHE_EXPIRATION_MS;

                    if (expired)
                    {
                        removeFromTableMapping(entry.getKey(), entry.getValue().tables());
                    }
                    return expired;
                });
            });
        }
    }

    // Retrieves a cache entry if it is still valid; otherwise, removes expired entries.
    // @param key The cache key to look up.
    // @return The cached entry if valid, otherwise null.
    private CacheEntry getFromCache(String key)
    {
        var entry = cache.get(key);

        if (entry != null && System.currentTimeMillis() - entry.timestamp() < CACHE_EXPIRATION_MS)
        {
            return entry;
        }
        if (entry != null)
        {
            cache.remove(key);

            removeFromTableMapping(key, entry.tables());
        }
        return null;
    }

    // Stores a response in the cache, evicting the oldest entry if the cache is full.
    // @param key The cache key.
    // @param response The JSON response to store.
    // @param tables The set of tables associated with the cache entry.
    private void putInCache(String key, JsonObject response, Set<String> tables)
    {
        if (cache.size() >= MAX_CACHE_SIZE)
        {
            evictOldestEntry();
        }

        var newEntry = new CacheEntry(response, tables, System.currentTimeMillis());

        cache.put(key, newEntry);

        tables.forEach(table -> tableToCacheKeys.computeIfAbsent(table, k -> ConcurrentHashMap.newKeySet()).add(key));
    }

    private void evictOldestEntry()
    {
        cache.entrySet().stream()
                .min(Comparator.comparingLong(entry -> entry.getValue().timestamp()))
                .ifPresent(oldest ->
                {
                    cache.remove(oldest.getKey());

                    removeFromTableMapping(oldest.getKey(), oldest.getValue().tables());
                });
    }

    private void removeFromTableMapping(String key, Set<String> tables)
    {
        tables.forEach(table ->
                tableToCacheKeys.computeIfPresent(table, (k, keys) ->
                {
                    keys.remove(key);

                    return keys.isEmpty() ? null : keys;
                })
        );
    }

    // Invalidates cache entries associated with the given tables.
    // @param tables The set of tables whose cache entries should be removed.
    private void invalidateCacheForTables(Set<String> tables)
    {
        tables.forEach(table -> tableToCacheKeys.getOrDefault(table, Collections.emptySet()).forEach(cache::remove));
    }

    //Sets up an EventBus consumer to handle database queries, execute them using the PostgreSQL client,
    //cache SELECT queries, and invalidate cache on mutations
    private void setupEventBusConsumer()
    {
        vertx.eventBus().<JsonObject>localConsumer(Constants.EVENTBUS_DATABASE_ADDRESS, message ->
        {
            var query = message.body().getString(Constants.QUERY).toLowerCase().trim();

            var isInsertPollingData = query.startsWith(INSERT_INTO_PROVISION_DATA);

            logger.info("Executing query: {}", query);

            var params = message.body().getJsonArray(Constants.PARAMS);

            if (params == null)
            {
                params = new JsonArray();
            }

            var isInsertOrUpdate = query.startsWith(Constants.INSERT) || query.startsWith(Constants.UPDATE);

            if(!isInsertPollingData)
            {
                if (isInsertOrUpdate && !query.contains(RETURNING_ID))
                {
                    query += " returning id";
                }
            }

            var cacheHit = false;

            if ((query.startsWith(Constants.SELECT) || query.startsWith(Constants.WITH)) && !queryInvolvesProvisionData(query))
            {
                 var entry = getFromCache((Util.generateCacheKey(query, params)));

                if (entry != null)
                {
                    logger.info("Cache hit for query: {}", query);

                    message.reply(entry.response());

                    cacheHit=true;
                }
            }

            if(!cacheHit)
            {
                var finalQuery = query;

                var tupleParams = Tuple.tuple();

                for (int i = 0; i < params.size(); i++)
                {
                    var paramValue = params.getValue(i);

                    tupleParams.addValue(paramValue);

                }

                var finalParams = params;

                vertx.executeBlocking(promise ->
                {
                    pgClient.preparedQuery(finalQuery).execute(tupleParams, ar ->
                    {
                        if (ar.succeeded())
                        {
                            var rows = ar.result();

                            var response = new JsonObject().put(Constants.STATUS, Constants.SUCCESS);

                            if (isInsertOrUpdate || finalQuery.startsWith(Constants.DELETE))
                            {
                                invalidateCacheForTables(Util.parseTablesForMutation(finalQuery));
                            }

                            if (finalQuery.startsWith(Constants.SELECT) || finalQuery.startsWith(Constants.WITH))
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

                                        rowJson.put(columnName, value);
                                    });

                                    resultData.add(rowJson.copy());
                                });

                                if (resultData.size() <= MAX_CACHEABLE_ROWS && !queryInvolvesProvisionData(finalQuery))
                                {
                                    logger.info("Inserting into cache for query: {} with {} rows", finalQuery, resultData.size());

                                    putInCache(Util.generateCacheKey(finalQuery, finalParams),
                                            response.put(Constants.DATA, resultData),
                                            Util.parseTablesForSelect(finalQuery));
                                }
                                else
                                {
                                    logger.info("Skipping cache for query: {} - either result too large ({} rows) or involves provision_data", finalQuery, resultData.size());

                                    response.put(Constants.DATA, resultData);
                                }

                            }
                            else if (isInsertOrUpdate && !isInsertPollingData)
                            {
                                if (rows.iterator().hasNext())
                                {
                                    response.put(Constants.ID, rows.iterator().next().getInteger(Constants.ID));
                                }

                                response.put(Constants.MESSAGE, Constants.MESSAGE_OPERATION_SUCCESSFUL);

                            }
                            else
                            {
                                response.put(Constants.MESSAGE, Constants.MESSAGE_OPERATION_SUCCESSFUL);
                            }

                            promise.complete(response);

                        }
                        else
                        {
                            logger.error("Database query failed: {}", ar.cause().getMessage());

                            promise.fail(ar.cause());
                        }
                    });

                },false, res -> {

                    if (res.succeeded())
                    {
                        if (!isInsertPollingData)
                        {
                            message.reply( res.result());
                        }
                        else
                        {
                            logger.info("Polling stored Successful");
                        }
                    }
                    else
                    {
                        if (!isInsertPollingData)
                        {
                            message.fail(1, new JsonObject()
                                    .put(Constants.STATUS, Constants.FAIL)
                                    .put(Constants.MESSAGE, res.cause().getMessage())
                                    .encode());
                        }
                        else
                        {
                            logger.error("Database query failed: {}", res.cause().getMessage());
                        }
                    }
                });
            }
        });
    }

    // Initializes the database by ensuring required tables exist.
    // Creates tables if they do not already exist and sets up necessary constraints.
    private Future<Object> init()
    {
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
                polled_at BIGINT
            );

            CREATE INDEX IF NOT EXISTS idx_credential_profile_in_use_by ON credential_profile (in_use_by);
            CREATE INDEX IF NOT EXISTS idx_discovery_profiles_credential_id ON discovery_profiles (credential_profile_id);
            CREATE INDEX IF NOT EXISTS idx_prov_jobs_cred_id ON provisioning_jobs (credential_profile_id);
            CREATE INDEX IF NOT EXISTS idx_provision_data_job_polled ON provision_data (job_id, polled_at);
            CREATE INDEX IF NOT EXISTS idx_provision_data_interface_errors ON provision_data ((COALESCE(((data -> 'interfaces'::text) ->> 'interface.sent.error.packets'::text)::integer, 0) + COALESCE(((data -> 'interfaces'::text) ->> 'interface.received.error.packets'::text)::integer, 0)));
            CREATE INDEX IF NOT EXISTS idx_provision_data_interface_speed ON provision_data (COALESCE((NULLIF(((data -> 'interfaces'::text) ->> 'interface.speed'::text), ''::text))::bigint, 0)) WHERE COALESCE((NULLIF(((data -> 'interfaces'::text) ->> 'interface.speed'::text), ''::text))::bigint, 0) > 0;
            CREATE INDEX IF NOT EXISTS idx_provision_data_system_uptime ON provision_data ((data->>'system.uptime'));
            CREATE INDEX IF NOT EXISTS idx_provisioning_jobs_deleted ON provisioning_jobs(deleted);
            CREATE INDEX IF NOT EXISTS idx_provision_data_polled_at ON provision_data(polled_at);
            CREATE INDEX IF NOT EXISTS idx_provision_data_jsonb ON provision_data USING GIN (data);
        """;

        return vertx.executeBlocking(promise ->
        {
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
        });
    }

    // Checks if a query involves the provision_data table
    // @param query The SQL query string.
    // @return True if the query involves the provision_data table, false otherwise.
    private boolean queryInvolvesProvisionData(String query)
    {
        return query.contains(Constants.DATABASE_TABLE_PROVISION_DATA);
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
                    cache.clear();

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
            cache.clear();

            stopPromise.complete();
        }
    }
}