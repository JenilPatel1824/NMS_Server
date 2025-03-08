package io.vertx.nms.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.nms.util.Constants;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Database extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    private PgPool pgClient;

// Initializes the database connection and ensures necessary tables exist.
// Sets up an event bus consumer for handling database queries.
    @Override
    public void start(Promise<Void> startPromise)
    {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost(Constants.DB_HOST)
                .setPort(Constants.DB_PORT)
                .setDatabase(Constants.DB_NAME)
                .setUser(Constants.DB_USER)
                .setPassword(Constants.DB_PASSWORD);

        PoolOptions poolOptions = new PoolOptions().setMaxSize(10);

        pgClient = PgPool.pool(vertx, connectOptions, poolOptions);

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
    private void setupEventBusConsumer()
    {
        EventBus eventBus = vertx.eventBus();

        eventBus.<JsonObject>localConsumer(Constants.EVENTBUS_DATABASE_ADDRESS, message ->
        {
            JsonObject request =  message.body();

            String query = request.getString(Constants.QUERY);

            JsonArray params = request.getJsonArray(Constants.PARAMS);

            if (params == null)
            {
                params = new JsonArray();
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

                    JsonObject response = new JsonObject().put(Constants.STATUS, Constants.SUCCESS);

                    if (finalQuery.trim().toLowerCase().startsWith(Constants.DATABASE_OPERATION_SELECT))
                    {
                        response.put(Constants.DATA, resultData);
                    }
                    else
                    {
                        response.put(Constants.MESSAGE, "Query executed successfully");
                    }

                    message.reply(response);
                }
                else
                {
                    logger.error("Database query failed: {}", ar.cause().getMessage());

                    message.fail(1, String.valueOf(new JsonObject().put(Constants.STATUS, "fail").put(Constants.MESSAGE, ar.cause().getMessage())));
                }
            });
        });

        logger.info("DatabaseService is listening on eventbus address: database.query.execute");
    }

    private Future<Void> init()
    {
        Promise<Void> promise = Promise.promise();

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

            CREATE TABLE IF NOT EXISTS provision_data (
                id SERIAL PRIMARY KEY,
                discovery_profile_name TEXT NOT NULL,
                data JSONB NOT NULL,
                polled_at TEXT,
                FOREIGN KEY (discovery_profile_name) REFERENCES discovery_profiles(discovery_profile_name) ON DELETE CASCADE
            );
        """;

        pgClient.query(createTablesQuery).execute(ar ->
        {
            if (ar.succeeded())
            {
                logger.info("Checked and ensured tables exist.");

                promise.complete();

            } else
            {
                logger.error("Failed to create tables: {}", ar.cause().getMessage());

                promise.fail(ar.cause());
            }
        });

        return promise.future();
    }
}

