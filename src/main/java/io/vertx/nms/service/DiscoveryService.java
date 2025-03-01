package io.vertx.nms.service;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.nms.util.PingUtil;
import io.vertx.nms.database.QueryBuilder;
import io.vertx.nms.util.ZmqClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class DiscoveryService
{
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryService.class);

    private final EventBus eventBus;

    private final Vertx vertx;

    private final QueryBuilder queryBuilder;

    private static final Set<String> VALID_FIELDS = new HashSet<>();

    static
    {
        VALID_FIELDS.add("discovery_profile_name");

        VALID_FIELDS.add("credential_profile_name");

        VALID_FIELDS.add("ip");
    }

    public DiscoveryService(Vertx vertx, QueryBuilder queryBuilder)
    {
        this.eventBus = vertx.eventBus();

        this.queryBuilder = queryBuilder;

        this.vertx=vertx;
    }

    public void getAllDiscoveries(RoutingContext ctx)
    {
        JsonObject request = new JsonObject()
                .put("tableName", "discovery")
                .put("operation", "select")
                .put("columns", new JsonArray().add("*"));

        String query = queryBuilder.buildQuery(request);

        logger.info("[{}] Generated Query: {}", Thread.currentThread().getName(), query);

        eventBus.request("database.query.execute", new JsonObject().put("query", query), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(200).end(reply.result().body().toString());
            }
            else
            {
                logger.error("[{}] Failed to process GET all discoveries request: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end("Internal Server Error");
            }
        });
    }

    public void getDiscoveryByProfileName(String profileName, RoutingContext ctx)
    {
        if (profileName == null || profileName.isEmpty())
        {
            ctx.response().setStatusCode(400).end("Invalid Request: Profile name is required");

            return;
        }

        String conditionString = "discovery_profile_name = '" + profileName + "'";

        JsonObject request = new JsonObject()
                .put("tableName", "discovery")
                .put("operation", "select")
                .put("columns", new JsonArray().add("*"))
                .put("condition", conditionString);

        String query = queryBuilder.buildQuery(request);

        logger.info("[{}] Generated Query: {}", Thread.currentThread().getName(), query);

        eventBus.request("database.query.execute", new JsonObject().put("query", query), reply ->
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

    public void createDiscovery(JsonObject requestBody, RoutingContext ctx)
    {
        if(!isValidDiscoveryRequest(requestBody)) return;

        JsonObject request = new JsonObject()
                .put("tableName", "discovery")
                .put("operation", "insert")
                .put("data", requestBody);

        String query = queryBuilder.buildQuery(request);

        logger.info("[{}] Generated Query: {}", Thread.currentThread().getName(), query);

        eventBus.request("database.query.execute", new JsonObject().put("query", query), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(201).end("Discovery Created Successfully");
            }
            else
            {
                logger.error("[{}] Failed to create discovery: {}", Thread.currentThread().getName(), reply.cause().getMessage());
                ctx.response().setStatusCode(500).end("Internal Server Error");
            }
        });
    }

    public void updateDiscovery(String profileName, JsonObject updateRequest, RoutingContext ctx)
    {
        if (profileName == null || profileName.isEmpty())
        {
            ctx.response().setStatusCode(400).end("Invalid Request: Profile name is required");

            return;
        }

        if(!isValidUpdateRequest(updateRequest)) return;

        String condition = "discovery_profile_name = '" + profileName + "'";

        JsonObject request = new JsonObject()
                .put("tableName", "discovery")
                .put("operation", "update")
                .put("data", updateRequest)
                .put("condition", condition);

        String query = queryBuilder.buildQuery(request);

        logger.info("[{}] Generated Query: {}", Thread.currentThread().getName(), query);

        eventBus.request("database.query.execute", new JsonObject().put("query", query), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(200).end("Discovery Updated Successfully");
            }
            else
            {
                logger.error("[{}] Failed to update discovery: {}", Thread.currentThread().getName(), reply.cause().getMessage());
                ctx.response().setStatusCode(500).end("Internal Server Error");
            }
        });
    }

    public void deleteDiscovery(String discoveryProfileName, RoutingContext ctx)
    {
        if (discoveryProfileName == null || discoveryProfileName.isEmpty())
        {
            ctx.response().setStatusCode(400).end("Invalid Request: Discovery profile name is required");
            return;
        }

        String condition = "discovery_profile_name = '" + discoveryProfileName + "'";

        JsonObject request = new JsonObject()
                .put("tableName", "discovery")
                .put("operation", "delete")
                .put("condition", condition);

        String query = queryBuilder.buildQuery(request);

        logger.info("[{}] Generated Query: {}", Thread.currentThread().getName(), query);

        eventBus.request("database.query.execute", new JsonObject().put("query", query), reply ->
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

    public void runDiscovery(String discoveryProfileName, RoutingContext ctx) {
        JsonObject request = new JsonObject().put("query", "SELECT * FROM discovery WHERE discovery_profile_name = '" + discoveryProfileName + "'");

        eventBus.request("database.query.execute", request, fetchResult -> {
            if (fetchResult.failed()) {
                logger.error("Failed to fetch discovery for profile name: {}", discoveryProfileName);
                ctx.response().setStatusCode(500).end("Internal server error");
                return;
            }

            JsonObject result = (JsonObject) fetchResult.result().body();
            JsonArray dataArray = result.getJsonArray("data");

            if (dataArray == null || dataArray.isEmpty()) {
                logger.error("No discovery data found for profile name: {}", discoveryProfileName);
                ctx.response().setStatusCode(404).end("Discovery not found");
                return;
            }

            JsonObject discovery = dataArray.getJsonObject(0);
            String targetIp = discovery.getString("ip");
            String credentialProfileName = discovery.getString("credential_profile_name");

            if (targetIp == null || targetIp.isEmpty()) {
                ctx.response().setStatusCode(400).end("Target IP not found in discovery data");
                return;
            }

            logger.info("Found IP for request: {}", targetIp);

            vertx.executeBlocking(promise -> {
                boolean isReachable = PingUtil.ping(targetIp);
                promise.complete(isReachable);
            }, res -> {
                if (res.succeeded() && (Boolean) res.result()) {
                    String query = "SELECT * FROM credential WHERE credential_profile_name = '" + credentialProfileName + "'";
                    logger.info("Credential Query: {}", query);

                    eventBus.request("database.query.execute", new JsonObject().put("query", query), credentialResult -> {
                        if (credentialResult.failed()) {
                            logger.error("Failed to fetch credential for profile: {}", credentialProfileName);
                            ctx.response().setStatusCode(500).end("Failed to fetch credential");
                            return;
                        }

                        JsonObject credentialResultBody = (JsonObject) credentialResult.result().body();
                        JsonArray credentialData = credentialResultBody.getJsonArray("data");

                        if (credentialData == null || credentialData.isEmpty()) {
                            ctx.response().setStatusCode(404).end("No credential data found");
                            return;
                        }

                        JsonObject credential = credentialData.getJsonObject(0);
                        String community = credential.getString("community");
                        String version = credential.getString("version");
                        String deviceType = credential.getString("system_type");

                        JsonObject zmqRequest = new JsonObject()
                                .put("ip", targetIp)
                                .put("community", community)
                                .put("version", version)
                                .put("pluginType", deviceType)
                                .put("requestType", "discovery");

                        logger.info("ZMQ Request: " + zmqRequest);

                        eventBus.request("zmq.send", zmqRequest, zmqResult -> {
                            if (zmqResult.failed()) {
                                logger.info("No response from ZMQ server");
                                ctx.response().setStatusCode(504).end("No response from ZMQ server");
                                return;
                            }

                            String zmqResponseStr = (String) zmqResult.result().body();
                            logger.info("ZMQ Response: " + zmqResponseStr);

                            JsonObject zmqResponse = new JsonObject(zmqResponseStr);
                            boolean isSuccess = "success".equalsIgnoreCase(zmqResponse.getString("status"));

                            JsonObject updateRequest = new JsonObject()
                                    .put("tableName", "discovery")
                                    .put("operation", "update")
                                    .put("data", new JsonObject().put("status", isSuccess))
                                    .put("condition", "discovery_profile_name = '" + discoveryProfileName + "'");

                            String updateQuery = queryBuilder.buildQuery(updateRequest);

                            eventBus.request("database.query.execute", new JsonObject().put("query", updateQuery), updateResult -> {
                                if (updateResult.succeeded()) {
                                    logger.info("Discovery status updated to '{}' for profile name: {}", isSuccess, discoveryProfileName);

                                    if (isSuccess) {
                                        ctx.response().setStatusCode(200).end("Discovery run successful system name: " + zmqResponse.getString("systemName"));
                                    } else {
                                        ctx.response().setStatusCode(500).end("Discovery failed for IP: " + targetIp);
                                    }
                                } else {
                                    logger.error("Failed to update discovery status for profile name: {}", discoveryProfileName);
                                    ctx.response().setStatusCode(500).end("Failed to update discovery status");
                                }
                            });
                        });
                    });
                } else {
                    logger.error("Ping failed for IP: {}", targetIp);
                    ctx.response().setStatusCode(400).end("Ping failed");
                }
            });
        });
    }


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
        return requestBody.containsKey("discovery_profile_name") &&
                requestBody.containsKey("credential_profile_name") &&
                requestBody.containsKey("ip");
    }

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
