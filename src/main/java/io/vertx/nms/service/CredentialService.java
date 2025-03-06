package io.vertx.nms.service;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.nms.constants.Constants;
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
                .put(Constants.TABLE_NAME_KEY, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                .put(Constants.OPERATION_KEY, Constants.DATABASE_OPERATION_SELECT)
                .put(Constants.COLUMNS_KEY, new JsonArray().add(Constants.DATABASE_ALL_COLUMN));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY_KEY, queryResult.getQuery()).put(Constants.PARAMS_KEY, queryResult.getParams()), reply ->
                {
                    if (reply.succeeded())
                    {
                        ctx.response().setStatusCode(200).end(reply.result().body().toString());
                    }
                    else
                    {
                        logger.error("Failed to fetch credentials: {}", reply.cause().getMessage());

                        ctx.response().setStatusCode(500).end(Constants.INTERNAL_SERVER_ERROR_MESSAGE);
                    }
                });
    }

    // Fetches specific credential profiles from the database.
    // @param credentialProfileName The name of the credential profile to fetch.
    // @param ctx The RoutingContext containing the request and response.
    public void getCredentialByName(String credentialProfileName, RoutingContext ctx)
    {
        JsonObject request = new JsonObject()
                .put(Constants.TABLE_NAME_KEY, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                .put(Constants.OPERATION_KEY, Constants.DATABASE_OPERATION_SELECT)
                .put(Constants.COLUMNS_KEY, new JsonArray().add(Constants.DATABASE_ALL_COLUMN))
                .put(Constants.CONDITION_KEY, new JsonObject().put(Constants.DATABASE_CREDENTIAL_PROFILE_NAME_KEY, credentialProfileName));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY_KEY, queryResult.getQuery()).put(Constants.PARAMS_KEY, queryResult.getParams()), reply ->
                {
                    if (reply.succeeded())
                    {
                        ctx.response().setStatusCode(200).end(reply.result().body().toString());
                    }
                    else
                    {
                        logger.error("Failed to fetch credential by name: {}", reply.cause().getMessage());

                        ctx.response().setStatusCode(500).end(Constants.INTERNAL_SERVER_ERROR_MESSAGE);
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
                .put(Constants.TABLE_NAME_KEY, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                .put(Constants.OPERATION_KEY, Constants.DATABASE_OPERATION_INSERT)
                .put(Constants.DATA_KEY, requestBody);

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY_KEY, queryResult.getQuery()).put(Constants.PARAMS_KEY, queryResult.getParams()), reply ->
                {
                    if (reply.succeeded())
                    {
                        ctx.response().setStatusCode(201).end(reply.result().body().toString());
                    }
                    else
                    {
                        logger.error("Failed to create credential: {}", reply.cause().getMessage());

                        ctx.response().setStatusCode(500).end(Constants.INTERNAL_SERVER_ERROR_MESSAGE);
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
                .put(Constants.TABLE_NAME_KEY, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                .put(Constants.OPERATION_KEY, Constants.DATABASE_OPERATION_UPDATE)
                .put(Constants.DATA_KEY, requestBody)
                .put(Constants.CONDITION_KEY, new JsonObject().put(Constants.DATABASE_CREDENTIAL_PROFILE_NAME_KEY, credentialProfileName));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY_KEY, queryResult.getQuery()).put(Constants.PARAMS_KEY, queryResult.getParams()), reply ->
        {
                    if (reply.succeeded())
                    {
                        ctx.response().setStatusCode(200).end(reply.result().body().toString());
                    }
                    else
                    {
                        logger.error("Failed to update credential: {}", reply.cause().getMessage());

                        ctx.response().setStatusCode(500).end(Constants.INTERNAL_SERVER_ERROR_MESSAGE);
                    }
                });
    }

    // Deletes credential profile from the database.
    // @param credentialProfileName The name of the credential profile to delete.
    // @param ctx The RoutingContext containing the request and response.
    public void deleteCredential(String credentialProfileName, RoutingContext ctx)
    {
        JsonObject request = new JsonObject()
                .put(Constants.TABLE_NAME_KEY, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                .put(Constants.OPERATION_KEY, Constants.DATABASE_OPERATION_DELETE)
                .put(Constants.CONDITION_KEY, new JsonObject().put(Constants.DATABASE_CREDENTIAL_PROFILE_NAME_KEY, credentialProfileName));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY_KEY, queryResult.getQuery()).put(Constants.PARAMS_KEY, queryResult.getParams()), reply ->
        {
                    if (reply.succeeded())
                    {
                        ctx.response().setStatusCode(204).end();
                    }
                    else
                    {
                        logger.error("Failed to delete credential: {}", reply.cause().getMessage());

                        ctx.response().setStatusCode(500).end(Constants.INTERNAL_SERVER_ERROR_MESSAGE);
                    }
                });
    }

    // Validates the request body for required fields.
    // @param requestBody The JSON object containing the request body.
    // @param ctx The RoutingContext containing the request and response.
    private boolean isValidRequestBody(JsonObject requestBody, RoutingContext ctx)
    {
        if (!requestBody.containsKey(Constants.DATABASE_CREDENTIAL_PROFILE_NAME_KEY) || requestBody.getString(Constants.DATABASE_CREDENTIAL_PROFILE_NAME_KEY).isEmpty() ||
                !requestBody.containsKey(Constants.JSON_SYSTEM_TYPE_KEY) || requestBody.getString(Constants.JSON_SYSTEM_TYPE_KEY).isEmpty() ||
                !requestBody.containsKey(Constants.JSON_CREDENTIALS_KEY) || requestBody.getJsonObject(Constants.JSON_CREDENTIALS_KEY).isEmpty()) {

            ctx.response().setStatusCode(400).end("Required fields: credential_profile_name, system_type, credentials");

            return true;
        }

        String systemType = requestBody.getString(Constants.JSON_SYSTEM_TYPE_KEY);

        JsonObject credentials = requestBody.getJsonObject(Constants.JSON_CREDENTIALS_KEY);

        if ("SNMP".equalsIgnoreCase(systemType) && !credentials.containsKey(Constants.JSON_COMMUNITY_KEY) && !credentials.containsKey(Constants.JSON_VERSION_KEY))
        {
            ctx.response().setStatusCode(400).end("SNMP system type requires 'community_version' in credentials");

            return true;
        }
        return false;
    }
}
