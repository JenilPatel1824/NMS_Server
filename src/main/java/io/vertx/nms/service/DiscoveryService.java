package io.vertx.nms.service;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.nms.util.Constants;
import io.vertx.nms.database.QueryBuilder;
import io.vertx.nms.util.ConnectivityTester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class DiscoveryService
{
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryService.class);

    private final EventBus eventBus;

    private final Vertx vertx;

    private static final Set<String> VALID_FIELDS = new HashSet<>();

    private static final String DISCOVERY_DELETE_SUCCESSFUL = "Discovery Deleted Successfully";

    private static final String DISCOVERY_NOT_FOUND = "Discovery not found";

    private static final String IP_NOT_FOUND = "Target IP not found in discovery data";

    private static final String INVALID_DEVICE_TYPE = "Target IP not found in discovery data";

    private static final String INVALID_CREDENTIAL_FORMAT = "Invalid credentials format";

    private static final String DISCOVERY_SUCCESSFUL = "Discovery run successful. System name: ";

    private static final String DISCOVERY_FAIL = "Discovery failed for IP: ";

    private static final String DISCOVERY_STATUS_FAIL = "Failed to update discovery status";

    private static final String PING_FAIL = "Ping Failed ";


    static
    {
        VALID_FIELDS.add(Constants.DATABASE_DISCOVERY_PROFILE_NAME);

        VALID_FIELDS.add(Constants.DATABASE_CREDENTIAL_PROFILE_ID);

        VALID_FIELDS.add(Constants.IP);
    }

    public DiscoveryService(Vertx vertx)
    {
        this.eventBus = vertx.eventBus();

        this.vertx=vertx;
    }

    // Fetches all discovery profiles from the database.
    // @param context The RoutingContext containing the request and response.
    public void getAllDiscoveries(RoutingContext context)
    {
        var request = new JsonObject()
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_DISCOVERY_PROFILE)
                .put(Constants.OPERATION, Constants.SELECT)
                .put(Constants.COLUMNS, new JsonArray().add(Constants.DATABASE_ALL_COLUMN));

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
                logger.error("Failed to process GET all discoveries request {}", reply.cause().getMessage());

                context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR + reply.cause().getMessage());
            }
        });
    }

    // Fetches a specific discovery profile from the database.
    // @param discoveryProfileId The id of the discovery profile to fetch.
    // @param context The RoutingContext containing the request and response.
    public void getDiscoveryById(String discoveryProfileId, RoutingContext context)
    {
        try
        {
            var profileId = Long.parseLong(discoveryProfileId);

            var request = new JsonObject()
                    .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_DISCOVERY_PROFILE)
                    .put(Constants.OPERATION, Constants.SELECT)
                    .put(Constants.COLUMNS, new JsonArray().add(Constants.DATABASE_ALL_COLUMN))
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
                    logger.error(" Failed to process GET by profile name request: {}", reply.cause().getMessage());

                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR + reply.cause().getMessage());
                }
            });
        }
        catch (NumberFormatException e)
        {
            logger.error("Invalid profileId: {}", discoveryProfileId);

            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
        }
    }

    // Creates a new discovery profile in the database.
    // @param requestBody The JSON object containing the discovery profile details.
    // @param context The RoutingContext containing the request and response.
    public void createDiscovery(JsonObject requestBody, RoutingContext context)
    {
        if(!isValidDiscoveryRequest(requestBody))
        {
            context.response().setStatusCode(400).end("invalid fields");

            return;
        }

        var request = new JsonObject()
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_DISCOVERY_PROFILE)
                .put(Constants.OPERATION, Constants.INSERT)
                .put(Constants.DATA, requestBody);

        var queryResult = QueryBuilder.buildQuery(request);

        eventBus.<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS,queryResult.getParams()), reply ->
        {
            if (reply.succeeded())
            {
                context.response().setStatusCode(201).end(reply.result().body().toString());
            }
            else
            {
                logger.error(" Failed to create discovery: {}", reply.cause().getMessage());

                context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR+reply.cause().getMessage());
            }
        });
    }

    // Updates an existing discovery profile in the database.
    // @param discoveryProfileId The id of the discovery profile to update.
    // @param updateRequest The JSON object containing the updated discovery profile details.
    // @param context The RoutingContext containing the request and response.
    public void updateDiscovery(String discoveryProfileId, JsonObject updateRequest, RoutingContext context)
    {
        if(!isValidUpdateRequest(updateRequest)) return;

        try
        {
            var profileId = Long.parseLong(discoveryProfileId);

            var request = new JsonObject()
                    .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_DISCOVERY_PROFILE)
                    .put(Constants.OPERATION, Constants.UPDATE)
                    .put(Constants.DATA, updateRequest)
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
                    logger.error(" Failed to update discovery {}", reply.cause().getMessage());

                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR + reply.cause().getMessage());
                }
            });
        }
        catch (NumberFormatException e)
        {
            logger.error("Invalid profileId: {}", discoveryProfileId);

            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
        }
    }

    // Deletes a discovery profile from the database.
    // @param discoveryProfileId The id of the discovery profile to delete.
    public void deleteDiscovery(String discoveryProfileId, RoutingContext context)
    {
        try
        {
            var profileId = Long.parseLong(discoveryProfileId);

            var request = new JsonObject()
                    .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_DISCOVERY_PROFILE)
                    .put(Constants.OPERATION, Constants.DELETE)
                    .put(Constants.CONDITION, new JsonObject().put(Constants.ID, profileId));

            var queryResult = QueryBuilder.buildQuery(request);

            eventBus.<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()), reply ->
            {
                if (reply.succeeded())
                {
                    context.response().setStatusCode(200).end(DISCOVERY_DELETE_SUCCESSFUL);
                }
                else
                {
                    logger.error(" Failed to delete discovery: {}", reply.cause().getMessage());

                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR + reply.cause().getMessage());
                }
            });
        }
        catch (NumberFormatException e)
        {
            logger.error("Invalid profileId: {}", discoveryProfileId);

            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
        }
    }

    // Runs a discovery for the given discovery profile.
    // This method is responsible for running a discovery for a given discovery profile.
    // It fetches the discovery profile from the database, pings the target IP, and sends a ZMQ request to the ZMQ server.
    // The ZMQ server responds with the discovery data, which is then updated in the database.
    // @param discoveryProfileId The id of the discovery profile to run.
    // @param context The RoutingContext containing the request and response.
    public void runDiscovery(String discoveryProfileId, RoutingContext context)
    {
        try
        {
            var profileId = Long.parseLong(discoveryProfileId);

            var queryForDiscovery = "SELECT dp.id, dp.ip, dp.discovery_profile_name, cp.id, cp.system_type, cp.credentials " +
                    "FROM discovery_profiles dp " +
                    "JOIN credential_profile cp ON dp.credential_profile_id = cp.id " +
                    "WHERE dp.id = $1";

            eventBus.<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryForDiscovery).put(Constants.PARAMS, new JsonArray().add(profileId)), fetchResult ->
            {
                if (fetchResult.failed())
                {
                    logger.error("Failed to fetch discovery for profile id: {}", discoveryProfileId);

                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);

                    return;
                }

                var result = fetchResult.result().body();

                var dataArray = result.getJsonArray(Constants.DATA);

                if (dataArray == null || dataArray.isEmpty())
                {
                    logger.error("No discovery data found for profile id: {}", discoveryProfileId);

                    context.response().setStatusCode(404).end(DISCOVERY_NOT_FOUND);

                    return;
                }

                var discovery = dataArray.getJsonObject(0);

                var targetIp = discovery.getString(Constants.IP);

                if (targetIp == null || targetIp.isEmpty())
                {
                    context.response().setStatusCode(400).end(IP_NOT_FOUND);

                    return;
                }

                logger.info("Found IP for request: {}", targetIp);

                vertx.executeBlocking(promise ->
                {
                    var isReachable = ConnectivityTester.ping(targetIp);

                    promise.complete(isReachable);

                }, res ->
                {
                    if (res.succeeded() && (Boolean) res.result())
                    {
                        var deviceType = discovery.getString(Constants.SYSTEM_TYPE);

                        if (!deviceType.equalsIgnoreCase(Constants.SNMP))
                        {
                            context.response().setStatusCode(400).end(INVALID_DEVICE_TYPE);

                            return;
                        }

                        var credentials = discovery.getJsonObject(Constants.CREDENTIALS);

                        if (credentials == null)
                        {
                            context.response().setStatusCode(500).end(INVALID_CREDENTIAL_FORMAT);

                            return;
                        }

                        var zmqRequest = new JsonObject()
                                .put(Constants.IP, targetIp)
                                .put(Constants.COMMUNITY, credentials.getString(Constants.COMMUNITY))
                                .put(Constants.VERSION, credentials.getString(Constants.VERSION))
                                .put(Constants.PLUGIN_TYPE, deviceType)
                                .put(Constants.REQUEST_TYPE, Constants.DISCOVERY);

                        eventBus.<JsonObject>request(Constants.EVENTBUS_ZMQ_ADDRESS, zmqRequest, zmqResult ->
                        {
                            if (zmqResult.failed())
                            {
                                logger.info(Constants.MESSAGE_ZMQ_NO_RESPONSE);

                                context.response().setStatusCode(504).end(Constants.MESSAGE_ZMQ_NO_RESPONSE);

                                return;
                            }

                            var zmqResponseJson = zmqResult.result().body();

                            var isSuccess = Constants.SUCCESS.equalsIgnoreCase(zmqResponseJson.getString(Constants.STATUS));

                            var request = new JsonObject()
                                    .put(Constants.OPERATION, Constants.UPDATE)
                                    .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_DISCOVERY_PROFILE)
                                    .put(Constants.DATA, new JsonObject().put(Constants.DISCOVERY, isSuccess))
                                    .put(Constants.CONDITION, new JsonObject().put(Constants.ID, profileId));

                            var queryResult = QueryBuilder.buildQuery(request);

                            eventBus.<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()), updateResult ->
                            {
                                if (updateResult.succeeded())
                                {
                                    if (isSuccess)
                                    {
                                        context.response().setStatusCode(200).end(DISCOVERY_SUCCESSFUL + zmqResponseJson.getJsonObject(Constants.DATA).getString(Constants.SYSTEM_NAME));
                                    }
                                    else
                                    {
                                        context.response().setStatusCode(500).end(DISCOVERY_FAIL + targetIp);
                                    }
                                }
                                else
                                {
                                    logger.error("Failed to update discovery status for profile name: {}", discoveryProfileId);

                                    context.response().setStatusCode(500).end(DISCOVERY_STATUS_FAIL);
                                }
                            });
                        });
                    }
                    else
                    {
                        logger.error("Ping failed for IP: {}", targetIp);

                        context.response().setStatusCode(400).end(PING_FAIL);
                    }
                });
            });
        }
         catch (NumberFormatException e)
            {
                logger.error("Invalid discoveryProfileName: {}", discoveryProfileId);

                context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);

            }
    }

    // Validates the discovery request body for required fields.
    private boolean isValidDiscoveryRequest(JsonObject requestBody)
    {
        for (String key : requestBody.fieldNames())
        {
            if (!VALID_FIELDS.contains(key))
            {
                logger.error("Invalid Field: {}", key);

                return false;
            }
        }
        return requestBody.containsKey(Constants.DATABASE_DISCOVERY_PROFILE_NAME) &&
                requestBody.containsKey(Constants.DATABASE_CREDENTIAL_PROFILE_ID) &&
                requestBody.containsKey(Constants.IP);
    }

    // Validates the update request body for valid fields.
    private boolean isValidUpdateRequest(JsonObject updateRequest)
    {
        for (var key : updateRequest.fieldNames())
        {
            if (!VALID_FIELDS.contains(key))
            {
                logger.error("Invalid Field in Update Request: {}", key);

                return false;
            }
        }
        return true;
    }
}
