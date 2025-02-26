package io.vertx.nms.service;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CredentialService extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(CredentialService.class);

    @Override
    public void start()
    {
        EventBus eventBus = vertx.eventBus();

        logger.info("CredentialService is listening on eventbus address: service.credential");

        eventBus.consumer("service.credential.create", message -> {

            String requestBodyStr = message.body().toString();

            JsonObject requestBody = new JsonObject(requestBodyStr);

            String systemType = requestBody.getString("system_type");

            if ("snmp".equalsIgnoreCase(systemType))
            {
                if (!requestBody.containsKey("community") || !requestBody.containsKey("version") || !requestBody.containsKey("credential_profile_name"))
                {
                    message.reply(new JsonObject()
                            .put("status", "fail")
                            .put("message", "System type and parameters not matching"));
                    return;
                }
            }
            else if ("windows".equalsIgnoreCase(systemType) || "linux".equalsIgnoreCase(systemType))
            {
                message.reply(new JsonObject()
                        .put("status", "fail")
                        .put("message", "Currently not supported, try with snmp only"));
                return;
            }
            else
            {
                message.reply(new JsonObject()
                        .put("status", "fail")
                        .put("message", "system type is not known"));
                return;
            }

            eventBus.request("database.credential.add", requestBody, reply -> {

                if (reply.succeeded())
                {
                    message.reply(reply.result().body());
                }
                else {
                    String errorMessage = reply.cause().getMessage();

                    if (errorMessage.contains("duplicate key value violates unique constraint")) {
                        JsonObject response = new JsonObject()
                                .put("status", "fail")
                                .put("message", "Credential Profile Name already exists");

                        message.reply(response);
                    } else {
                        JsonObject response = new JsonObject()
                                .put("status", "fail")
                                .put("message", "not proceed");

                        message.reply(response);
                    }
                }
            });
        });

        eventBus.consumer("service.credential.read", message -> {

            String credentialProfileName = message.body().toString();

            logger.info("Fetching Credential for Profile Name: {}", credentialProfileName);

            eventBus.request("database.credential.read", credentialProfileName, reply -> {

                if (reply.succeeded())
                {
                    message.reply(reply.result().body());
                }
                else
                {
                    JsonObject response = new JsonObject()
                            .put("status", "fail")
                            .put("message", "Credential not found");

                    message.reply(response);
                }
            });
        });

        eventBus.consumer("service.credential.update", message -> {

            logger.info("service.credential.update received message: " + message.body());

            String requestBodyStr = message.body().toString();

            JsonObject requestBody = new JsonObject(requestBodyStr);

            String credentialProfileName = requestBody.getString("credential_profile_name");
            String systemType = requestBody.getString("system_type");
            String community = requestBody.getString("community");
            String version = requestBody.getString("version");

            if (credentialProfileName == null || credentialProfileName.isEmpty() ||
                    systemType == null || systemType.isEmpty() ||
                    community == null || community.isEmpty() ||
                    version == null || version.isEmpty())
            {
                message.reply(new JsonObject()
                        .put("status", "fail")
                        .put("message", "All fields (credential_profile_name, system_type, community, version) are required and must not be empty"));
                return;
            }

            if (!"snmp".equalsIgnoreCase(systemType)) {
                message.reply(new JsonObject()
                        .put("status", "fail")
                        .put("message", "Currently not supported, try with snmp only"));
                return;
            }

            logger.debug("sending from service.credential.update to database.credential.update ");
            eventBus.request("database.credential.update", requestBody, reply -> {
                if (reply.succeeded())
                {
                    message.reply(reply.result().body());
                }
                else
                {
                    JsonObject response = new JsonObject()
                            .put("status", "fail")
                            .put("message", "Update failed");

                    message.reply(response);
                }
            });
        });

        eventBus.consumer("service.credential.delete", message -> {

            String credentialProfileName = message.body().toString();

            if (credentialProfileName == null || credentialProfileName.isEmpty())
            {
                message.reply(new JsonObject()
                        .put("status", "fail")
                        .put("message", "Credential Profile Name is required for deleting credential"));
                return;
            }

            logger.info("Deleting Credential for Profile Name: {}", credentialProfileName);

            eventBus.request("database.credential.delete", credentialProfileName, reply -> {

                if (reply.succeeded())
                {
                    message.reply(reply.result().body());
                }
                else
                {
                    JsonObject response = new JsonObject()
                            .put("status", "fail")
                            .put("message", "Deletion failed");

                    message.reply(response);
                }
            });
        });
    }
}
