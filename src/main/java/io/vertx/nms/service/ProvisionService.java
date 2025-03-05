package io.vertx.nms.service;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.nms.database.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProvisionService
{
    private static final Logger logger = LoggerFactory.getLogger(ProvisionService.class);

    private final EventBus eventBus;

    public ProvisionService(Vertx vertx)
    {
        this.eventBus = vertx.eventBus();
    }

    // Updates the provision status of a discovery profile.
    // @param discoveryProfileName The name of the discovery profile to update.
    // @param status The new provision status.
    // @param ctx The RoutingContext containing the request and response.
    public void updateProvisionStatus(String discoveryProfileName, String status, RoutingContext ctx)
    {
        boolean provisionStatus;

        if ("yes".equalsIgnoreCase(status))
        {
            provisionStatus = true;
        }
        else if ("no".equalsIgnoreCase(status))
        {
            provisionStatus = false;
        }
        else
        {
            ctx.response().setStatusCode(400).end("Bad Request: Status must be either 'yes' or 'no'");

            return;
        }

        JsonObject request = new JsonObject()
                .put("operation", "update")
                .put("tableName", "discovery_profiles")
                .put("data", new JsonObject().put("provision", provisionStatus))
                .put("condition", new JsonObject()
                        .put("discovery_profile_name", discoveryProfileName)
                        .put("discovery", true));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request("database.query.execute", new JsonObject().put("query",queryResult.getQuery()).put("params",queryResult.getParams()), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(200).end("Provision status updated successfully");
            }
            else
            {
                logger.error("[{}] Failed to update provision status: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end("Internal Server Error");
            }
        });
    }

    // Fetches provision data for a specific discovery profile.
    // @param discoveryProfileName The name of the discovery profile to fetch data for.
    // @param ctx The RoutingContext containing the request and response.
    public void getProvisionData(String discoveryProfileName, RoutingContext ctx)
    {
        JsonObject request = new JsonObject()
                .put("operation", "select")
                .put("tableName", "provision_data")
                .put("columns", new JsonArray().add("data").add("polled_at"))
                .put("condition", new JsonObject().put("discovery_profile_name", discoveryProfileName));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        JsonObject fetchRequest = new JsonObject()
                .put("query", queryResult.getQuery())
                .put("params", queryResult.getParams());

        eventBus.request("database.query.execute", fetchRequest, reply ->
        {
            if (reply.succeeded())
            {
                Object body = reply.result().body();

                if (!(body instanceof JsonObject))
                {
                    ctx.response().setStatusCode(500).end("Unexpected response format from database service");

                    return;
                }

                JsonObject resultObject = (JsonObject) body;

                JsonArray results = resultObject.getJsonArray("data");

                if (results == null || results.isEmpty())
                {
                    ctx.response().setStatusCode(404).end(new JsonObject().put("message", "No data found for discoveryProfileName: " + discoveryProfileName).encode());

                    return;
                }

                JsonArray responseArray = new JsonArray();

                for (int i = 0; i < results.size(); i++)
                {
                    JsonObject row = results.getJsonObject(i);

                    JsonObject responseData = new JsonObject()
                            .put("data", row.getJsonObject("data"))
                            .put("polled_at", row.getString("polled_at"));

                    responseArray.add(responseData);
                }

                ctx.response().setStatusCode(200).end(new JsonObject().put("data", responseArray).encode());
            }
            else
            {
                logger.error("[{}] Failed to fetch provision data: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end(new JsonObject().put("message", "Internal Server Error").encode());
            }
        });
    }

    // Fetches all provision data from the database.
    // @param ctx The RoutingContext containing the request and response.
    public void getAllProvisionData(RoutingContext ctx)
    {
        JsonObject request = new JsonObject()
                .put("operation", "select")
                .put("tableName", "discovery_data")
                .put("columns", new JsonArray().add("*"));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        JsonObject fetchRequest = new JsonObject()
                .put("query", queryResult.getQuery())
                .put("params", queryResult.getParams());

        eventBus.request("database.query.execute", fetchRequest, reply ->
        {
            if (reply.succeeded())
            {
                Object body = reply.result().body();

                if (!(body instanceof JsonObject))
                {
                    ctx.response().setStatusCode(500).end("Unexpected response format from database service");

                    return;
                }

                JsonObject resultObject = (JsonObject) body;

                JsonArray results = resultObject.getJsonArray("data");

                if (results == null || results.isEmpty())
                {
                    ctx.response().setStatusCode(404).end(new JsonObject().put("message", "No data found").encode());

                    return;
                }

                JsonArray responseArray = new JsonArray();

                for (int i = 0; i < results.size(); i++)
                {
                    JsonObject row = results.getJsonObject(i);

                    JsonObject entry = new JsonObject()
                            .put("discoveryProfileName", row.getString("discovery_profile_name"))
                            .put("data", row.getJsonObject("data"));

                    responseArray.add(entry);
                }

                ctx.response().setStatusCode(200).end(new JsonObject().put("data", responseArray).encode());

            }
            else
            {
                logger.error("[{}] Failed to fetch all provision data: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end(new JsonObject().put("message", "Internal Server Error").encode());
            }
        });
    }
}
