package io.vertx.nms.service;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.nms.util.Constants;
import io.vertx.nms.database.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProvisionService
{
    private static final Logger logger = LoggerFactory.getLogger(ProvisionService.class);

    private final EventBus eventBus;

    private static final String YES = "yes";

    private static final String NO = "no";

    private static final String PROVISION_COLUMN = "provision";

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

        if (YES.equalsIgnoreCase(status))
        {
            provisionStatus = true;
        }
        else if (NO.equalsIgnoreCase(status))
        {
            provisionStatus = false;
        }
        else
        {
            ctx.response().setStatusCode(400).end("Bad Request: Status must be either 'yes' or 'no'");

            return;
        }

        JsonObject request = new JsonObject()
                .put(Constants.OPERATION, Constants.DATABASE_OPERATION_UPDATE)
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_DISCOVERY_PROFILE)
                .put(Constants.DATA, new JsonObject().put(PROVISION_COLUMN, provisionStatus))
                .put(Constants.CONDITION, new JsonObject()
                        .put(Constants.DATABASE_DISCOVERY_PROFILE_NAME, discoveryProfileName)
                        .put(Constants.DISCOVERY, true));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY,queryResult.getQuery()).put(Constants.PARAMS,queryResult.getParams()), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(200).end("Provision status updated successfully");
            }
            else
            {
                logger.error(" Failed to update provision status: {}",reply.cause().getMessage());

                ctx.response().setStatusCode(500).end(Constants.INTERNAL_SERVER_ERROR_MESSAGE);
            }
        });
    }

    // Fetches provision data for a specific discovery profile.
    // @param discoveryProfileName The name of the discovery profile to fetch data for.
    // @param ctx The RoutingContext containing the request and response.
    public void getProvisionData(String discoveryProfileName, RoutingContext ctx)
    {
        JsonObject request = new JsonObject()
                .put(Constants.OPERATION, Constants.DATABASE_OPERATION_SELECT)
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISION_DATA)
                .put(Constants.COLUMNS, new JsonArray().add(Constants.DATA).add(Constants.DATABASE_COLUMN_POLLED_AT))
                .put(Constants.CONDITION, new JsonObject().put(Constants.DATABASE_DISCOVERY_PROFILE_NAME, discoveryProfileName));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        JsonObject fetchRequest = new JsonObject()
                .put(Constants.QUERY, queryResult.getQuery())
                .put(Constants.PARAMS, queryResult.getParams());

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, fetchRequest, reply ->
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

                JsonArray results = resultObject.getJsonArray(Constants.DATA);

                if (results == null || results.isEmpty())
                {
                    ctx.response().setStatusCode(404).end(new JsonObject().put(Constants.MESSAGE, "No data found for discoveryProfileName: " + discoveryProfileName).encode());

                    return;
                }

                JsonArray responseArray = new JsonArray();

                for (int i = 0; i < results.size(); i++)
                {
                    JsonObject row = results.getJsonObject(i);

                    JsonObject responseData = new JsonObject()
                            .put(Constants.DATA, row.getJsonObject(Constants.DATA))
                            .put(Constants.DATABASE_COLUMN_POLLED_AT, row.getString(Constants.DATABASE_COLUMN_POLLED_AT));

                    responseArray.add(responseData);
                }

                ctx.response().setStatusCode(200).end(new JsonObject().put(Constants.DATA, responseArray).encode());
            }
            else
            {
                logger.error("Failed to fetch provision data: {}", reply.cause().getMessage());

                ctx.response().setStatusCode(500).end(new JsonObject().put(Constants.MESSAGE, Constants.INTERNAL_SERVER_ERROR_MESSAGE).encode());
            }
        });
    }

    // Fetches all provision data from the database.
    // @param ctx The RoutingContext containing the request and response.
    public void getAllProvisionData(RoutingContext ctx)
    {
        JsonObject request = new JsonObject()
                .put(Constants.OPERATION, Constants.DATABASE_OPERATION_SELECT)
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISION_DATA)
                .put(Constants.COLUMNS, new JsonArray().add(Constants.DATABASE_ALL_COLUMN));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        JsonObject fetchRequest = new JsonObject()
                .put(Constants.QUERY, queryResult.getQuery())
                .put(Constants.PARAMS, queryResult.getParams());

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, fetchRequest, reply ->
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

                JsonArray results = resultObject.getJsonArray(Constants.DATA);

                if (results == null || results.isEmpty())
                {
                    ctx.response().setStatusCode(404).end(new JsonObject().put(Constants.MESSAGE, "No data found").encode());

                    return;
                }

                JsonArray responseArray = new JsonArray();

                for (int i = 0; i < results.size(); i++)
                {
                    JsonObject row = results.getJsonObject(i);

                    JsonObject entry = new JsonObject()
                            .put(Constants.DISCOVERY_PROFILE_NAME, row.getString(Constants.DATABASE_DISCOVERY_PROFILE_NAME))
                            .put(Constants.DATA, row.getJsonObject(Constants.DATA));

                    responseArray.add(entry);
                }

                ctx.response().setStatusCode(200).end(new JsonObject().put(Constants.DATA, responseArray).encode());

            }
            else
            {
                logger.error(" Failed to fetch all provision data: {}", reply.cause().getMessage());

                ctx.response().setStatusCode(500).end(new JsonObject().put(Constants.MESSAGE, Constants.INTERNAL_SERVER_ERROR_MESSAGE).encode());
            }
        });
    }
}
