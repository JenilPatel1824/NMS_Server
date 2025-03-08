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
    // @param ctx The RoutingContext containing the request and response.
    public void getAllCredentials(RoutingContext ctx)
    {
        JsonObject request = new JsonObject()
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                .put(Constants.OPERATION, Constants.DATABASE_OPERATION_SELECT)
                .put(Constants.COLUMNS, new JsonArray().add(Constants.DATABASE_ALL_COLUMN));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()), reply ->
                {
                    if (reply.succeeded())
                    {
                        ctx.response().setStatusCode(200).end(reply.result().body().toString());
                    }
                    else
                    {
                        logger.error("Failed to fetch credentials: {}", reply.cause().getMessage());

                        ctx.response().setStatusCode(500).end(Constants.INTERNAL_SERVER_ERROR_MESSAGE + reply.cause().getMessage());
                    }
                });
    }

    // Fetches specific credential profiles from the database.
    // @param credentialProfileName The name of the credential profile to fetch.
    // @param ctx The RoutingContext containing the request and response.
    public void getCredentialByName(String credentialProfileName, RoutingContext ctx)
    {
        JsonObject request = new JsonObject()
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                .put(Constants.OPERATION, Constants.DATABASE_OPERATION_SELECT)
                .put(Constants.COLUMNS, new JsonArray().add(Constants.DATABASE_ALL_COLUMN))
                .put(Constants.CONDITION, new JsonObject().put(Constants.DATABASE_CREDENTIAL_PROFILE_NAME, credentialProfileName));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()), reply ->
                {
                    if (reply.succeeded())
                    {
                        ctx.response().setStatusCode(200).end(reply.result().body().toString());
                    }
                    else
                    {
                        logger.error("Failed to fetch credential by name: {}", reply.cause().getMessage());

                        ctx.response().setStatusCode(500).end(Constants.INTERNAL_SERVER_ERROR_MESSAGE + reply.cause().getMessage());
                    }
                });
    }

    // Creates new credential profile
    // @param requestBody The JSON object containing the credential profile details.
    // @param ctx The RoutingContext containing the request and response.
    public void createCredential(JsonObject requestBody, RoutingContext ctx)
    {
        if (isValidRequestBody(requestBody, ctx)) return;

        JsonObject request = new JsonObject()
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                .put(Constants.OPERATION, Constants.DATABASE_OPERATION_INSERT)
                .put(Constants.DATA, requestBody);

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()), reply ->
                {
                    if (reply.succeeded())
                    {
                        ctx.response().setStatusCode(201).end(reply.result().body().toString());
                    }
                    else
                    {
                        logger.error("Failed to create credential: {}", reply.cause().getMessage());

                        ctx.response().setStatusCode(500).end(Constants.INTERNAL_SERVER_ERROR_MESSAGE + reply.cause().getMessage());
                    }
                });
    }

    //Updates credential profile to database.
    // @param credentialProfileName The name of the credential profile to update.
    // @param requestBody The JSON object containing the updated credential profile details.
    // @param ctx The RoutingContext containing the request and response.
    public void updateCredential(String credentialProfileName, JsonObject requestBody, RoutingContext ctx)
    {
        JsonObject request = new JsonObject()
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                .put(Constants.OPERATION, Constants.DATABASE_OPERATION_UPDATE)
                .put(Constants.DATA, requestBody)
                .put(Constants.CONDITION, new JsonObject().put(Constants.DATABASE_CREDENTIAL_PROFILE_NAME, credentialProfileName));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()), reply ->
        {
                    if (reply.succeeded())
                    {
                        ctx.response().setStatusCode(200).end(reply.result().body().toString());
                    }
                    else
                    {
                        logger.error("Failed to update credential: {}", reply.cause().getMessage());

                        ctx.response().setStatusCode(500).end(Constants.INTERNAL_SERVER_ERROR_MESSAGE + reply.cause().getMessage());
                    }
                });
    }

    // Deletes credential profile from the database.
    // @param credentialProfileName The name of the credential profile to delete.
    // @param ctx The RoutingContext containing the request and response.
    public void deleteCredential(String credentialProfileName, RoutingContext ctx)
    {
        JsonObject request = new JsonObject()
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                .put(Constants.OPERATION, Constants.DATABASE_OPERATION_DELETE)
                .put(Constants.CONDITION, new JsonObject().put(Constants.DATABASE_CREDENTIAL_PROFILE_NAME, credentialProfileName));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()), reply ->
        {
                    if (reply.succeeded())
                    {
                        ctx.response().setStatusCode(204).end();
                    }
                    else
                    {
                        logger.error("Failed to delete credential: {}", reply.cause().getMessage());

                        ctx.response().setStatusCode(500).end(Constants.INTERNAL_SERVER_ERROR_MESSAGE + reply.cause().getMessage());
                    }
                });
    }

    // Validates the request body for required fields.
    // @param requestBody The JSON object containing the request body.
    // @param ctx The RoutingContext containing the request and response.
    private boolean isValidRequestBody(JsonObject requestBody, RoutingContext ctx)
    {
        if (!requestBody.containsKey(Constants.DATABASE_CREDENTIAL_PROFILE_NAME) || requestBody.getString(Constants.DATABASE_CREDENTIAL_PROFILE_NAME).isEmpty() ||
                !requestBody.containsKey(Constants.SYSTEM_TYPE) || requestBody.getString(Constants.SYSTEM_TYPE).isEmpty() ||
                !requestBody.containsKey(Constants.CREDENTIALS) || requestBody.getJsonObject(Constants.CREDENTIALS).isEmpty()) {

            ctx.response().setStatusCode(400).end("Required fields: credential_profile_name, system_type, credentials");

            return true;
        }

        String systemType = requestBody.getString(Constants.SYSTEM_TYPE);

        JsonObject credentials = requestBody.getJsonObject(Constants.CREDENTIALS);

        if ("SNMP".equalsIgnoreCase(systemType) && !credentials.containsKey(Constants.COMMUNITY) && !credentials.containsKey(Constants.VERSION))
        {
            ctx.response().setStatusCode(400).end("SNMP system type requires 'community_version' in credentials");

            return true;
        }
        return false;
    }
}
