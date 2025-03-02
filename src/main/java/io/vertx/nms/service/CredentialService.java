package io.vertx.nms.service;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
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

    public void getAllCredentials(RoutingContext ctx)
    {
        JsonObject request = new JsonObject().put("tableName", "credential").put("operation", "select").put("columns", new JsonArray().add("*"));

        String query = QueryBuilder.buildQuery(request);

        eventBus.request("database.query.execute", new JsonObject().put("query", query), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(200).end(reply.result().body().toString());
            }
            else
            {
                logger.error("[{}] Failed to process GET request: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end("Internal Server Error");
            }
        });
    }

    public void getCredentialByName(String credentialProfileName, RoutingContext ctx)
    {
        JsonObject request = new JsonObject().put("tableName", "credential").put("operation", "select").put("columns", new JsonArray().add("*")).put("condition", "credential_profile_name = '" + credentialProfileName + "'");

        String query = QueryBuilder.buildQuery(request);

        eventBus.request("database.query.execute", new JsonObject().put("query", query), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(200).end(reply.result().body().toString());
            }
            else
            {
                logger.error("[{}] Failed to process GET by name request: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end("Internal Server Error");
            }
        });
    }

    public void createCredential(JsonObject requestBody, RoutingContext ctx)
    {
        if (isValidRequestBody(requestBody, ctx)) return;

        JsonObject request = new JsonObject()
                .put("tableName", "credential")
                .put("operation", "insert")
                .put("data", requestBody);

        String query = QueryBuilder.buildQuery(request);

        eventBus.request("database.query.execute", new JsonObject().put("query", query), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(201).end(reply.result().body().toString());
            }
            else
            {
                logger.error("[{}] Failed to process POST request: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end("Internal Server Error");
            }
        });
    }

    public void updateCredential(String credentialProfileName, JsonObject requestBody, RoutingContext ctx)
    {
        if (isValidRequestBody(requestBody, ctx)) return;

        JsonObject request = new JsonObject()
                .put("tableName", "credential")
                .put("operation", "update")
                .put("data", requestBody)
                .put("condition", "credential_profile_name = '" + credentialProfileName + "'");

        String query = QueryBuilder.buildQuery(request);

        eventBus.request("database.query.execute", new JsonObject().put("query", query), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(200).end(reply.result().body().toString());
            }
            else
            {
                logger.error("[{}] Failed to process PUT request: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end("Internal Server Error");
            }
        });
    }

    public void deleteCredential(String credentialProfileName, RoutingContext ctx)
    {
        JsonObject request = new JsonObject()
                .put("tableName", "credential")
                .put("operation", "delete")
                .put("condition", "credential_profile_name = '" + credentialProfileName + "'");

        String query = QueryBuilder.buildQuery(request);

        logger.info("[{}] Generated Query: {}", Thread.currentThread().getName(), query);

        eventBus.request("database.query.execute", new JsonObject().put("query", query), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(204).end();
            }
            else
            {
                logger.error("[{}] Failed to process DELETE request: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end("Internal Server Error");
            }
        });
    }

    private boolean isValidRequestBody(JsonObject requestBody, RoutingContext ctx)
    {
        if (!requestBody.containsKey("credential_profile_name") || requestBody.getString("credential_profile_name").isEmpty() ||
                !requestBody.containsKey("version") || requestBody.getString("version").isEmpty() ||
                !requestBody.containsKey("community") || requestBody.getString("community").isEmpty() ||
                !requestBody.containsKey("system_type") || requestBody.getString("system_type").isEmpty()) {

            ctx.response().setStatusCode(400).end("Required fields: credential_profile_name, version, community, system_type");

            return true;
        }
        return false;
    }
}
