package io.vertx.nms.service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.nms.util.Constants;
import io.vertx.nms.database.QueryBuilder;
import io.vertx.nms.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Service
{
    private static final Logger logger = LoggerFactory.getLogger(Service.class);

    private final Vertx vertx;

    private static final String DISCOVERY_NOT_FOUND = "Discovery not found or credential profile deleted";

    private static final String IP_NOT_FOUND = "Target IP not found in discovery data";

    private static final String INVALID_DEVICE_TYPE = "Target IP not found in discovery data";

    private static final String INVALID_CREDENTIAL_FORMAT = "Invalid credentials format";

    private static final String DISCOVERY_SUCCESSFUL = "Discovery run successful. id: ";

    private static final String DISCOVERY_FAIL = "Discovery failed for IP: ";

    private static final String DISCOVERY_STATUS_FAIL = "Failed to update discovery status";

    private static final String PING_FAIL = "Ping Failed ";

    private static final String YES = "yes";

    private static final String NO = "no";

    private static final String PROVISION = "provision";

    private static final String INVALID_STATUS = "Bad Request: Status must be either 'yes' or 'no'";

    private static final String PROVISION_UPDATE_SUCCESSFUL = "Provision status updated successfully";

    private static final String DATA_NOT_FOUND ="No data found for discoveryProfileId: ";

    public Service(Vertx vertx)
    {
        this.vertx = vertx;
    }

    public void create(JsonObject requestBody, RoutingContext context)
    {
        if (!Util.isValidRequest(requestBody, context))
        {
            return;
        }

        var request = new JsonObject()
                .put(Constants.TABLE_NAME, Util.getTableNameFromContext(context))
                .put(Constants.OPERATION, Constants.INSERT)
                .put(Constants.DATA, requestBody);

        executeQuery(context, request, 201);
    }

    public void getById(String id, RoutingContext context)
    {
        var tableName = Util.getTableNameFromContext(context);

        if (tableName == null)
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_BAD_REQUEST);

            return;
        }

        try
        {
            var parsedId = Long.parseLong(id);

            var request = new JsonObject()
                    .put(Constants.TABLE_NAME, tableName)
                    .put(Constants.OPERATION, Constants.SELECT)
                    .put(Constants.COLUMNS, new JsonArray().add(Constants.DATABASE_ALL_COLUMN))
                    .put(Constants.CONDITION, new JsonObject().put(Constants.ID, parsedId));

            executeQuery(context, request, 200);
        }
        catch (NumberFormatException e)
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
        }
    }

    public void update(String id, JsonObject requestBody, RoutingContext context)
    {
        var tableName = Util.getTableNameFromContext(context);

        if (tableName == null)
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_BAD_REQUEST);

            return;
        }

        try
        {
            var profileId = Long.parseLong(id);

            if (!Util.isValidRequest( requestBody, context))
            {
                return;
            }

            var request = new JsonObject()
                    .put(Constants.TABLE_NAME, tableName)
                    .put(Constants.OPERATION, Constants.UPDATE)
                    .put(Constants.DATA, requestBody)
                    .put(Constants.CONDITION, new JsonObject().put(Constants.ID, profileId));

            executeQuery(context, request, 200);
        }
        catch (NumberFormatException e)
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
        }
    }

    public void getAll(RoutingContext context)
    {
        var tableName = Util.getTableNameFromContext(context);

        if (tableName == null)
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_BAD_REQUEST);

            return;
        }

        try
        {
            var request = new JsonObject()
                    .put(Constants.TABLE_NAME, tableName)
                    .put(Constants.OPERATION, Constants.SELECT)
                    .put(Constants.COLUMNS, new JsonArray().add(Constants.DATABASE_ALL_COLUMN));

            executeQuery(context, request, 200);
        }
        catch (Exception e)
        {
            context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
        }
    }

    public void delete(String id, RoutingContext context)
    {
        var tableName = Util.getTableNameFromContext(context);

        if (tableName == null)
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_BAD_REQUEST);

            return;
        }

        if (id == null || id.trim().isEmpty())
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);

            return;
        }

        try
        {
            var parsedId = Long.parseLong(id);

            var request = new JsonObject()
                    .put(Constants.TABLE_NAME, tableName)
                    .put(Constants.OPERATION, Constants.DELETE)
                    .put(Constants.CONDITION, new JsonObject().put(Constants.ID, parsedId));

            executeQuery(context, request, 204);
        }
        catch (NumberFormatException e)
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
        }
        catch (Exception e)
        {
            context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
        }
    }

    private void executeQuery(RoutingContext context, JsonObject request, int successStatusCode)
    {
        var queryResult = QueryBuilder.buildQuery(request);

        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS,
                new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()), reply ->
                {
                    if (reply.succeeded())
                    {
                        context.response().setStatusCode(successStatusCode).end(reply.result().body().toString());
                    }
                    else
                    {
                        logger.error("Database operation failed: {}", reply.cause().getMessage());

                        context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR + reply.cause().getMessage());
                    }
                });
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

            var queryForDiscovery = "SELECT dp.id, dp.ip, dp.discovery_profile_name, cp.id as credential_profile_id, cp.system_type, cp.credentials " +
                    "FROM discovery_profiles dp " +
                    "JOIN credential_profile cp ON dp.credential_profile_id = cp.id " +
                    "WHERE dp.id = $1";

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS,
                    new JsonObject()
                            .put(Constants.QUERY, queryForDiscovery)
                            .put(Constants.PARAMS, new JsonArray().add(profileId)),
                    fetchResult ->
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
                            logger.error("No discovery data found or credential profile deleted for profile id: {}", discoveryProfileId);

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
                            var isReachable = Util.ping(targetIp);

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

                                vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_ZMQ_ADDRESS, zmqRequest, zmqResult ->
                                {
                                    if (zmqResult.failed())
                                    {
                                        logger.info(Constants.MESSAGE_ZMQ_NO_RESPONSE);

                                        context.response().setStatusCode(504).end(Constants.MESSAGE_ZMQ_NO_RESPONSE);

                                        return;
                                    }

                                    var zmqResponseJson = zmqResult.result().body();

                                    logger.info("ZMQ Response: {}", zmqResponseJson);

                                    var isSuccess = Constants.SUCCESS.equalsIgnoreCase(zmqResponseJson.getString(Constants.STATUS));

                                    if (!isSuccess)
                                    {
                                        context.response().setStatusCode(500).end(DISCOVERY_FAIL + targetIp);

                                        return;
                                    }

                                    var checkExistingQuery = "SELECT id,credential_profile_id FROM provisioning_jobs WHERE ip = $1";

                                    vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS,
                                            new JsonObject()
                                                    .put(Constants.QUERY, checkExistingQuery)
                                                    .put(Constants.PARAMS, new JsonArray().add(targetIp)),
                                            checkResult ->
                                            {
                                                if (checkResult.failed())
                                                {
                                                    logger.error("Failed to check existing provisioning job for profile id: {}", discoveryProfileId);

                                                    context.response().setStatusCode(500).end(DISCOVERY_STATUS_FAIL);

                                                    return;
                                                }

                                                var existingDataArray = checkResult.result().body().getJsonArray(Constants.DATA);

                                                if (existingDataArray != null && !existingDataArray.isEmpty())
                                                {
                                                    var existingId = existingDataArray.getJsonObject(0).getLong(Constants.ID);

                                                    if(existingDataArray.getJsonObject(0).getLong(Constants.DATABASE_CREDENTIAL_PROFILE_ID)==null)
                                                    {
                                                        var request = new JsonObject().put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISIONING_JOBS)
                                                                .put(Constants.OPERATION, Constants.UPDATE)
                                                                .put(Constants.DATA,new JsonObject().put(Constants.DATABASE_CREDENTIAL_PROFILE_ID,dataArray.getJsonObject(0).getLong(Constants.DATABASE_CREDENTIAL_PROFILE_ID)))
                                                                .put(Constants.CONDITION, new JsonObject().put(Constants.ID, existingId));

                                                        var queryResult = QueryBuilder.buildQuery(request);

                                                        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS,new JsonObject().put(Constants.QUERY,queryResult.getQuery()).put(Constants.PARAMS,queryResult.getParams()),reply->
                                                        {
                                                            if(!reply.succeeded())
                                                            {
                                                                logger.error("Failed to update provisioning job for profile id: {}", discoveryProfileId);

                                                                context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);

                                                            }
                                                        });
                                                    }

                                                    logger.info("Provisioning job already exists for profile id: {}, returning existing ID: {}", discoveryProfileId, existingId);

                                                    context.response().setStatusCode(200).end("Monitor already exists with ID: " + existingId);

                                                    return;
                                                }

                                                var request = new JsonObject()
                                                        .put(Constants.OPERATION, Constants.INSERT)
                                                        .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISIONING_JOBS)
                                                        .put(Constants.DATA, new JsonObject()
                                                                .put(Constants.STATUS, false)
                                                                .put(Constants.IP, targetIp)
                                                                .put(Constants.DATABASE_CREDENTIAL_PROFILE_ID, result.getJsonArray(Constants.DATA).getJsonObject(0).getLong(Constants.DATABASE_CREDENTIAL_PROFILE_ID))
                                                        );

                                                var queryResult = QueryBuilder.buildQuery(request);

                                                vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS,
                                                        new JsonObject()
                                                                .put(Constants.QUERY, queryResult.getQuery())
                                                                .put(Constants.PARAMS, queryResult.getParams()),
                                                        updateResult ->
                                                        {
                                                            if (updateResult.succeeded())
                                                            {
                                                                var insertedId = updateResult.result().body().getLong(Constants.ID);

                                                                context.response().setStatusCode(200).end(DISCOVERY_SUCCESSFUL + insertedId);

                                                            }
                                                            else
                                                            {
                                                                logger.error("Failed to insert provisioning job for profile id: {}", discoveryProfileId);

                                                                context.response().setStatusCode(500).end(DISCOVERY_STATUS_FAIL);
                                                            }
                                                        });
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

    public void updateProvisionStatus(String provisionId, String status, RoutingContext context)
    {
        try
        {
            boolean statusForDatabase;

            if(status.equalsIgnoreCase(YES))
            {
                statusForDatabase = true;
            }
            else if(status.equalsIgnoreCase(NO))
            {
                statusForDatabase = false;
            }
            else
            {
                context.response().setStatusCode(400).end(INVALID_STATUS);

                return;
            }
            var profileId = Long.parseLong(provisionId);

            var request= new JsonObject()
                    .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISIONING_JOBS)
                    .put(Constants.OPERATION, Constants.UPDATE)
                    .put(Constants.DATA, new JsonObject().put(Constants.STATUS, statusForDatabase))
                    .put(Constants.CONDITION, new JsonObject().put(Constants.ID, profileId));

            var queryResult = QueryBuilder.buildQuery(request);

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS,
                    new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()),
                    reply ->
                    {
                        if (reply.succeeded())
                        {
                            var result = reply.result().body();

                            logger.info("Update result: {}", result);

                            if (result.getLong(Constants.ID)==null)
                            {
                                context.response().setStatusCode(404).end("No monitor found");
                            }
                            else
                            {
                                context.response().setStatusCode(200).end("Provision status updated");
                            }
                        }
                        else
                        {
                            logger.error("Operation failed: {}", reply.cause().getMessage());

                            context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
                        }
                    });
        }
        catch (NumberFormatException e)
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
        }
    }

    // Fetches provision data for a specific discovery profile.
    // @param discoveryProfileId The id of the discovery profile to fetch data for.
    // @param context The RoutingContext containing the request and response.
    public void getProvisionData(String jobId, RoutingContext context)
    {
        try
        {
            var jobIdLong = Long.parseLong(jobId);

            var request = new JsonObject()
                    .put(Constants.OPERATION, Constants.SELECT)
                    .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISION_DATA)
                    .put(Constants.COLUMNS, new JsonArray()
                            .add(Constants.DATA)
                            .add(Constants.POLLED_AT))
                    .put(Constants.CONDITION, new JsonObject()
                            .put(Constants.JOB_ID, jobIdLong));

            var queryResult = QueryBuilder.buildQuery(request);

            var fetchRequest = new JsonObject()
                    .put(Constants.QUERY, queryResult.getQuery() + " ORDER BY polled_at DESC")
                    .put(Constants.PARAMS, queryResult.getParams());

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, fetchRequest, reply ->
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
                        context.response().setStatusCode(404).end(new JsonObject().put(Constants.MESSAGE, "No data found for job ID: " + jobId).encode());

                        return;
                    }

                    var responseArray = new JsonArray();

                    results.forEach(row ->
                    {
                        var rowObj = (JsonObject) row;

                        responseArray.add(new JsonObject()
                                .put(Constants.DATA, rowObj.getJsonObject(Constants.DATA))
                                .put(Constants.POLLED_AT, rowObj.getString(Constants.POLLED_AT)));
                    });

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
            logger.error("Invalid job ID: {}", jobId);

            context.response().setStatusCode(400).end(new JsonObject().put(Constants.MESSAGE, Constants.MESSAGE_INVALID_PROFILE_ID).encode());
        }
    }
}
