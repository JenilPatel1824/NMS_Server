package io.vertx.nms.database;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public class Database extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    private PgPool pgClient;

    // Initializes the database connection and ensures necessary tables exist.
    // Sets up an event bus consumer for handling database queries.
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
        var eventBus = vertx.eventBus();

        eventBus.<JsonObject>localConsumer(Constants.EVENTBUS_DATABASE_ADDRESS, message ->
        {
            var request = message.body();

            var query = request.getString(Constants.QUERY);

            logger.info("Executing query: {}", query);

            var params = request.getJsonArray(Constants.PARAMS);

            if (request.getJsonArray(Constants.PARAMS) == null)
            {
                params = new JsonArray();
            }

            boolean isInsertOrUpdate = query.trim().toLowerCase().startsWith(Constants.INSERT) || query.trim().toLowerCase().startsWith(Constants.UPDATE);

            if (isInsertOrUpdate && !query.toLowerCase().contains("returning id"))
            {
                query += " RETURNING id";
            }

            var finalQuery = query;

            var tupleParams = Tuple.tuple();

            for (int i = 0; i < params.size(); i++)
            {
                var paramValue = params.getValue(i);

                if ((query.toLowerCase().contains(Constants.POLLED_AT) && paramValue instanceof String) || (query.toLowerCase().contains("updated_at") && paramValue instanceof String))
                {
                    tupleParams.addLocalDateTime(LocalDateTime.parse((String) paramValue, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                }
                else
                {
                    tupleParams.addValue(paramValue);
                }
            }

            pgClient.preparedQuery(query).execute(tupleParams, ar ->
            {
                if (ar.succeeded())
                {
                    var rows = ar.result();

                    var response = new JsonObject().put(Constants.STATUS, Constants.SUCCESS);

                    if (finalQuery.trim().toLowerCase().startsWith(Constants.SELECT) || finalQuery.trim().toLowerCase().startsWith(Constants.WITH))
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

                        response.put(Constants.DATA, resultData);
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

                    message.fail(1, new JsonObject().put(Constants.STATUS, Constants.FAIL).put(Constants.MESSAGE, ar.cause().getMessage()).encode());
                }
            });
        });

        logger.info("DatabaseService is listening on eventbus address: database.query.execute");
    }

    private Future<Object> init()
    {
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
                ip TEXT NOT NULL,
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

            } else
            {
                logger.error("Failed to create tables: {}", ar.cause().getMessage());

                promise.fail(ar.cause());
            }
        });

        return promise.future();
    }
}

