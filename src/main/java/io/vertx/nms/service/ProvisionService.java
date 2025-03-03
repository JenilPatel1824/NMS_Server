package io.vertx.nms.service;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ProvisionService
{
    private static final Logger logger = LoggerFactory.getLogger(ProvisionService.class);

    private final EventBus eventBus;

    public ProvisionService(Vertx vertx)
    {
        this.eventBus = vertx.eventBus();
    }

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

        String query = "UPDATE discovery SET provision = " + provisionStatus +
                " WHERE discovery_profile_name = '" + discoveryProfileName + "' AND discovery = true";

        JsonObject request = new JsonObject().put("query", query);

        eventBus.request("database.query.execute", request, reply ->
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

    public void getProvisionData(String discoveryProfileName, RoutingContext ctx)
    {
        String query = "SELECT data FROM discovery_data WHERE discovery_profile_name = $1";

        JsonObject request = new JsonObject()
                .put("query", query)
                .put("params", new JsonArray().add(discoveryProfileName));

        eventBus.request("database.query.execute", request, reply ->
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
                for (int i = 0; i < results.size(); i++) {
                    JsonObject row = results.getJsonObject(i);
                    responseArray.add(row.getJsonObject("data"));
                }

                ctx.response().setStatusCode(200).end(new JsonObject().put("data", responseArray).encode());
            } else {
                logger.error("[{}] Failed to fetch provision data: {}", Thread.currentThread().getName(), reply.cause().getMessage());
                ctx.response().setStatusCode(500).end(new JsonObject().put("message", "Internal Server Error").encode());
            }
        });
    }

    public void getAllProvisionData(RoutingContext ctx) {
        String query = "SELECT discovery_profile_name, data FROM discovery_data";

        JsonObject request = new JsonObject().put("query", query);

        eventBus.request("database.query.execute", request, reply -> {
            if (reply.succeeded()) {
                Object body = reply.result().body();

                if (!(body instanceof JsonObject)) {
                    ctx.response().setStatusCode(500).end("Unexpected response format from database service");
                    return;
                }

                JsonObject resultObject = (JsonObject) body;
                JsonArray results = resultObject.getJsonArray("data");

                if (results == null || results.isEmpty()) {
                    ctx.response().setStatusCode(404).end(new JsonObject().put("message", "No data found").encode());
                    return;
                }

                JsonArray responseArray = new JsonArray();
                for (int i = 0; i < results.size(); i++) {
                    JsonObject row = results.getJsonObject(i);
                    JsonObject entry = new JsonObject()
                            .put("discoveryProfileName", row.getString("discovery_profile_name"))
                            .put("data", row.getJsonObject("data"));
                    responseArray.add(entry);
                }

                ctx.response().setStatusCode(200).end(new JsonObject().put("data", responseArray).encode());
            } else {
                logger.error("[{}] Failed to fetch all provision data: {}", Thread.currentThread().getName(), reply.cause().getMessage());
                ctx.response().setStatusCode(500).end(new JsonObject().put("message", "Internal Server Error").encode());
            }
        });
    }



}
