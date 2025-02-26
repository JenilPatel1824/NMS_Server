package io.vertx.nms.service;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class DatabaseService extends AbstractVerticle
{

    private final PgPool pgClient;

    public DatabaseService(PgPool pgPool)
    {
        this.pgClient = pgPool;
    }

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    @Override
    public void start() {
        checkAndCreateTable()
                .onSuccess(v -> {
                    logger.info("Table check/creation complete.");

                    EventBus eventBus = vertx.eventBus();

                    eventBus.consumer("database.credential.add", this::handleCredentialAdd);
                    eventBus.consumer("database.credential.read", this::handleCredentialRead);
                    eventBus.consumer("database.credential.update", this::handleCredentialUpdate);
                    eventBus.consumer("database.credential.delete", this::handleCredentialDelete);
                })
                .onFailure(err -> logger.error("Failed to check/create table: " + err.getMessage()));
    }

    private Future<Void> checkAndCreateTable() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS credentials (" +
                "id SERIAL PRIMARY KEY, " +
                "credential_profile_name VARCHAR(255) NOT NULL UNIQUE, " +
                "community VARCHAR(255) NOT NULL, " +
                "version VARCHAR(50) NOT NULL, " +
                "system_type VARCHAR(50) NOT NULL" +
                ")";

        return pgClient.query(createTableSQL)
                .execute()
                .mapEmpty();
    }
    private void handleCredentialAdd(Message<JsonObject> message) {
        JsonObject credentialData = message.body();

        String insertSQL = "INSERT INTO credentials (credential_profile_name, community, version, system_type) VALUES ($1, $2, $3, $4)";

        Tuple params = Tuple.of(
                credentialData.getString("credential_profile_name"),
                credentialData.getString("community"),
                credentialData.getString("version"),
                credentialData.getString("system_type")
        );

        pgClient.preparedQuery(insertSQL)
                .execute(params)
                .onSuccess(res -> {
                    logger.info("Credential added successfully");

                    JsonObject response = new JsonObject()
                            .put("status", "success")
                            .put("details", message.body());

                    message.reply(response);
                })
                .onFailure(err -> {
                    logger.error("Failed to add credential: " + err.getMessage());
                    message.fail(500, "Failed to add credential: " + err.getMessage());
                });
    }

    private void handleCredentialRead(Message<JsonObject> message) {
        String profileName = String.valueOf(message.body());

        logger.info("Database service fetching credential for: " + profileName);

        String querySQL = "SELECT * FROM credentials WHERE credential_profile_name = $1";

        pgClient.preparedQuery(querySQL)
                .execute(Tuple.of(profileName))
                .onSuccess(rows -> {
                    if (rows.size() > 0) {
                        JsonObject result = new JsonObject();
                        for (Row row : rows) {
                            result.put("id", row.getInteger("id"));
                            result.put("credential_profile_name", row.getString("credential_profile_name"));
                            result.put("community", row.getString("community"));
                            result.put("version", row.getString("version"));
                            result.put("system_type", row.getString("system_type"));
                        }

                        message.reply(new JsonObject().put("status", "success").put("data", result));
                    } else {
                        message.reply(new JsonObject().put("status", "fail").put("message", "Credential not found"));
                    }
                })
                .onFailure(err -> {
                    logger.error("Failed to read credential: " + err.getMessage());
                    message.fail(500, "Failed to read credential: " + err.getMessage());
                });
    }

    // Update: Update Credential by Profile Name
    private void handleCredentialUpdate(Message<JsonObject> message) {
        logger.debug("dbservice handlecredentialupdate called");

        JsonObject data = message.body();

        String updateSQL = "UPDATE credentials SET community = $1, version = $2, system_type = $3 WHERE credential_profile_name = $4";

        Tuple params = Tuple.of(
                data.getString("community"),
                data.getString("version"),
                data.getString("system_type"),
                data.getString("credential_profile_name")
        );

        pgClient.preparedQuery(updateSQL)
                .execute(params)
                .onSuccess(res -> {
                    if (res.rowCount() > 0) {
                        message.reply(new JsonObject().put("status", "success").put("message", "Credential updated"));
                    } else {
                        message.reply(new JsonObject().put("status", "fail").put("message", "Credential not found"));
                    }
                })
                .onFailure(err -> {
                    logger.error("Failed to update credential: " + err.getMessage());
                    message.fail(500, "Failed to update credential: " + err.getMessage());
                });
    }

    // Delete: Delete Credential by Profile Name
    private void handleCredentialDelete(Message<JsonObject> message) {
        String profileName = String.valueOf(message.body());

        String deleteSQL = "DELETE FROM credentials WHERE credential_profile_name = $1";

        pgClient.preparedQuery(deleteSQL)
                .execute(Tuple.of(profileName))
                .onSuccess(res -> {
                    if (res.rowCount() > 0) {
                        message.reply(new JsonObject().put("status", "success").put("message", "Credential deleted"));
                    } else {
                        message.reply(new JsonObject().put("status", "fail").put("message", "Credential not found"));
                    }
                })
                .onFailure(err -> {
                    logger.error("Failed to delete credential: " + err.getMessage());
                    message.fail(500, "Failed to delete credential: " + err.getMessage());
                });
    }
}
