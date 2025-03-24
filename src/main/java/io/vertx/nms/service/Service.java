package io.vertx.nms.service;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.nms.util.Constants;
import io.vertx.nms.database.QueryBuilder;
import io.vertx.nms.util.Util;
import org.checkerframework.checker.units.qual.C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        var request = new JsonObject().put(Constants.TABLE_NAME, Util.getTableNameFromContext(context)).put(Constants.OPERATION, Constants.INSERT).put(Constants.DATA, requestBody);

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

            var request = new JsonObject().put(Constants.TABLE_NAME, tableName).put(Constants.OPERATION, Constants.SELECT).put(Constants.COLUMNS, new JsonArray().add(Constants.DATABASE_ALL_COLUMN)).put(Constants.CONDITION, new JsonObject().put(Constants.ID, parsedId));

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
                var queryRequest = new JsonObject().put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_DISCOVERY_PROFILE).put(Constants.OPERATION, Constants.SELECT).put(Constants.COLUMNS, new JsonArray().add(Constants.DATABASE_ALL_COLUMN)).put(Constants.CONDITION, new JsonObject().put(Constants.ID, profileId));

                var queryResult = QueryBuilder.buildQuery(queryRequest);

                vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.query()).put(Constants.PARAMS, queryResult.params()), fetchResult ->
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

                        requestBody.put(Constants.STATUS, false);
                    }

                    if (!existingName.equals(newName) && requestBody.isEmpty())
                    {
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
        var queryRequest = new JsonObject()
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                .put(Constants.OPERATION, Constants.SELECT)
                .put(Constants.COLUMNS, new JsonArray().add(Constants.DATABASE_IN_USE_BY)).put(Constants.CONDITION, new JsonObject().put(Constants.ID, credentialId));

        var queryResult = QueryBuilder.buildQuery(queryRequest);

        var request = new JsonObject()
                .put(Constants.QUERY, queryResult.query())
                .put(Constants.PARAMS, queryResult.params());

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

        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.query()).put(Constants.PARAMS, queryResult.params()), reply ->
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

            var queryForDiscovery = "SELECT dp.id, dp.ip, dp.discovery_profile_name, cp.id as credential_profile_id, cp.system_type, cp.credentials " +
                    "FROM discovery_profiles dp " +
                    "JOIN credential_profile cp ON dp.credential_profile_id = cp.id " +
                    "WHERE dp.id = $1";

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

                        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_ZMQ_ADDRESS, zmqRequest,new DeliveryOptions().setSendTimeout(280_000), zmqResult ->
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
                                context.response().setStatusCode(400).end(new JsonObject().put(Constants.STATUS,Constants.FAIL).put(Constants.MESSAGE,DISCOVERY_FAIL + targetIp).encode());

                                return;
                            }

                            var updateStatusQuery = "UPDATE discovery_profiles " +
                                    "SET status = true " +
                                    "WHERE id = $2";

                            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, updateStatusQuery).put(Constants.PARAMS, new JsonArray().add(targetIp).add(profileId)), updateResult ->
                            {
                                if (updateResult.succeeded())
                                {
                                    context.response().setStatusCode(200).end(new JsonObject().put(Constants.STATUS,Constants.SUCCESS).put(Constants.MESSAGE,DISCOVERY_SUCCESSFUL).encode());
                                }
                                else
                                {
                                    logger.error("Failed to update discovery status {}", discoveryProfileId);

                                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
                                }
                            });
                        });
                    }
                    else
                    {
                        logger.error("Ping failed for IP: {}", targetIp);

                        context.response().setStatusCode(400).end(new JsonObject().put(Constants.STATUS,Constants.FAIL).put(Constants.MESSAGE,PING_FAIL).encode());
                    }
                });
            });
        }
        catch (NumberFormatException e)
        {

            logger.error("Invalid discoveryProfileId: {}", discoveryProfileId);

            context.response().setStatusCode(400).end(new JsonObject().put(Constants.STATUS,Constants.FAIL).put(Constants.MESSAGE,Constants.MESSAGE_INVALID_PROFILE_ID).encode());
        }
    }

    // Updates the provision status for a discovery profile.
    // @param discoveryProfileId The ID of the discovery profile to update.
    // @param context The RoutingContext containing the request and response.
    public void updateProvisionStatus(String discoveryProfileId, RoutingContext context)
    {
        try
        {
            var profileId = Long.parseLong(discoveryProfileId);

            var queryRequest = new JsonObject().put(Constants.TABLE_NAME,Constants.DATABASE_TABLE_DISCOVERY_PROFILE).put(Constants.OPERATION,Constants.SELECT).put(Constants.COLUMNS,new JsonArray().add(Constants.DATABASE_ALL_COLUMN)).put(Constants.CONDITION,new JsonObject().put(Constants.ID,profileId));

            var queryResult = QueryBuilder.buildQuery(queryRequest);

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.query()).put(Constants.PARAMS,queryResult.params()), checkReply ->
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

                    var status = data.getBoolean(Constants.STATUS);

                    var ip = data.getString(Constants.IP);

                    var credentialProfileId = data.getLong(Constants.DATABASE_CREDENTIAL_PROFILE_ID);

                    if (status == null || !status)
                    {
                        context.response().setStatusCode(400).end(Constants.MESSAGE_IP_NOT_DISCOVERED);

                        return;
                    }

                    if (credentialProfileId == null)
                    {
                        context.response().setStatusCode(400).end(Constants.MESSAGE_NULL_CREDENTIAL_ID);

                        return;
                    }

                    var insertRequest = new JsonObject()
                            .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISIONING_JOBS)
                            .put(Constants.OPERATION, Constants.INSERT)
                            .put(Constants.DATA, new JsonObject()
                                    .put(Constants.DATABASE_CREDENTIAL_PROFILE_ID, credentialProfileId)
                                    .put(Constants.IP, ip));

                    var insertQueryResult = QueryBuilder.buildQuery(insertRequest);

                    vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, insertQueryResult.query()).put(Constants.PARAMS, insertQueryResult.params()), insertReply ->
                    {
                        if (insertReply.succeeded())
                        {

                            var insertResult = insertReply.result().body();

                            var insertedId = insertResult.getLong(Constants.ID);

                            if (insertedId == null)
                            {

                                context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);

                                return;
                            }

                            var updateQuery = "UPDATE credential_profile SET in_use_by = in_use_by + 1 WHERE id = " + credentialProfileId;

                            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, updateQuery), updateReply ->
                            {
                                if (updateReply.succeeded())
                                {
                                    context.response().setStatusCode(200).end(new JsonObject().put(Constants.STATUS,Constants.SUCCESS).put(Constants.MESSAGE,PROVISION_UPDATE_SUCCESSFUL).put(Constants.ID,insertedId).encode());
                                }
                                else
                                {
                                    logger.error("Failed to update in_use_by: {}", updateReply.cause().getMessage());

                                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
                                }
                            });

                        }
                        else
                        {
                            logger.error("Provisioning insert failed: {}", insertReply.cause().getMessage());

                            context.response().setStatusCode(400).end(Constants.MESSAGE_POLLING_STARTED);
                        }
                    });

                }
                else
                {
                    logger.error("Failed to fetch discovery profile: {}", checkReply.cause().getMessage());

                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
                }
            });
        }
        catch (NumberFormatException e)
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
        }
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

            var fetchRequest = new JsonObject().put(Constants.QUERY, queryResult.query() + " ORDER BY polled_at DESC").put(Constants.PARAMS, queryResult.params());

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
                        context.response().setStatusCode(404).end(new JsonObject().put(Constants.MESSAGE, DATA_NOT_FOUND + jobId).encode());

                        return;
                    }

                    var responseArray = new JsonArray();

                    results.forEach(row ->
                    {
                        var rowObj = (JsonObject) row;

                        responseArray.add(new JsonObject().put(Constants.DATA, rowObj.getJsonObject(Constants.DATA)).put(Constants.POLLED_AT, rowObj.getString(Constants.POLLED_AT)));
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
    public void getInterfacesByError(RoutingContext context)
    {
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
    public void getInterfacesBySpeed(RoutingContext context)
    {

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
        var queryRequest = new JsonObject().put(Constants.TABLE_NAME,Constants.DATABASE_TABLE_PROVISIONING_JOBS).put(Constants.OPERATION,Constants.SELECT).put(Constants.COLUMNS,new JsonArray().add(Constants.DATABASE_ALL_COLUMN));

        var queryResult = QueryBuilder.buildQuery(queryRequest);

        executeAndRespond(context, queryResult.query());
    }

    // Executes a query and responds with the result.
    // @param context The RoutingContext containing the request and response.
    // @param query The query to execute
    private void executeAndRespond(RoutingContext context, String query)
    {
        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, query).put(Constants.PARAMS,new JsonArray()), reply ->
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
        if (jobId == null || jobId.trim().isEmpty())
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);

            return;
        }

        try
        {
            var parsedId = Long.parseLong(jobId);

            var fetchQueryRequest = (new JsonObject().put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISIONING_JOBS).put(Constants.OPERATION, Constants.SELECT).put(Constants.COLUMNS, new JsonArray().add(Constants.DATABASE_CREDENTIAL_PROFILE_ID)).put(Constants.CONDITION,new JsonObject().put(Constants.ID, parsedId)));

            var queryResult = QueryBuilder.buildQuery(fetchQueryRequest);

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, queryResult.query()).put(Constants.PARAMS,queryResult.params()), fetchReply ->
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

                    vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, deleteQuery.query()).put(Constants.PARAMS, deleteQuery.params()), deleteReply ->
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