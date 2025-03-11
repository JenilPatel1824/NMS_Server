package io.vertx.nms.service;

import com.sun.tools.jconsole.JConsoleContext;
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

    private static final String PROVISION = "provision";

    private static final String INVALID_STATUS = "Bad Request: Status must be either 'yes' or 'no'";

    private static final String PROVISION_UPDATE_SUCCESSFUL = "Provision status updated successfully";

    private static final String DATA_NOT_FOUND ="No data found for discoveryProfileId: ";

    public ProvisionService(Vertx vertx)
    {
        this.eventBus = vertx.eventBus();
    }

    // Updates the provision status of a discovery profile.
    // @param discoveryProfileId The id of the discovery profile to update.
    // @param status The new provision status.
    // @param context The RoutingContext containing the request and response.
    public void updateProvisionStatus(String discoveryProfileId, String status, RoutingContext context)
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
            context.response().setStatusCode(400).end(INVALID_STATUS);

            return;
        }

        try
        {
            var profileId = Long.parseLong(discoveryProfileId);

            var request = new JsonObject()
                    .put(Constants.OPERATION, Constants.UPDATE)
                    .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_DISCOVERY_PROFILE)
                    .put(Constants.DATA, new JsonObject().put(PROVISION, provisionStatus))
                    .put(Constants.CONDITION, new JsonObject()
                            .put(Constants.DATABASE_DISCOVERY_PROFILE_ID, profileId)
                            .put(Constants.DISCOVERY, true));

            var queryResult = QueryBuilder.buildQuery(request);

            eventBus.<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()), reply ->
            {
                if (reply.succeeded())
                {
                    context.response().setStatusCode(200).end(PROVISION_UPDATE_SUCCESSFUL);
                }
                else
                {
                    logger.error(" Failed to update provision status: {}", reply.cause().getMessage());

                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
                }
            });
        }
        catch (NumberFormatException e)
        {
            logger.error("Invalid profileId: {}", discoveryProfileId);

            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
        }
    }

    // Fetches provision data for a specific discovery profile.
    // @param discoveryProfileId The id of the discovery profile to fetch data for.
    // @param context The RoutingContext containing the request and response.
    public void getProvisionData(String discoveryProfileId, RoutingContext context)
    {
        try
        {
            var profileId = Long.parseLong(discoveryProfileId);

            var request = new JsonObject()
                    .put(Constants.OPERATION, Constants.SELECT)
                    .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISION_DATA)
                    .put(Constants.COLUMNS, new JsonArray().add(Constants.DATA).add(Constants.POLLED_AT))
                    .put(Constants.CONDITION, new JsonObject().put(Constants.DATABASE_DISCOVERY_PROFILE_ID, profileId));

            var queryResult = QueryBuilder.buildQuery(request);

            var fetchRequest = new JsonObject()
                    .put(Constants.QUERY, queryResult.getQuery()+" ORDER BY polled_at DESC")
                    .put(Constants.PARAMS, queryResult.getParams());

            eventBus.<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, fetchRequest, reply ->
            {
                if (reply.succeeded())
                {
                    var resultObject = reply.result().body();

                    if (resultObject == null)
                    {
                        context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);

                        return;
                    }

                    var results = resultObject.getJsonArray(Constants.DATA);

                    if (results == null || results.isEmpty())
                    {
                        context.response().setStatusCode(404).end(new JsonObject().put(Constants.MESSAGE, DATA_NOT_FOUND + discoveryProfileId).encode());

                        return;
                    }

                    var responseArray = new JsonArray();

                    for (int i = 0; i < results.size(); i++)
                    {
                        var row = results.getJsonObject(i);

                        var responseData = new JsonObject()
                                .put(Constants.DATA, row.getJsonObject(Constants.DATA))
                                .put(Constants.POLLED_AT, row.getString(Constants.POLLED_AT));

                        responseArray.add(responseData);
                    }

                    context.response().setStatusCode(200).end(new JsonObject().put(Constants.DATA, responseArray).encode());
                }
                else
                {
                    logger.error("Failed to fetch provision data: {}", reply.cause().getMessage());

                    context.response().setStatusCode(500).end(new JsonObject().put(Constants.MESSAGE, Constants.MESSAGE_INTERNAL_SERVER_ERROR).encode());
                }
            });
        }
        catch (NumberFormatException e)
        {
            logger.error("Invalid profileId: {}", discoveryProfileId);

            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
        }
    }

    // Fetches all provision data from the database.
    // @param context The RoutingContext containing the request and response.
    public void getAllProvisionData(RoutingContext context)
    {
        var request = new JsonObject()
                .put(Constants.OPERATION, Constants.SELECT)
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISION_DATA)
                .put(Constants.COLUMNS, new JsonArray().add(Constants.DATABASE_ALL_COLUMN));

        var queryResult = QueryBuilder.buildQuery(request);

        var fetchRequest = new JsonObject()
                .put(Constants.QUERY, queryResult.getQuery())
                .put(Constants.PARAMS, queryResult.getParams());

        eventBus.<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, fetchRequest, reply ->
        {
            if (reply.succeeded())
            {
                var resultObject = reply.result().body();

                if (resultObject == null)
                {
                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);

                    return;
                }

                var results = resultObject.getJsonArray(Constants.DATA);

                if (results == null || results.isEmpty())
                {
                    context.response().setStatusCode(404).end(new JsonObject().put(Constants.MESSAGE, DATA_NOT_FOUND).encode());

                    return;
                }

                var responseArray = new JsonArray();

                for (int i = 0; i < results.size(); i++)
                {
                    var row = results.getJsonObject(i);

                    var entry = new JsonObject()
                            .put(Constants.DISCOVERY_PROFILE_ID, row.getString(Constants.DATABASE_DISCOVERY_PROFILE_NAME))
                            .put(Constants.DATA, row.getJsonObject(Constants.DATA));

                    responseArray.add(entry);
                }

                context.response().setStatusCode(200).end(new JsonObject().put(Constants.DATA, responseArray).encode());

            }
            else
            {
                logger.error(" Failed to fetch all provision data: {}", reply.cause().getMessage());

                context.response().setStatusCode(500).end(new JsonObject().put(Constants.MESSAGE, Constants.MESSAGE_INTERNAL_SERVER_ERROR).encode());
            }
        });
    }
}
