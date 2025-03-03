package io.vertx.nms.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.nms.config.Config;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(DatabaseVerticle.class);

    private PgPool pgClient;

// Initializes the database connection and ensures necessary tables exist.
// Sets up an event bus consumer for handling database queries.
    @Override
    public void start(Promise<Void> startPromise)
    {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost(Config.DB_HOST)
                .setPort(Config.DB_PORT)
                .setDatabase(Config.DB_NAME)
                .setUser(Config.DB_USER)
                .setPassword(Config.DB_PASSWORD);

        PoolOptions poolOptions = new PoolOptions().setMaxSize(10);

        pgClient = PgPool.pool(vertx, connectOptions, poolOptions);

        EventBus eventBus = vertx.eventBus();

        String createTablesQuery = """
            CREATE TABLE IF NOT EXISTS credential_profile (
                id SERIAL PRIMARY KEY,
                credential_profile_name TEXT UNIQUE NOT NULL,
                system_type TEXT NOT NULL,
                credentials JSONB NOT NULL
            );

            CREATE TABLE IF NOT EXISTS discovery_profiles (
                id SERIAL PRIMARY KEY,
                discovery_profile_name TEXT UNIQUE NOT NULL,
                credential_profile_name TEXT NOT NULL,
                ip INET NOT NULL,
                discovery BOOLEAN,
                provision BOOLEAN,
                FOREIGN KEY (credential_profile_name) REFERENCES credential_profile(credential_profile_name) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS discovery_data (
                id SERIAL PRIMARY KEY,
                discovery_profile_name TEXT NOT NULL,
                data JSONB NOT NULL,
                inserted_at TIMESTAMPTZ DEFAULT NOW(),
                FOREIGN KEY (discovery_profile_name) REFERENCES discovery_profiles(discovery_profile_name) ON DELETE CASCADE
            );
        """;

        pgClient.query(createTablesQuery).execute(ar ->
        {
            if (ar.succeeded())
            {
                logger.info("Checked and ensured tables exist.");

                startPromise.complete();

            } else
            {
                logger.error("Failed to create tables: {}", ar.cause().getMessage());

                startPromise.fail(ar.cause());
            }
        });

        eventBus.consumer("database.query.execute", message ->
        {
            JsonObject request = (JsonObject) message.body();

            String query = request.getString("query");

            JsonArray params = request.getJsonArray("params");

            if (params == null)
            {
                params = new JsonArray();
            }

            for (int i = 0; i < params.size(); i++)
            {
                query = query.replaceFirst("\\?", "\\$" + (i + 1));
            }

            logger.info("Executing Query: {}", query);

            String finalQuery = query;

            pgClient.preparedQuery(query).execute(Tuple.tuple(params.getList()), ar ->
            {
                if (ar.succeeded())
                {
                    RowSet<Row> rows = ar.result();

                    JsonArray resultData = new JsonArray();

                    for (Row row : rows)
                    {
                        JsonObject rowJson = new JsonObject();

                        for (int i = 0; i < row.size(); i++)
                        {
                            rowJson.put(row.getColumnName(i), row.getValue(i));
                        }

                        resultData.add(rowJson);
                    }
                    JsonObject response = new JsonObject().put("status", "success");

                    if (finalQuery.trim().toLowerCase().startsWith("select"))
                    {
                        response.put("data", resultData);
                    }
                    else
                    {
                        response.put("message", "Query executed successfully");
                    }

                    message.reply(response);
                }
                else
                {
                    logger.error("Database query failed: {}", ar.cause().getMessage());

                    message.fail(1, String.valueOf(new JsonObject().put("status", "fail").put("message", ar.cause().getMessage())));
                }
            });
        });

        logger.info("DatabaseService is listening on eventbus address: database.query.execute");
    }
}
