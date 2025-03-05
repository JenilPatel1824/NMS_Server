package io.vertx.nms.service;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.nms.constants.Constants;
import io.vertx.nms.database.QueryBuilder;
import io.vertx.nms.network.ConnectivityTester;
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

    static
    {
        VALID_FIELDS.add(Constants.DISCOVERY_PROFILE_NAME_KEY);

        VALID_FIELDS.add(Constants.CREDENTIAL_PROFILE_NAME_KEY);

        VALID_FIELDS.add("ip");
    }

    public DiscoveryService(Vertx vertx)
    {
        this.eventBus = vertx.eventBus();

        this.vertx=vertx;
    }

    // Fetches all discovery profiles from the database.
    // @param ctx The RoutingContext containing the request and response.
    public void getAllDiscoveries(RoutingContext ctx)
    {
        JsonObject request = new JsonObject()
                .put(Constants.TABLE_NAME_KEY, Constants.DATABASE_TABLE_DISCOVERY_PROFILE)
                .put(Constants.OPERATION_KEY, Constants.DATABASE_OPERATION_SELECT)
                .put(Constants.COLUMNS_KEY, new JsonArray().add("*"));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject()
                .put(Constants.QUERY_KEY, queryResult.getQuery())
                .put(Constants.PARAMS_KEY, queryResult.getParams()), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(200).end(reply.result().body().toString());
            }
            else
            {
                logger.error("[{}] Failed to process GET all discoveries request: {}",
                        Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end("Internal Server Error");
            }
        });
    }
    
    // Fetches a specific discovery profile from the database.
    // @param profileName The name of the discovery profile to fetch.
    // @param ctx The RoutingContext containing the request and response.
    public void getDiscoveryByProfileName(String profileName, RoutingContext ctx)
    {
        if (profileName == null || profileName.isEmpty())
        {
            ctx.response().setStatusCode(400).end("Invalid Request: Profile name is required");

            return;
        }

        JsonObject request = new JsonObject()
                .put(Constants.TABLE_NAME_KEY, Constants.DATABASE_TABLE_DISCOVERY_PROFILE)
                .put(Constants.OPERATION_KEY, Constants.DATABASE_OPERATION_SELECT)
                .put(Constants.COLUMNS_KEY, new JsonArray().add("*"))
                .put(Constants.CONDITION_KEY, new JsonObject().put(Constants.DISCOVERY_PROFILE_NAME_KEY, profileName));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY_KEY, queryResult.getQuery()).put(Constants.PARAMS_KEY,queryResult.getParams()), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(200).end(reply.result().body().toString());
            }
            else
            {
                logger.error("[{}] Failed to process GET by profile name request: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end("Internal Server Error");
            }
        });
    }

    // Creates a new discovery profile in the database.
    // @param requestBody The JSON object containing the discovery profile details.
    // @param ctx The RoutingContext containing the request and response.
    public void createDiscovery(JsonObject requestBody, RoutingContext ctx)
    {
        if(!isValidDiscoveryRequest(requestBody))
        {
            ctx.response().setStatusCode(400).end("invalid fields");

            return;

        }
        JsonObject request = new JsonObject()
                .put(Constants.TABLE_NAME_KEY, Constants.DATABASE_TABLE_DISCOVERY_PROFILE)
                .put(Constants.OPERATION_KEY, Constants.DATABASE_OPERATION_INSERT)
                .put(Constants.DATA_KEY, requestBody);

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY_KEY, queryResult.getQuery()).put(Constants.PARAMS_KEY,queryResult.getParams()), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(201).end("Discovery Created Successfully");
            }
            else
            {
                logger.error("[{}] Failed to create discovery: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end("Internal Server Error"+reply.cause().getMessage());
            }
        });
    }

    // Updates an existing discovery profile in the database.
    // @param profileName The name of the discovery profile to update.
    // @param updateRequest The JSON object containing the updated discovery profile details.
    // @param ctx The RoutingContext containing the request and response.
    public void updateDiscovery(String profileName, JsonObject updateRequest, RoutingContext ctx)
    {
        if (profileName == null || profileName.isEmpty())
        {
            ctx.response().setStatusCode(400).end("Invalid Request: Profile name is required");

            return;
        }

        if(!isValidUpdateRequest(updateRequest)) return;

        JsonObject request = new JsonObject()
                .put(Constants.TABLE_NAME_KEY, Constants.DATABASE_TABLE_DISCOVERY_PROFILE)
                .put(Constants.OPERATION_KEY, Constants.DATABASE_OPERATION_UPDATE)
                .put(Constants.DATA_KEY, updateRequest)
                .put(Constants.CONDITION_KEY, new JsonObject().put(Constants.DISCOVERY_PROFILE_NAME_KEY, profileName));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY_KEY, queryResult.getQuery()).put(Constants.PARAMS_KEY,queryResult.getParams()), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(200).end("Discovery Updated Successfully");
            }
            else
            {
                logger.error("[{}] Failed to update discovery: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end("Internal Server Error" +reply.cause());
            }
        });
    }

    // Deletes a discovery profile from the database.
    // @param discoveryProfileName The name of the discovery profile to delete.
    public void deleteDiscovery(String discoveryProfileName, RoutingContext ctx)
    {
        if (discoveryProfileName == null || discoveryProfileName.isEmpty())
        {
            ctx.response().setStatusCode(400).end("Invalid Request: Discovery profile name is required");

            return;
        }

        JsonObject request = new JsonObject()
                .put(Constants.TABLE_NAME_KEY, Constants.DATABASE_TABLE_DISCOVERY_PROFILE)
                .put(Constants.OPERATION_KEY, Constants.DATABASE_OPERATION_DELETE)
                .put(Constants.CONDITION_KEY, new JsonObject().put(Constants.DISCOVERY_PROFILE_NAME_KEY, discoveryProfileName)); // Correctly formatted condition

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);


        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY_KEY, queryResult.getQuery()).put(Constants.PARAMS_KEY,queryResult.getParams()), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(200).end("Discovery Deleted Successfully");
            }
            else
            {
                logger.error("[{}] Failed to delete discovery: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end("Internal Server Error");
            }
        });
    }

    // Runs a discovery for the given discovery profile.
    // This method is responsible for running a discovery for a given discovery profile.
    // It fetches the discovery profile from the database, pings the target IP, and sends a ZMQ request to the ZMQ server.
    // The ZMQ server responds with the discovery data, which is then updated in the database.
    // @param discoveryProfileName The name of the discovery profile to run.
    // @param ctx The RoutingContext containing the request and response.
    public void runDiscovery(String discoveryProfileName, RoutingContext ctx)
    {
        String queryForDiscovery = "SELECT dp.ip, dp.discovery_profile_name, cp.system_type, cp.credentials " +
                "FROM discovery_profiles dp " +
                "JOIN credential_profile cp ON dp.credential_profile_name = cp.credential_profile_name " +
                "WHERE dp.discovery_profile_name = $1";

        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY_KEY,queryForDiscovery).put(Constants.PARAMS_KEY,new JsonArray().add(discoveryProfileName)), fetchResult ->
        {
            if (fetchResult.failed())
            {
                logger.error("Failed to fetch discovery for profile name: {}", discoveryProfileName);

                ctx.response().setStatusCode(500).end("Internal server error");

                return;
            }

            JsonObject result = (JsonObject) fetchResult.result().body();

            JsonArray dataArray = result.getJsonArray(Constants.DATA_KEY);

            if (dataArray == null || dataArray.isEmpty())
            {
                logger.error("No discovery data found for profile name: {}", discoveryProfileName);

                ctx.response().setStatusCode(404).end("Discovery not found");

                return;
            }

            JsonObject discovery = dataArray.getJsonObject(0);

            String targetIp = discovery.getString("ip");

            if (targetIp == null || targetIp.isEmpty())
            {
                ctx.response().setStatusCode(400).end("Target IP not found in discovery data");

                return;
            }

            logger.info("Found IP for request: {}", targetIp);

            vertx.executeBlocking(promise ->
            {
                boolean isReachable = ConnectivityTester.ping(targetIp);

                promise.complete(isReachable);
            }, res ->
            {
                if (res.succeeded() && (Boolean) res.result())
                {
                    String deviceType = discovery.getString("system_type");

                    if(!deviceType.equalsIgnoreCase("snmp"))
                    {
                        ctx.response().setStatusCode(400).end("Invalid device type, discovery is not supported for this device type");

                        return;
                    }

                    JsonObject credentials = discovery.getJsonObject("credentials");

                    if (credentials == null)
                    {
                        ctx.response().setStatusCode(500).end("Invalid credentials format");

                        return;
                    }

                    JsonObject zmqRequest = new JsonObject()
                            .put("ip", targetIp)
                            .put("community", credentials.getString("community"))
                            .put("version", credentials.getString("version"))
                            .put("pluginType", deviceType)
                            .put("requestType", "discovery");

                    eventBus.request("zmq.send", zmqRequest, zmqResult ->
                    {
                        if (zmqResult.failed())
                        {
                            logger.info("No response from ZMQ server");

                            ctx.response().setStatusCode(504).end("No response from ZMQ server");

                            return;
                        }

                        JsonObject zmqResponseJson = (JsonObject) zmqResult.result().body();

                        boolean isSuccess = "success".equalsIgnoreCase(zmqResponseJson.getString("status"));

                        JsonObject request = new JsonObject()
                                .put(Constants.OPERATION_KEY, Constants.DATABASE_OPERATION_UPDATE)
                                .put(Constants.TABLE_NAME_KEY, Constants.DATABASE_TABLE_DISCOVERY_PROFILE)
                                .put(Constants.DATA_KEY, new JsonObject().put("discovery", isSuccess))
                                .put(Constants.CONDITION_KEY, new JsonObject().put(Constants.DISCOVERY_PROFILE_NAME_KEY, discoveryProfileName));

                        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

                        eventBus.request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY_KEY,queryResult.getQuery()).put(Constants.PARAMS_KEY,queryResult.getParams()), updateResult ->
                         {
                             if (updateResult.succeeded())
                             {
                                 if (isSuccess)
                                 {
                                     ctx.response().setStatusCode(200).end("Discovery run successful. System name: " + zmqResponseJson.getString("systemName"));
                                 }
                                 else
                                 {
                                     ctx.response().setStatusCode(500).end("Discovery failed for IP: " + targetIp);
                                 }
                             }
                             else
                             {
                                 logger.error("Failed to update discovery status for profile name: {}", discoveryProfileName);

                                 ctx.response().setStatusCode(500).end("Failed to update discovery status");
                             }
                         });
                    });
                }
                else
                {
                    logger.error("Ping failed for IP: {}", targetIp);

                    ctx.response().setStatusCode(400).end("Ping failed");
                }
            });
        });
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
        return requestBody.containsKey(Constants.DISCOVERY_PROFILE_NAME_KEY) &&
                requestBody.containsKey(Constants.CREDENTIAL_PROFILE_NAME_KEY) &&
                requestBody.containsKey("ip");
    }

    // Validates the update request body for valid fields.
    private boolean isValidUpdateRequest(JsonObject updateRequest)
    {
        for (String key : updateRequest.fieldNames())
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
