package io.vertx.nms.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryExecutor extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(QueryExecutor.class);

    @Override
    public void start(Promise<Void> startPromise)
    {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost("localhost")
                .setPort(5432)
                .setDatabase("NMS_Lite")
                .setUser("admin")
                .setPassword("admin");

        PoolOptions poolOptions = new PoolOptions().setMaxSize(10);

        PgPool pgClient = PgPool.pool(vertx, connectOptions, poolOptions);

        EventBus eventBus = vertx.eventBus();

        String createCredentialTableQuery = "CREATE TABLE IF NOT EXISTS credential (" +
                "id SERIAL PRIMARY KEY," +
                "credential_profile_name VARCHAR(255) UNIQUE NOT NULL," +
                "community VARCHAR(255) NOT NULL," +
                "version VARCHAR(50) NOT NULL," +
                "system_type VARCHAR(100) NOT NULL" +
                ");";

        pgClient.query(createCredentialTableQuery).execute(ar ->
        {
            if (ar.succeeded())
            {
                logger.info("Checked credential table existence. Created if not present.");
            }
            else
            {
                logger.error("Failed to check or create credential table: {}", ar.cause().getMessage());
            }
        });

        String createDiscoveryTableQuery = "CREATE TABLE IF NOT EXISTS discovery (" +
                "id SERIAL PRIMARY KEY," +
                "discovery_profile_name VARCHAR(255) UNIQUE NOT NULL," +
                "credential_profile_name VARCHAR(255) REFERENCES credential(credential_profile_name)," +
                "ip VARCHAR(50) NOT NULL," +
                "discovery BOOLEAN ," +
                "provision BOOLEAN " +
                ");";

        pgClient.query(createDiscoveryTableQuery).execute(ar ->
        {
            if (ar.succeeded())
            {
                logger.info("Checked discovery table existence. Created if not present.");
            }
            else
            {
                logger.error("Failed to check or create discovery table: {}", ar.cause().getMessage());
            }
        });

        logger.info("DatabaseService is listening on eventbus address: database.query.execute");

        eventBus.consumer("database.query.execute", message -> {

            JsonObject request = (JsonObject) message.body();

            logger.info("got in db service " + request);

            String query = request.getString("query");

            logger.debug("Executing Query: {}", query);

            pgClient.query(query).execute(ar -> {

                if (ar.succeeded())
                {
                    RowSet<Row> rows = ar.result();

                    JsonArray data = new JsonArray();

                    for (Row row : rows)
                    {
                        JsonObject json = new JsonObject();

                        for (int i = 0; i < row.size(); i++)
                        {
                            String columnName = row.getColumnName(i);

                            json.put(columnName, row.getValue(i));
                        }
                        data.add(json);
                    }

                    JsonObject response = new JsonObject().put("status", "success");

                    if(!data.isEmpty())
                    {
                        response.put("data",data);
                    }

                    message.reply(response);
                }
                else
                {
                    logger.error("Database query failed: ", ar.cause());

                    message.reply(new JsonObject().put("status", "fail").put("message", ar.cause().getMessage()));
                }
            });
        });

        eventBus.consumer("database.test.query",req->
        {
            logger.info("got in db");

            req.reply(new JsonObject().put("status","done"));
        });
        startPromise.complete();
    }
}
