package io.vertx.nms.service;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.nms.util.Constants;
import io.vertx.nms.database.QueryBuilder;
import io.vertx.nms.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Service
{
    private static final Logger logger = LoggerFactory.getLogger(Service.class);

    private final Vertx vertx;

    private static final String DISCOVERY_NOT_FOUND = "Discovery not found or credential profile deleted";

    private static final String IP_NOT_FOUND = "Target IP not found in discovery data";

    private static final String INVALID_DEVICE_TYPE = "Target IP not found in discovery data";

    private static final String INVALID_CREDENTIAL_FORMAT = "Invalid credentials format";

    private static final String DISCOVERY_SUCCESSFUL = "Discovery run successful.";

    private static final String DISCOVERY_FAIL = "Discovery failed for IP: ";

    private static final String PING_FAIL = "Ping Failed ";

    private static final String PROVISION_UPDATE_SUCCESSFUL = "Provision status updated successfully id: ";

    private static final String DATA_NOT_FOUND = "No data found for ProfileId: ";

    public Service(Vertx vertx)
    {
        this.vertx = vertx;
    }

    // Creates a new record in the database.
    // @param requestBody The JSON request body containing the data to insert.
    // @param context The RoutingContext containing the request and response.
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

    // Fetches a record by ID from the database.
    // @param id The ID of the record to fetch.
    // @param context The RoutingContext containing the request and response.
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

    // Updates a record in the database.
    // @param id The ID of the record to update.
    // @param requestBody The JSON request body containing the data to update.
    // @param context The RoutingContext containing the request and response.
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

            if (!Util.isValidRequest(requestBody, context))
            {
                return;
            }

            if (Constants.DATABASE_TABLE_DISCOVERY_PROFILE.equals(tableName) && (requestBody.containsKey(Constants.IP) || requestBody.containsKey(Constants.DATABASE_CREDENTIAL_PROFILE_ID)))
            {
                var fetchQuery = "SELECT ip, discovery_profile_name, credential_profile_id, status FROM " + tableName + " WHERE id = $1";

                vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS,
                        new JsonObject()
                                .put(Constants.QUERY, fetchQuery)
                                .put(Constants.PARAMS, new JsonArray().add(profileId)),
                        fetchResult ->
                        {
                            if (fetchResult.failed())
                            {
                                context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);

                                return;
                            }

                            var existingData = fetchResult.result().body();

                            if (existingData == null || existingData.getJsonArray(Constants.DATA).isEmpty())
                            {
                                context.response().setStatusCode(404).end(Constants.MESSAGE_NOT_FOUND);

                                return;
                            }

                            var existingRecord = existingData.getJsonArray(Constants.DATA).getJsonObject(0);

                            var existingIp = existingRecord.getString(Constants.IP);

                            var existingName = existingRecord.getString(Constants.DATABASE_DISCOVERY_PROFILE_NAME);

                            var existingCredentialProfileId = existingRecord.getValue(Constants.DATABASE_CREDENTIAL_PROFILE_ID);

                            var newIp = requestBody.getString(Constants.IP, existingIp);

                            var newName = requestBody.getString(Constants.DATABASE_CREDENTIAL_PROFILE_NAME, existingName);

                            var newCredentialProfileId = requestBody.getValue(Constants.DATABASE_CREDENTIAL_PROFILE_ID, existingCredentialProfileId);

                            if (!existingIp.equals(newIp) || !existingCredentialProfileId.equals(newCredentialProfileId))
                            {
                                requestBody.put(Constants.IP, newIp);

                                requestBody.put(Constants.DATABASE_CREDENTIAL_PROFILE_ID, newCredentialProfileId);

                                requestBody.put(Constants.STATUS, null);
                            }

                            if (!existingName.equals(newName) && requestBody.isEmpty())
                            {
                                logger.info("name not matched ");

                                requestBody.put(Constants.DATABASE_CREDENTIAL_PROFILE_NAME, newName);
                            }

                            if (requestBody.isEmpty())
                            {
                                context.response().setStatusCode(204).end();

                                return;
                            }

                            var request = new JsonObject()
                                    .put(Constants.TABLE_NAME, tableName)
                                    .put(Constants.OPERATION, Constants.UPDATE)
                                    .put(Constants.DATA, requestBody)
                                    .put(Constants.CONDITION, new JsonObject().put(Constants.ID, profileId));

                            executeQuery(context, request, 200);
                        });
            }
            else
            {
                var request = new JsonObject()
                        .put(Constants.TABLE_NAME, tableName)
                        .put(Constants.OPERATION, Constants.UPDATE)
                        .put(Constants.DATA, requestBody)
                        .put(Constants.CONDITION, new JsonObject().put(Constants.ID, profileId));

                executeQuery(context, request, 200);
            }
        }
        catch (NumberFormatException e)
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
        }
    }


    // Fetches all records from the database.
    // @param context The RoutingContext containing the request and response.
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

    // Deletes a record from the database.
    // @param id The ID of the record to delete.
    // @param context The RoutingContext containing the request and response.
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

            if (Constants.DATABASE_TABLE_CREDENTIAL_PROFILE.equals(tableName))
            {
                checkCredentialUsage(parsedId, context);
            }
            else
            {
                performDelete(parsedId, tableName, context);
            }
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

    // Checks if a credential profile is in use before deleting it.
    // @param credentialId The ID of the credential profile to check.
    // @param context The RoutingContext containing the request and response.
    private void checkCredentialUsage(long credentialId, RoutingContext context)
    {
        var query = "SELECT in_use_by FROM credential_profile WHERE id = $1";

        var request = new JsonObject()
                .put(Constants.QUERY, query)
                .put(Constants.PARAMS, new JsonArray().add(credentialId));

        vertx.eventBus().request(Constants.EVENTBUS_DATABASE_ADDRESS, request, reply ->
        {
            if (reply.succeeded())
            {
                var result = (JsonObject) reply.result().body();

                var data = result.getJsonArray(Constants.DATA);

                if (!data.isEmpty() && data.getJsonObject(0).getInteger(Constants.DATABASE_IN_USE_BY) > 0)
                {
                    context.response().setStatusCode(400).end(Constants.MESSAGE_CREDENTIAL_IN_USE);
                }
                else
                {
                    performDelete(credentialId, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE, context);
                }
            }
            else
            {
                context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
            }
        });
    }

    // Performs the delete operation on the database.
    // @param id The ID of the record to delete.
    // @param tableName The name of the table to delete from.
    private void performDelete(long id, String tableName, RoutingContext context)
    {
        var request = new JsonObject()
                .put(Constants.TABLE_NAME, tableName)
                .put(Constants.OPERATION, Constants.DELETE)
                .put(Constants.CONDITION, new JsonObject().put(Constants.ID, id));

        executeQuery(context, request, 204);
    }

    // Executes a query on the database.
    // @param context The RoutingContext containing the request and response.
    // @param request The JSON object containing the query details.
    // @param successStatusCode The HTTP status code to return on success.
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

    // Runs a discovery operation on a device.
    // @param discoveryProfileId The ID of the discovery profile to run.
    // @param context The RoutingContext containing the request and response.
    public void runDiscovery(String discoveryProfileId, RoutingContext context)
    {
        try
        {
            var profileId = Long.parseLong(discoveryProfileId);

            var queryForDiscovery = "SELECT dt.id, dt.ip, dt.discovery_profile_name, cp.id as credential_profile_id, cp.system_type, cp.credentials " +
                    "FROM discovery_test dt " +
                    "JOIN credential_profile cp ON dt.credential_profile_id = cp.id " +
                    "WHERE dt.id = $1";

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryForDiscovery).put(Constants.PARAMS, new JsonArray().add(profileId)), fetchResult ->
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

                        if (targetIp.contains("/"))
                        {
                            processCidrRangeDiscovery(targetIp, discovery, profileId, context);
                        }
                        else
                        {
                            processSingleIpDiscovery(targetIp, discovery, profileId, context);
                        }
                    });

        }
        catch (NumberFormatException e)
        {

            logger.error("Invalid discoveryProfileId: {}", discoveryProfileId);

            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
        }
    }

    private void processCidrRangeDiscovery(String cidrRange, JsonObject discovery, Long profileId, RoutingContext context) {
        try
        {
            var parts = cidrRange.split("/");

            var networkAddress = parts[0];

            var prefixLength = Integer.parseInt(parts[1]);

            if (prefixLength < 0 || prefixLength > 32)
            {
                context.response().setStatusCode(400).end("Invalid CIDR range format");

                return;
            }

            if (prefixLength == 24)
            {
                var baseIp = networkAddress.substring(0, networkAddress.lastIndexOf(".") + 1);

                var allIps = new ArrayList<String>();

                for (int i = 1; i <= 255; i++)
                {
                    allIps.add(baseIp + i);
                }

                logger.info("Starting discovery for IP range: {} with {} IPs", cidrRange, allIps.size());

                vertx.executeBlocking(promise ->
                {
                    var reachableIps = Util.fpingBatch(allIps);

                    promise.complete(reachableIps);

                }, pingResult ->
                {
                    if (pingResult.succeeded())
                    {
                        var reachableIps = (Map<Integer, String>) pingResult.result();

                        if (reachableIps.isEmpty()) 
                        {
                            logger.error("No reachable IPs found in range: {}", cidrRange);

                            context.response().setStatusCode(400).end("No reachable IPs found in the specified range");

                            return;
                        }

                        logger.info("Found {} reachable IPs in range {}", reachableIps.size(), cidrRange);

                        var deviceType = discovery.getString(Constants.SYSTEM_TYPE);

                        if (!deviceType.equalsIgnoreCase(Constants.SNMP))
                        {
                            context.response().setStatusCode(400).end(INVALID_DEVICE_TYPE);

                            return;
                        }

                        var credentials = discovery.getJsonObject(Constants.CREDENTIALS);

                        runBatchSnmpDiscovery(reachableIps, credentials, deviceType, profileId, context);

                    }
                    else
                    {
                        logger.error("Error during batch ping for range: {}", cidrRange);

                        context.response().setStatusCode(500).end("Error during IP range discovery");
                    }
                });

            }
            else
            {
                context.response().setStatusCode(400).end("Only /24 CIDR ranges are supported");
            }
        }
        catch (Exception e)
        {
            logger.error("Error processing CIDR range: {}", cidrRange, e);

            context.response().setStatusCode(500).end("Error processing IP range");
        }
    }

    private void runBatchSnmpDiscovery(Map<Integer, String> reachableIps, JsonObject credentials, String deviceType, Long profileId, RoutingContext context) {

        var pingFutures = new ArrayList<Future>();

        var pingSuccessIps = new HashMap<Integer,String>();

        for (Map.Entry<Integer, String> entry : reachableIps.entrySet()) {

            var number = entry.getKey();

            var ip = entry.getValue();

            Promise<Boolean> pingPromise = Promise.promise();

            pingFutures.add(pingPromise.future().compose(isReachable -> {

                if (isReachable) {

                    pingSuccessIps.put(number, ip);

                    return Future.succeededFuture(true);

                } else {

                    logger.info("Ping failed for IP: {}", ip);

                    return Future.succeededFuture(false);

                }

            }));

            vertx.executeBlocking(promise -> {

                boolean isReachable = Util.ping(ip);

                promise.complete(isReachable);

            }, res -> {

                pingPromise.complete(res.succeeded() && (Boolean) res.result());

            });

        }

        CompositeFuture.all(pingFutures).onComplete(pingResults -> {

            if (pingSuccessIps.isEmpty()) {

                logger.warn("No reachable IPs in the provided range");

                context.response().setStatusCode(400).end(Constants.MESSAGE_NO_REACHABLE_DEVICE_IN_RANGE);

                return;

            }

            var zmqFutures = new ArrayList<Future>();

            var successfulDiscoveries = new HashMap<Integer,JsonObject>();

            for (Map.Entry<Integer, String> entry : pingSuccessIps.entrySet()) {

                var number = entry.getKey();

                var ip = entry.getValue();

                var zmqRequest = new JsonObject()
                        .put(Constants.IP, ip)
                        .put(Constants.COMMUNITY, credentials.getString(Constants.COMMUNITY))
                        .put(Constants.VERSION, credentials.getString(Constants.VERSION))
                        .put(Constants.PLUGIN_TYPE, deviceType)
                        .put(Constants.REQUEST_TYPE, Constants.DISCOVERY);

                Promise<JsonObject> zmqPromise = Promise.promise();

                zmqFutures.add(zmqPromise.future().compose(result -> {

                    var response = result.put(Constants.NUMBER, number).put(Constants.ID, ip);

                    if (Constants.SUCCESS.equalsIgnoreCase(response.getString(Constants.STATUS))) {

                        successfulDiscoveries.put(number, response);

                        logger.info("Successfully discovered device at IP: {}", ip);

                    } else {

                        logger.info("Failed to discover device at IP: {}", ip);

                    }

                    return Future.succeededFuture(response);

                }));


                vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_ZMQ_ADDRESS, zmqRequest, ar -> {

                    if (ar.succeeded()) {

                        zmqPromise.complete(ar.result().body());

                    } else {

                        zmqPromise.complete(new JsonObject()
                                .put(Constants.STATUS, Constants.FAIL)
                                .put(Constants.IP, ip));

                    }

                });

            }

            CompositeFuture.all(zmqFutures).onComplete(zmqResults -> {

                if (successfulDiscoveries.isEmpty()) {

                    logger.warn("No successful SNMP discoveries in the reachable IPs");

                    context.response().setStatusCode(400).end(Constants.MESSAGE_NO_SNMP_DEVICE_IN_RANGE);

                    return;

                }

                var statusJson = new JsonObject();

                for (Map.Entry<Integer, JsonObject> entry : successfulDiscoveries.entrySet()) {

                    statusJson.put(entry.getKey().toString(), entry.getValue().getString(Constants.IP));

                }

                var updateStatusQuery = "UPDATE discovery_test " +
                        "SET status = $1::jsonb " +
                        "WHERE id = $2";

                vertx.eventBus().<JsonObject>request(
                        Constants.EVENTBUS_DATABASE_ADDRESS,
                        new JsonObject()
                                .put(Constants.QUERY, updateStatusQuery)
                                .put(Constants.PARAMS, new JsonArray().add(statusJson).add(profileId)),
                        updateResult -> {

                            if (updateResult.succeeded()) {

                                context.response()
                                        .setStatusCode(200)
                                        .end(new JsonObject()
                                                .put(Constants.MESSAGE, Constants.SUCCESS)
                                                .put(Constants.DISCOVERED_IPS, statusJson)
                                                .encode());

                            } else {

                                logger.error("Failed to update discovery status {}", profileId);

                                context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);

                            }

                        });

            });

        });

    }

    private void processSingleIpDiscovery(String targetIp, JsonObject discovery, Long profileId, RoutingContext context) {

        vertx.executeBlocking(promise -> {

            boolean isReachable = Util.ping(targetIp);

            promise.complete(isReachable);

        }, pingRes -> {

            if (pingRes.succeeded() && (Boolean) pingRes.result()) {

                var deviceType = discovery.getString(Constants.SYSTEM_TYPE);

                if (!deviceType.equalsIgnoreCase(Constants.SNMP)) {

                    context.response().setStatusCode(400).end(INVALID_DEVICE_TYPE);

                    return;

                }

                var credentials = discovery.getJsonObject(Constants.CREDENTIALS);

                if (credentials == null) {

                    context.response().setStatusCode(500).end(INVALID_CREDENTIAL_FORMAT);

                    return;

                }

                var zmqRequest = new JsonObject()
                        .put(Constants.IP, targetIp)
                        .put(Constants.COMMUNITY, credentials.getString(Constants.COMMUNITY))
                        .put(Constants.VERSION, credentials.getString(Constants.VERSION))
                        .put(Constants.PLUGIN_TYPE, deviceType)
                        .put(Constants.REQUEST_TYPE, Constants.DISCOVERY);


                vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_ZMQ_ADDRESS, zmqRequest, zmqResult -> {

                    if (zmqResult.failed()) {

                        logger.info(Constants.MESSAGE_ZMQ_NO_RESPONSE);

                        context.response().setStatusCode(504).end(Constants.MESSAGE_ZMQ_NO_RESPONSE);

                        return;

                    }

                    var zmqResponseJson = zmqResult.result().body();

                    logger.info("ZMQ Response: {}", zmqResponseJson);

                    boolean isSuccess = Constants.SUCCESS.equalsIgnoreCase(zmqResponseJson.getString(Constants.STATUS));

                    if (!isSuccess) {

                        context.response().setStatusCode(500).end(DISCOVERY_FAIL + targetIp);

                        return;

                    }

                    var statusJson = new JsonObject().put("1", targetIp);

                    var updateStatusQuery = "UPDATE discovery_test " +
                            "SET status = $1::jsonb " +
                            "WHERE id = $2";

                    vertx.eventBus().<JsonObject>request(
                            Constants.EVENTBUS_DATABASE_ADDRESS,
                            new JsonObject()
                                    .put(Constants.QUERY, updateStatusQuery)
                                    .put(Constants.PARAMS, new JsonArray().add(statusJson.encode()).add(profileId)),
                            updateResult -> {

                                if (updateResult.succeeded()) {

                                    context.response()
                                            .setStatusCode(200)
                                            .end(new JsonObject()
                                                    .put(Constants.MESSAGE, DISCOVERY_SUCCESSFUL)
                                                    .put(Constants.DISCOVERED_IPS, statusJson)
                                                    .encode());

                                } else {

                                    logger.error("Failed to update discovery status {}", profileId);

                                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);

                                }

                            });
                });

            } else {

                logger.error("Ping failed for IP: {}", targetIp);

                context.response().setStatusCode(400).end(PING_FAIL);

            }
        });
    }

    // Updates the provision status for a discovery profile.
    // @param discoveryProfileId The ID of the discovery profile to update.
    // @param context The RoutingContext containing the request and response.
    public void updateProvisionStatus(String discoveryProfileId, RoutingContext context)
    {
        try
        {
            var profileId = Long.parseLong(discoveryProfileId);

            var requestBody = context.getBodyAsJson();

            var numbersArray = requestBody.getJsonArray(Constants.NUMBERS);

            if (numbersArray == null || numbersArray.isEmpty())
            {
                context.response().setStatusCode(400).end("Numbers array is required");

                return;
            }

            var successResults = new JsonArray();

            var failedResults = new JsonArray();

            var query = "SELECT id, status FROM discovery_test WHERE id = " + profileId;

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, query), checkReply ->
                    {
                        if (checkReply.succeeded())
                        {
                            var result = checkReply.result().body();

                            if (result == null || result.getJsonArray(Constants.DATA).isEmpty())
                            {
                                context.response().setStatusCode(404).end(Constants.MESSAGE_NOT_FOUND);

                                return;
                            }

                            var data = result.getJsonArray(Constants.DATA).getJsonObject(0);

                            var statusJson = data.getJsonObject(Constants.STATUS);

                            if (statusJson == null)
                            {
                                context.response().setStatusCode(400).end("Status information not available");

                                return;
                            }

                            processNumbers(profileId, numbersArray, statusJson, successResults, failedResults, context);
                        }
                        else
                        {
                            logger.error("Failed to fetch discovery test: {}", checkReply.cause().getMessage());

                            context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
                        }
                    });
        }
        catch (NumberFormatException e)
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
        }
    }

    // Processes a list of numbers, checks their status, and starts provisioning if valid.
    // Updates success and failure results accordingly and sends a final response when all operations complete.
    // @param profileId       The ID of the discovery profile to fetch credentials for.
    // @param numbersArray    The list of numbers to process.
    // @param statusJson      A mapping of numbers to their associated IP addresses.
    // @param successResults  The list to store successful processing results.
    // @param failedResults   The list to store failed processing results with error messages.
    // @param context         The Vert.x RoutingContext for handling the request.
    private void processNumbers(Long profileId, JsonArray numbersArray, JsonObject statusJson, JsonArray successResults, JsonArray failedResults, RoutingContext context) {

        var pendingOperations = new AtomicInteger(numbersArray.size());

        for (int i = 0; i < numbersArray.size(); i++)
        {
            var number = String.valueOf(numbersArray.getValue(i));

            if (!statusJson.containsKey(number))
            {
                var errorResult = new JsonObject()
                        .put(Constants.NUMBER, number)
                        .put(Constants.ERROR, "Number not found");
                failedResults.add(errorResult);

                if (pendingOperations.decrementAndGet() == 0)
                {
                    sendFinalResponse(successResults, failedResults, context);
                }

                continue;
            }

            var ip = statusJson.getString(number);

            if (ip == null || ip.isEmpty())
            {
                JsonObject errorResult = new JsonObject().put(Constants.NUMBER, number).put(Constants.ERROR, "Invalid IP address");

                failedResults.add(errorResult);

                if (pendingOperations.decrementAndGet() == 0)
                {
                    sendFinalResponse(successResults, failedResults, context);
                }

                continue;
            }

            var credentialQuery = "SELECT credential_profile_id FROM discovery_test WHERE id = " + profileId;

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, credentialQuery),
                    credReply ->
                    {
                        if (credReply.succeeded())
                        {
                            var credResult = credReply.result().body();

                            if (credResult == null || credResult.getJsonArray(Constants.DATA).isEmpty())
                            {
                                JsonObject errorResult = new JsonObject()
                                        .put(Constants.NUMBER, number)
                                        .put(Constants.ERROR, "Credential profile not found");

                                failedResults.add(errorResult);

                                if (pendingOperations.decrementAndGet() == 0)
                                {
                                    sendFinalResponse(successResults, failedResults, context);
                                }

                                return;
                            }

                            var credData = credResult.getJsonArray(Constants.DATA).getJsonObject(0);

                            var credentialProfileId = credData.getLong(Constants.DATABASE_CREDENTIAL_PROFILE_ID);

                            if (credentialProfileId == null)
                            {
                                var errorResult = new JsonObject().put(Constants.NUMBER, number).put(Constants.ERROR, Constants.MESSAGE_NULL_CREDENTIAL_ID);

                                failedResults.add(errorResult);

                                if (pendingOperations.decrementAndGet() == 0)
                                {
                                    sendFinalResponse(successResults, failedResults, context);
                                }

                                return;
                            }

                            startProvisioning( number, ip, credentialProfileId, successResults, failedResults, pendingOperations, context);
                        }
                        else
                        {
                            var errorResult = new JsonObject().put(Constants.NUMBER, number).put(Constants.ERROR, "Failed to fetch credential profile");

                            failedResults.add(errorResult);

                            if (pendingOperations.decrementAndGet() == 0)
                            {
                                sendFinalResponse(successResults, failedResults, context);
                            }
                        }
                    });
        }
    }

    // Initiates the provisioning process by inserting a new provisioning job into the database.
    // Updates the credential profile usage count and handles success or failure responses.
    // @param number             The number being provisioned.
    // @param ip                 The IP address associated with the provisioning job.
    // @param credentialProfileId The credential profile ID used for provisioning.
    // @param successResults     The list to store successful provisioning results.
    // @param failedResults      The list to store failed provisioning results with error messages.
    // @param pendingOperations  The counter tracking the number of pending provisioning operations.
    // @param context            The Vert.x RoutingContext for handling the request.
    private void startProvisioning( String number, String ip, Long credentialProfileId, JsonArray successResults, JsonArray failedResults, AtomicInteger pendingOperations, RoutingContext context)
    {

        var insertRequest = new JsonObject()
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISIONING_JOBS)
                .put(Constants.OPERATION, Constants.INSERT)
                .put(Constants.DATA, new JsonObject()
                        .put(Constants.DATABASE_CREDENTIAL_PROFILE_ID, credentialProfileId)
                        .put(Constants.IP, ip));

        var queryResult = QueryBuilder.buildQuery(insertRequest);

        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS,
                new JsonObject().put(Constants.QUERY, queryResult.getQuery()).put(Constants.PARAMS, queryResult.getParams()),
                insertReply ->
                {
                    if (insertReply.succeeded())
                    {
                        var insertResult = insertReply.result().body();

                        var insertedId = insertResult.getLong(Constants.ID);

                        if (insertedId == null)
                        {
                            var errorResult = new JsonObject().put(Constants.NUMBER, number).put(Constants.ERROR, "Failed to insert provisioning job");

                            failedResults.add(errorResult);

                            if (pendingOperations.decrementAndGet() == 0)
                            {
                                sendFinalResponse(successResults, failedResults, context);
                            }

                            return;
                        }

                        var updateQuery = "UPDATE credential_profile SET in_use_by = in_use_by + 1 WHERE id = " + credentialProfileId;

                        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, updateQuery),
                                updateReply ->
                                {
                                    if (updateReply.succeeded())
                                    {
                                        var successResult = new JsonObject()
                                                .put(Constants.NUMBER, number)
                                                .put(Constants.ID, insertedId)
                                                .put(Constants.STATUS, Constants.SUCCESS);

                                        successResults.add(successResult);

                                    }
                                    else
                                    {
                                        logger.error("Failed to update in_use_by for number {}: {}", number, updateReply.cause().getMessage());

                                        var successResult = new JsonObject()
                                                .put(Constants.NUMBER, number)
                                                .put(Constants.ID, insertedId)
                                                .put(Constants.STATUS, Constants.SUCCESS)
                                                .put("warning", "Provisioning successful, but failed to update credential profile usage count");
                                        successResults.add(successResult);
                                    }

                                    if (pendingOperations.decrementAndGet() == 0)
                                    {
                                        sendFinalResponse(successResults, failedResults, context);
                                    }
                                });
                    }
                    else
                    {
                        logger.error("Provisioning insert failed for number {}: {}", number, insertReply.cause().getMessage());

                        var errorResult = new JsonObject()
                                .put(Constants.NUMBER, number)
                                .put(Constants.ERROR, "Failed to insert provisioning job");

                        failedResults.add(errorResult);

                        if (pendingOperations.decrementAndGet() == 0)
                        {
                            sendFinalResponse(successResults, failedResults, context);
                        }
                    }
                });
    }

    // Sends the final response after processing all provisioning operations.
    // Includes the total count, success count, failure count, and details of successful and failed numbers
    // @param successResults The list of successfully processed numbers.
    // @param failedResults  The list of numbers that failed to process with error details.
    // @param context        The Vert.x RoutingContext used to send the response.
    private void sendFinalResponse(JsonArray successResults, JsonArray failedResults, RoutingContext context) {
        var response = new JsonObject()
                .put(Constants.STATUS, successResults.isEmpty() ? Constants.ERROR : Constants.SUCCESS)
                .put("total", successResults.size() + failedResults.size())
                .put(Constants.SUCCESS, successResults.size())
                .put(Constants.FAIL, failedResults.size())
                .put("successfulNumbers", successResults)
                .put("failedNumbers", failedResults);

        context.response().setStatusCode(200).end(response.encode());
    }

    // Fetches provision data for a given job ID.
    // @param jobId The ID of the job to fetch data for.
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
                            .put(Constants.DATABASE_JOB_ID, jobIdLong));

            var queryResult = QueryBuilder.buildQuery(request);

            var fetchRequest = new JsonObject()
                    .put(Constants.QUERY, queryResult.getQuery() + " ORDER BY polled_at DESC")
                    .put(Constants.PARAMS, queryResult.getParams());

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, fetchRequest, reply ->
            {
                if (reply.succeeded()) {
                    var resultObject = reply.result().body();

                    if (resultObject == null) {
                        context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);

                        return;
                    }

                    var results = resultObject.getJsonArray(Constants.DATA);

                    if (results == null || results.isEmpty()) {
                        context.response().setStatusCode(404).end(new JsonObject().put(Constants.MESSAGE, DATA_NOT_FOUND + jobId).encode());

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

    // Fetches the top 10 devices with the most errors in the latest polling.
    // @param context The RoutingContext containing the request and response.
    public void getInterfacesByError(RoutingContext context) {

        var query = """
                WITH latest_polling AS (
                    SELECT
                        pj.ip,
                        interface->>'interface.name' AS interface_name,
                        COALESCE((interface->>'interface.sent.error.packets')::INT, 0) +
                        COALESCE((interface->>'interface.received.error.packets')::INT, 0) AS total_errors
                    FROM provisioning_jobs pj
                    JOIN LATERAL (
                        SELECT pd.data AS interfaces
                        FROM provision_data pd
                        WHERE pd.job_id = pj.id
                        AND pd.data->'interfaces' IS NOT NULL  -- Ensure 'interfaces' exists
                        ORDER BY pd.polled_at DESC
                        LIMIT 1
                    ) latest_pd ON true
                    CROSS JOIN jsonb_array_elements(latest_pd.interfaces->'interfaces') AS interface
                )
                SELECT ip, interface_name, total_errors, rank
                FROM (
                    SELECT
                        ip,
                        interface_name,
                        total_errors,
                        DENSE_RANK() OVER (ORDER BY total_errors DESC) AS rank
                    FROM latest_polling
                    WHERE total_errors > 0
                ) ranked
                WHERE rank <= 10;
                """;

        executeAndRespond(context, query);
    }

    // Fetches the top 10 devices with the most speed in latest polling
    // @param context The RoutingContext containing the request and response.
    public void getInterfacesBySpeed(RoutingContext context) {

        var query = """
                WITH latest_polling AS (
                    SELECT
                        pj.ip,
                        interface->>'interface.name' AS interface_name,
                        (interface->>'interface.speed')::BIGINT AS speed
                    FROM provisioning_jobs pj
                    JOIN LATERAL (
                        SELECT pd.data->'interfaces' AS interfaces
                        FROM provision_data pd
                        WHERE pd.job_id = pj.id
                        AND pd.data->'interfaces' IS NOT NULL  -- Ensure 'interfaces' field exists
                        ORDER BY pd.polled_at DESC
                        LIMIT 1
                    ) latest_pd ON true
                    CROSS JOIN jsonb_array_elements(latest_pd.interfaces) AS interface
                    WHERE (interface->>'interface.speed') IS NOT NULL
                    AND (interface->>'interface.speed') ~ '^[0-9]+$'  -- Ensure speed is a valid number
                    AND (interface->>'interface.speed')::BIGINT > 0  -- Ignore speed = 0
                )
                SELECT ip, interface_name, speed, rank
                FROM (
                    SELECT
                        ip,
                        interface_name,
                        speed,
                        DENSE_RANK() OVER (ORDER BY speed DESC) AS rank
                    FROM latest_polling
                ) ranked
                WHERE rank <= 10;
        """;

        executeAndRespond(context, query);
    }

    // Fetches the top 10 devices with the most reboots in the last 7 days.
    // @param context The RoutingContext containing the request and response.
    public void getInterfacesByUptime(RoutingContext context)
    {
        var query = """
                WITH parsed_data AS (
                    SELECT
                        pj.ip,
                        pd.job_id,
                        pd.polled_at,
                        (pd.data->>'system.uptime') AS raw_uptime,
                        (
                            COALESCE((regexp_match(pd.data->>'system.uptime', '(\\d+) days'))[1]::INT, 0) * 86400 +
                            COALESCE((regexp_match(pd.data->>'system.uptime', '(\\d+) hours'))[1]::INT, 0) * 3600 +
                            COALESCE((regexp_match(pd.data->>'system.uptime', '(\\d+) minutes'))[1]::INT, 0) * 60 +
                            COALESCE((regexp_match(pd.data->>'system.uptime', '(\\d+) seconds'))[1]::INT, 0)
                        ) AS uptime_seconds
                    FROM provision_data pd
                    JOIN provisioning_jobs pj ON pd.job_id = pj.id
                    WHERE pd.data ? 'system.uptime'
                    AND pd.polled_at >= NOW() - INTERVAL '7 days'
                ),
                lagged_data AS (
                    SELECT
                        ip,
                        job_id,
                        polled_at,
                        uptime_seconds,
                        LAG(uptime_seconds) OVER (PARTITION BY job_id ORDER BY polled_at) AS prev_uptime
                    FROM parsed_data
                ),
                reboot_counts AS (
                    SELECT
                        ip,
                        job_id,
                        COUNT(*) AS reboot_count
                    FROM lagged_data
                    WHERE uptime_seconds < prev_uptime
                    GROUP BY ip, job_id
                )
                SELECT ip, job_id, reboot_count, reboot_rank
                FROM (
                    SELECT
                        ip,
                        job_id,
                        reboot_count,
                        DENSE_RANK() OVER (ORDER BY reboot_count DESC) AS reboot_rank
                    FROM reboot_counts
                ) ranked
                WHERE reboot_rank <= 10
                ORDER BY reboot_rank ASC;
            """;

        executeAndRespond(context, query);
    }

    // Fetches all devices from provisioning jobs
    public void getDevices(RoutingContext context)
    {
        var query = """ 
                SELECT * FROM provisioning_jobs;
                """;

        executeAndRespond(context, query);
    }

    // Executes a query and responds with the result.
    // @param context The RoutingContext containing the request and response.
    // @param query The query to execute
    private void executeAndRespond(RoutingContext context, String query)
    {
        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS,
                new JsonObject().put(Constants.QUERY, query).put(Constants.PARAMS,new JsonArray()), reply ->
                {
                    if (reply.succeeded())
                    {
                        context.response().setStatusCode(200).end(reply.result().body().toString());
                    }
                    else
                    {
                        logger.error("Database operation failed: {}", reply.cause().getMessage());

                        context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR + reply.cause().getMessage());
                    }
                });
    }

    // Deletes a provisioning job and updates the credential profile usage count.
    // @param jobId The ID of the job to delete.
    // @param context The RoutingContext containing the request and response.
    public void deleteProvisioningJob(String jobId, RoutingContext context)
    {
        if (jobId == null || jobId.trim().isEmpty()) {
            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
            return;
        }

        try {
            var parsedId = Long.parseLong(jobId);

            var fetchQuery = "SELECT credential_profile_id FROM " + Constants.DATABASE_TABLE_PROVISIONING_JOBS +
                    " WHERE id = " + parsedId;

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, fetchQuery), fetchReply ->
            {
                        if (fetchReply.succeeded())
                        {
                            var result = fetchReply.result().body();

                            if (result == null || result.getJsonArray(Constants.DATA).isEmpty())
                            {
                                context.response().setStatusCode(404).end(Constants.MESSAGE_JOB_NOT_FOUND);

                                return;
                            }

                            var credentialProfileId = result.getJsonArray(Constants.DATA).getJsonObject(0).getLong(Constants.DATABASE_CREDENTIAL_PROFILE_ID);

                            var deleteRequest = new JsonObject()
                                    .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISIONING_JOBS)
                                    .put(Constants.OPERATION, Constants.DELETE)
                                    .put(Constants.CONDITION, new JsonObject().put(Constants.ID, parsedId));

                            var deleteQuery = QueryBuilder.buildQuery(deleteRequest);

                            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, deleteQuery.getQuery()).put(Constants.PARAMS, deleteQuery.getParams()), deleteReply ->
                            {
                                        if (deleteReply.succeeded())
                                        {
                                            if (credentialProfileId != null)
                                            {
                                                var updateQuery = "UPDATE credential_profile SET in_use_by = in_use_by - 1 WHERE id = " + credentialProfileId;

                                                vertx.eventBus().send(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, updateQuery));
                                            }

                                            context.response().setStatusCode(204).end();

                                        }
                                        else
                                        {
                                            context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
                                        }
                                    });
                        }
                        else
                        {
                            context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
                        }
                    });
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
}