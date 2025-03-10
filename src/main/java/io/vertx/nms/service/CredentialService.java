package io.vertx.nms.service;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.nms.util.Constants;
import io.vertx.nms.database.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CredentialService
{
    private static final Logger logger = LoggerFactory.getLogger(CredentialService.class);

    private final EventBus eventBus;

    public CredentialService(EventBus eventBus)
    {
        this.eventBus = eventBus;
    }

    // Fetches all credential profiles from the database.
    // @param context The RoutingContext containing the request and response.
    public void getAllCredentials(RoutingContext context)
    {
        var request = new JsonObject()
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                .put(Constants.OPERATION, Constants.SELECT)
                .put(Constants.COLUMNS, new JsonArray().add(Constants.DATABASE_ALL_COLUMN));

        var queryResult = QueryBuilder.buildQuery(request);

        eventBus.<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()), reply ->
                {
                    if (reply.succeeded())
                    {
                        context.response().setStatusCode(200).end(reply.result().body().toString());
                    }
                    else
                    {
                        logger.error("Failed to fetch credentials: {}", reply.cause().getMessage());

                        context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR + reply.cause().getMessage());
                    }
                });
    }

    // Fetches specific credential profiles from the database.
    // @param credentialProfileName The name of the credential profile to fetch.
    // @param context The RoutingContext containing the request and response.
    public void getCredentialById(String credentialProfileId, RoutingContext context)
    {
        try
        {
            var credentialId = Long.parseLong(credentialProfileId);

            var request = new JsonObject()
                    .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                    .put(Constants.OPERATION, Constants.SELECT)
                    .put(Constants.COLUMNS, new JsonArray().add(Constants.DATABASE_ALL_COLUMN))
                    .put(Constants.CONDITION, new JsonObject().put(Constants.ID, credentialId));

            var queryResult = QueryBuilder.buildQuery(request);

            eventBus.<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject()
                    .put(Constants.QUERY, queryResult.getQuery())
                    .put(Constants.PARAMS, queryResult.getParams()), reply ->
            {
                if (reply.succeeded())
                {
                    context.response().setStatusCode(200).end(reply.result().body().toString());
                }
                else
                {
                    logger.error("Failed to fetch credential by ID: {}", reply.cause().getMessage());

                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR + reply.cause().getMessage());
                }
            });
        }
        catch (NumberFormatException e)
        {
            logger.error("Invalid credential profile ID format: {}", credentialProfileId);

            context.response().setStatusCode(400).end("Invalid credential profile ID format.");
        }
    }

    // Creates new credential profile
    // @param requestBody The JSON object containing the credential profile details.
    // @param context The RoutingContext containing the request and response.
    public void createCredential(JsonObject requestBody, RoutingContext context)
    {
        if (isValidRequestBody(requestBody, context)) return;

        var request = new JsonObject()
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                .put(Constants.OPERATION, Constants.INSERT)
                .put(Constants.DATA, requestBody);

        var queryResult = QueryBuilder.buildQuery(request);

        eventBus.<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()), reply ->
                {
                    if (reply.succeeded())
                    {
                        context.response().setStatusCode(201).end(reply.result().body().toString());
                    }
                    else
                    {
                        logger.error("Failed to create credential: {}", reply.cause().getMessage());

                        context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR + reply.cause().getMessage());
                    }
                });
    }

    //Updates credential profile to database.
    // @param credentialProfileId The id of the credential profile to update.
    // @param requestBody The JSON object containing the updated credential profile details.
    // @param context The RoutingContext containing the request and response.
    public void updateCredential(String credentialProfileId, JsonObject requestBody, RoutingContext context)
    {
        try
        {
            var profileId = Long.parseLong(credentialProfileId);


            var request = new JsonObject()
                    .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                    .put(Constants.OPERATION, Constants.UPDATE)
                    .put(Constants.DATA, requestBody)
                    .put(Constants.CONDITION, new JsonObject().put(Constants.ID, profileId));

            var queryResult = QueryBuilder.buildQuery(request);

            eventBus.<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()), reply ->
            {
                if (reply.succeeded())
                {
                    context.response().setStatusCode(200).end(reply.result().body().toString());
                }
                else
                {
                    logger.error("Failed to update credential: {}", reply.cause().getMessage());

                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR + reply.cause().getMessage());
                }
            });
        }
        catch (NumberFormatException e)
        {
            logger.error("Invalid credentialProfileId: {}", credentialProfileId);

            context.response().setStatusCode(400).end("Invalid credentialProfileId. It must be a numeric value.");
        }
    }

    // Deletes credential profile from the database.
    // @param credentialProfileId The id of the credential profile to delete.
    // @param context The RoutingContext containing the request and response.
    public void deleteCredential(String credentialProfileId, RoutingContext context)
    {
        try
        {
            var profileId = Long.parseLong(credentialProfileId);

            var request = new JsonObject()
                    .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                    .put(Constants.OPERATION, Constants.DELETE)
                    .put(Constants.CONDITION, new JsonObject().put(Constants.ID, profileId));

            var queryResult = QueryBuilder.buildQuery(request);

            eventBus.<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()), reply ->
            {
                if (reply.succeeded())
                {
                    context.response().setStatusCode(204).end();
                }
                else
                {
                    logger.error("Failed to delete credential: {}", reply.cause().getMessage());

                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR + reply.cause().getMessage());
                }
            });
        }
        catch (NumberFormatException e)
        {
            logger.error("Invalid credentialProfileId: {}", credentialProfileId);

            context.response().setStatusCode(400).end("Invalid credentialProfileId. It must be a numeric value.");
        }
    }

    // Validates the request body for required fields.
    // @param requestBody The JSON object containing the request body.
    // @param context The RoutingContext containing the request and response.
    private boolean isValidRequestBody(JsonObject requestBody, RoutingContext context)
    {
        if (!requestBody.containsKey(Constants.DATABASE_CREDENTIAL_PROFILE_NAME) || requestBody.getString(Constants.DATABASE_CREDENTIAL_PROFILE_NAME).isEmpty() ||
                !requestBody.containsKey(Constants.SYSTEM_TYPE) || requestBody.getString(Constants.SYSTEM_TYPE).isEmpty() ||
                !requestBody.containsKey(Constants.CREDENTIALS) || requestBody.getJsonObject(Constants.CREDENTIALS).isEmpty()) {

            context.response().setStatusCode(400).end("Required fields: credential_profile_name, system_type, credentials");

            return true;
        }

        var systemType = requestBody.getString(Constants.SYSTEM_TYPE);

        var credentials = requestBody.getJsonObject(Constants.CREDENTIALS);

        if (Constants.SNMP.equalsIgnoreCase(systemType) && !credentials.containsKey(Constants.COMMUNITY) && !credentials.containsKey(Constants.VERSION))
        {
            context.response().setStatusCode(400).end("SNMP system type requires 'community and version' in credentials");

            return true;
        }
        return false;
    }
}
