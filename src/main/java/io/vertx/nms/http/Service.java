package io.vertx.nms.http;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.nms.util.Constants;
import io.vertx.nms.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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

    private static final String DUPLICATE_ERROR = "Duplicate entry: Profile already exists.";

    private static final String FOREIGN_KEY_ERROR = "No credential profile found ";

    private static final String INVALID_VALUE_ERROR = "Some key contains invalid values in request";

    private static final String DUPLICATE_ERROR_CODE = "23505";

    private static final String FOREIGN_KEY_ERROR_CODE = "23503";

    private static final String MESSAGE_CREDENTIAL_UPDATE_FAILED = "Failed to update credential Profile Usage count";

    private static final String CLASS_CAST_ERROR = "can not be coerced to the expected class";

    private final StringBuilder reusableQueryBuilder = new StringBuilder();

    private final JsonObject reusableQueryRequest = new JsonObject();

    private final JsonObject reusableCondition = new JsonObject();

    private final JsonArray reusableColumn = new JsonArray();

    private final JsonObject reusableRequest = new JsonObject();

    private final JsonObject apiResponse = new JsonObject();

    private static final DeliveryOptions DELIVERY_OPTIONS = new DeliveryOptions().setSendTimeout(280_000);

    private final java.text.SimpleDateFormat DATE_FORMATTER = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final String QUERY_FOR_DISCOVERY = "SELECT dp.id, dp.ip, dp.port, cp.id as credential_profile_id, cp.system_type, cp.credentials " +
            "FROM discovery_profiles dp " +
            "JOIN credential_profile cp ON dp.credential_profile_id = cp.id " +
            "WHERE dp.id = $1";

    public Service(Vertx vertx)
    {
        this.vertx = vertx;
    }

    // Creates a new record in the database.
    // @param requestBody The JSON request body containing the data to insert.
    // @param context The RoutingContext containing the request and response.
    public void create(JsonObject requestBody, RoutingContext context)
    {
        if (Util.isValidRequest(requestBody, context))
        {
            reusableQueryRequest.clear();

            executeQuery(context, reusableQueryRequest.put(Constants.TABLE_NAME, Util.getTableNameFromContext(context)).put(Constants.OPERATION, Constants.INSERT).put(Constants.DATA, requestBody), 201);
        }
    }

    // Fetches a record by ID from the database.
    // @param id The ID of the record to fetch.
    // @param context The RoutingContext containing the request and response.
    public void getById(String id, RoutingContext context)
    {
        var tableName = Util.getTableNameFromContext(context);

        try
        {
            reusableQueryRequest.clear();

            reusableCondition.clear();

            executeQuery(context, reusableQueryRequest.put(Constants.TABLE_NAME, tableName).put(Constants.OPERATION, Constants.SELECT).put(Constants.COLUMNS, reusableColumn.clear().add(Constants.DATABASE_ALL_COLUMN)).put(Constants.CONDITION, reusableCondition.put(Constants.ID, Long.parseLong(id))), 200);
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

        try
        {
            var profileId = Long.parseLong(id);

            if (Util.isValidRequest(requestBody, context))
            {
                if (Constants.DATABASE_TABLE_DISCOVERY_PROFILE.equals(tableName) && (requestBody.containsKey(Constants.IP) || requestBody.containsKey(Constants.DATABASE_CREDENTIAL_PROFILE_ID)))
                {
                    reusableQueryBuilder.setLength(0);

                    reusableQueryRequest.clear();

                    reusableCondition.clear();

                    reusableColumn.clear();

                    var queryResult = QueryBuilder.buildQuery(reusableQueryRequest.put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_DISCOVERY_PROFILE).put(Constants.OPERATION, Constants.SELECT).put(Constants.COLUMNS,reusableColumn.add(Constants.DATABASE_ALL_COLUMN)).put(Constants.CONDITION, reusableCondition.put(Constants.ID, profileId)), reusableQueryBuilder);

                    reusableRequest.clear();

                    vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableRequest.put(Constants.QUERY, queryResult.query()).put(Constants.PARAMS, queryResult.params()), fetchResult ->
                    {
                        if (!fetchResult.failed())
                        {
                            if (!(fetchResult.result().body() == null || fetchResult.result().body().getJsonArray(Constants.DATA).isEmpty()))
                            {
                                var existingRecord = fetchResult.result().body().getJsonArray(Constants.DATA).getJsonObject(0);

                                if (!existingRecord.getString(Constants.IP).equals(requestBody.getString(Constants.IP)) || !existingRecord.getValue(Constants.DATABASE_CREDENTIAL_PROFILE_ID).equals(requestBody.getValue(Constants.DATABASE_CREDENTIAL_PROFILE_ID)) || !existingRecord.getLong(Constants.PORT).equals(requestBody.getLong(Constants.PORT)))
                                {
                                    requestBody.put(Constants.STATUS, false);
                                }

                                reusableCondition.clear();

                                reusableQueryRequest.clear();

                                executeQuery(context, reusableQueryRequest
                                        .put(Constants.TABLE_NAME, tableName)
                                        .put(Constants.OPERATION, Constants.UPDATE)
                                        .put(Constants.DATA, requestBody)
                                        .put(Constants.CONDITION, reusableCondition.put(Constants.ID, profileId)), 200);
                            }
                            else
                            {
                                context.response().setStatusCode(404).end(new JsonObject().put(Constants.STATUS, Constants.FAIL).put(Constants.MESSAGE, Constants.MESSAGE_NOT_FOUND).encode());
                            }
                        }
                        else
                        {
                            context.response().setStatusCode(503).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
                        }
                    });
                }
                else
                {
                    reusableQueryRequest.clear();

                    reusableCondition.clear();

                    executeQuery(context, reusableQueryRequest
                            .put(Constants.TABLE_NAME, tableName)
                            .put(Constants.OPERATION, Constants.UPDATE)
                            .put(Constants.DATA, requestBody)
                            .put(Constants.CONDITION, reusableCondition.put(Constants.ID, profileId)), 200);
                }
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

        reusableQueryRequest.clear();

        reusableColumn.clear();

        executeQuery(context, reusableQueryRequest.put(Constants.TABLE_NAME, tableName).put(Constants.OPERATION, Constants.SELECT).put(Constants.COLUMNS, reusableColumn.add(Constants.DATABASE_ALL_COLUMN)), 200);

    }

    // Deletes a record from the database.
    // @param id The ID of the record to delete.
    // @param context The RoutingContext containing the request and response.
    public void delete(String id, RoutingContext context)
    {
        var tableName = Util.getTableNameFromContext(context);

        if (!(id == null || id.trim().isEmpty()))
        {
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
        else
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_PROFILE_ID);
        }
    }

    // Checks if a credential profile is in use before deleting it.
    // @param credentialId The ID of the credential profile to check.
    // @param context The RoutingContext containing the request and response.
    private void checkCredentialUsage(long credentialId, RoutingContext context)
    {
        reusableQueryBuilder.setLength(0);

        reusableCondition.clear();

        reusableQueryRequest.clear();

        reusableColumn.clear();

        var queryResult = QueryBuilder.buildQuery( reusableQueryRequest
                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_CREDENTIAL_PROFILE)
                .put(Constants.OPERATION, Constants.SELECT)
                .put(Constants.COLUMNS, reusableColumn.add(Constants.DATABASE_IN_USE_BY)).put(Constants.CONDITION, reusableCondition.put(Constants.ID, credentialId)),reusableQueryBuilder);

        reusableRequest.clear();

        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableRequest
                .put(Constants.QUERY, queryResult.query())
                .put(Constants.PARAMS, queryResult.params()), reply ->
        {
            if (reply.succeeded())
            {
                var result = reply.result().body();

                if (!result.getJsonArray(Constants.DATA).isEmpty() && result.getJsonArray(Constants.DATA).getJsonObject(0).getInteger(Constants.DATABASE_IN_USE_BY) > 0)
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
        reusableQueryRequest.clear();

        reusableCondition.clear();

        executeQuery(context, reusableQueryRequest
                .put(Constants.TABLE_NAME, tableName)
                .put(Constants.OPERATION, Constants.DELETE)
                .put(Constants.CONDITION, reusableCondition.put(Constants.ID, id)), 204);
    }

    // Executes a query on the database.
    // @param context The RoutingContext containing the request and response.
    // @param request The JSON object containing the query details.
    // @param successStatusCode The HTTP status code to return on success.
    private void executeQuery(RoutingContext context, JsonObject request, int successStatusCode)
    {
        reusableQueryBuilder.setLength(0);

        var queryResult = QueryBuilder.buildQuery(request,reusableQueryBuilder);

        reusableRequest.clear();

        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableRequest.put(Constants.QUERY, queryResult.query()).put(Constants.PARAMS, queryResult.params()), reply ->
        {
            if (reply.succeeded())
            {
                context.response().setStatusCode(successStatusCode).end(reply.result().body().toString());
            }
            else
            {
                var errorMessage = reply.cause().getMessage();

                if (errorMessage.contains(DUPLICATE_ERROR_CODE))
                {
                    logger.warn("Duplicate key error: {}", errorMessage);

                    apiResponse.clear();

                    context.response().setStatusCode(409).end(apiResponse
                                    .put(Constants.STATUS, Constants.FAIL)
                                    .put(Constants.MESSAGE, DUPLICATE_ERROR)
                                    .encode());
                }
                else if (errorMessage.contains(FOREIGN_KEY_ERROR_CODE))
                {
                    logger.warn("Foreign key error: {}", errorMessage);

                    apiResponse.clear();

                    context.response().setStatusCode(400).end(apiResponse
                            .put(Constants.STATUS, Constants.FAIL)
                            .put(Constants.MESSAGE, FOREIGN_KEY_ERROR)
                            .encode());
                }
                else if (reply.cause().getMessage().contains(CLASS_CAST_ERROR))
                {
                    apiResponse.clear();

                    context.response().setStatusCode(400).end(apiResponse
                            .put(Constants.STATUS, Constants.FAIL)
                            .put(Constants.MESSAGE, INVALID_VALUE_ERROR)
                            .encode());
                }
                else
                {
                    apiResponse.clear();

                    logger.error("Database operation failed: {}", errorMessage);

                    context.response().setStatusCode(500).end(apiResponse
                                    .put(Constants.STATUS, Constants.FAIL)
                                    .put(Constants.MESSAGE, Constants.MESSAGE_INTERNAL_SERVER_ERROR)
                                    .encode()
                    );
                }
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

            reusableRequest.clear();

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableRequest.put(Constants.QUERY, QUERY_FOR_DISCOVERY).put(Constants.PARAMS, new JsonArray().add(profileId)), fetchResult ->
            {
                if (!fetchResult.failed())
                {
                    if (!(fetchResult.result().body().getJsonArray(Constants.DATA) == null || fetchResult.result().body().getJsonArray(Constants.DATA).isEmpty()))
                    {
                        var discovery = fetchResult.result().body().getJsonArray(Constants.DATA).getJsonObject(0);

                        var targetIp = discovery.getString(Constants.IP);

                        if (!(targetIp == null || targetIp.isEmpty()))
                        {
                            logger.info("Found IP for request: {}", targetIp);

                            vertx.executeBlocking(promise ->
                            {
                                var isReachable = Util.ping(targetIp);

                                promise.complete(isReachable);
                            },false, res ->
                            {
                                if (res.succeeded() && (Boolean) res.result())
                                {
                                    if (discovery.getString(Constants.SYSTEM_TYPE).equalsIgnoreCase(Constants.SNMP))
                                    {
                                        var credentials = discovery.getJsonObject(Constants.CREDENTIALS);

                                        if (credentials != null)
                                        {
                                            reusableRequest.clear();

                                            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_ZMQ_ADDRESS, reusableRequest
                                                    .put(Constants.IP, targetIp)
                                                    .put(Constants.COMMUNITY, credentials.getString(Constants.COMMUNITY))
                                                    .put(Constants.VERSION, credentials.getString(Constants.VERSION))
                                                    .put(Constants.PLUGIN_TYPE, discovery.getString(Constants.SYSTEM_TYPE))
                                                    .put(Constants.PORT, discovery.getLong(Constants.PORT))
                                                    .put(Constants.REQUEST_TYPE, Constants.DISCOVERY), DELIVERY_OPTIONS, zmqResult ->
                                            {
                                                if (zmqResult.succeeded())
                                                {
                                                    var zmqResponse = zmqResult.result().body();

                                                    logger.info("ZMQ Response: {}", zmqResponse);

                                                    if (Constants.FAIL.equalsIgnoreCase(zmqResponse.getString(Constants.STATUS)))
                                                    {
                                                        reusableQueryBuilder.setLength(0);

                                                        reusableQueryRequest.clear();

                                                        reusableCondition.clear();

                                                        var updateStatusQuery = QueryBuilder.buildQuery(reusableQueryRequest.put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_DISCOVERY_PROFILE).put(Constants.OPERATION, Constants.UPDATE).put(Constants.DATA, new JsonObject().put(Constants.STATUS, false)).put(Constants.CONDITION,reusableCondition.put(Constants.ID, profileId)), reusableQueryBuilder);

                                                        reusableRequest.clear();

                                                        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableRequest.put(Constants.QUERY, updateStatusQuery.query()).put(Constants.PARAMS, updateStatusQuery.params()), updateResult ->
                                                        {
                                                            if (updateResult.succeeded())
                                                            {
                                                                apiResponse.clear();

                                                                context.response().setStatusCode(400).end(apiResponse.put(Constants.STATUS, Constants.FAIL).put(Constants.MESSAGE, DISCOVERY_FAIL + targetIp).put(Constants.ERROR, zmqResponse.getString(Constants.ERROR)).encode());
                                                            }
                                                            else
                                                            {
                                                                logger.error("Failed to update discovery status {}", discoveryProfileId);

                                                                context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
                                                            }
                                                        });
                                                    }

                                                    else if(Constants.SUCCESS.equalsIgnoreCase(zmqResponse.getString(Constants.STATUS)))
                                                    {
                                                        reusableQueryBuilder.setLength(0);

                                                        reusableQueryRequest.clear();

                                                        reusableCondition.clear();

                                                        var updateStatusQuery = QueryBuilder.buildQuery(reusableQueryRequest.put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_DISCOVERY_PROFILE).put(Constants.OPERATION, Constants.UPDATE).put(Constants.DATA, new JsonObject().put(Constants.STATUS, true)).put(Constants.CONDITION, reusableCondition.put(Constants.ID, profileId)), reusableQueryBuilder);

                                                        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, updateStatusQuery.query()).put(Constants.PARAMS, updateStatusQuery.params()), updateResult ->
                                                        {
                                                            if (updateResult.succeeded())
                                                            {
                                                                apiResponse.clear();

                                                                context.response().setStatusCode(200).end(apiResponse.put(Constants.STATUS, Constants.SUCCESS).put(Constants.MESSAGE, DISCOVERY_SUCCESSFUL).encode());
                                                            }
                                                            else
                                                            {
                                                                logger.error("Failed to update discovery status {}", discoveryProfileId);

                                                                context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
                                                            }
                                                        });
                                                    }
                                                }
                                                else
                                                {
                                                    logger.info(Constants.MESSAGE_ZMQ_NO_RESPONSE);

                                                    context.response().setStatusCode(504).end(Constants.MESSAGE_ZMQ_NO_RESPONSE);
                                                }
                                            });
                                        }
                                        else
                                        {
                                            context.response().setStatusCode(500).end(INVALID_CREDENTIAL_FORMAT);
                                        }
                                    }
                                    else
                                    {
                                        context.response().setStatusCode(400).end(INVALID_DEVICE_TYPE);
                                    }
                                }
                                else
                                {
                                    logger.error("Ping failed for IP: {}", targetIp);

                                    context.response().setStatusCode(400).end(new JsonObject().put(Constants.STATUS, Constants.FAIL).put(Constants.MESSAGE, PING_FAIL).encode());
                                }
                            });
                        }
                        else
                        {
                            context.response().setStatusCode(400).end(IP_NOT_FOUND);
                        }
                    }
                    else
                    {
                        logger.error("No discovery data found or credential profile deleted for profile id: {}", discoveryProfileId);

                        context.response().setStatusCode(404).end(DISCOVERY_NOT_FOUND);
                    }
                }
                else
                {
                    logger.error("Failed to fetch discovery for profile id: {}", discoveryProfileId);

                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
                }
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
            reusableQueryBuilder.setLength(0);

            reusableQueryRequest.clear();

            reusableColumn.clear();

            reusableCondition.clear();

            var queryResult = QueryBuilder.buildQuery(reusableQueryRequest.put(Constants.TABLE_NAME,Constants.DATABASE_TABLE_DISCOVERY_PROFILE).put(Constants.OPERATION,Constants.SELECT).put(Constants.COLUMNS,reusableColumn.add(Constants.DATABASE_ALL_COLUMN)).put(Constants.CONDITION,reusableCondition.put(Constants.ID,Long.parseLong(discoveryProfileId))),reusableQueryBuilder);

            reusableRequest.clear();

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableRequest.put(Constants.QUERY, queryResult.query()).put(Constants.PARAMS,queryResult.params()), checkReply ->
            {
                if (checkReply.succeeded())
                {
                    if (!(checkReply.result().body() == null || checkReply.result().body().getJsonArray(Constants.DATA).isEmpty()))
                    {
                        var data = checkReply.result().body().getJsonArray(Constants.DATA).getJsonObject(0);

                        var credentialProfileId = data.getLong(Constants.DATABASE_CREDENTIAL_PROFILE_ID);

                        if (data.getBoolean(Constants.STATUS) == null || !data.getBoolean(Constants.STATUS))
                        {
                            context.response().setStatusCode(400).end(Constants.MESSAGE_IP_NOT_DISCOVERED);

                            return;
                        }

                        if (credentialProfileId == null)
                        {
                            context.response().setStatusCode(400).end(Constants.MESSAGE_NULL_CREDENTIAL_ID);

                            return;
                        }

                        reusableQueryBuilder.setLength(0);

                        reusableQueryRequest.clear();

                        var insertQueryResult = QueryBuilder.buildQuery(reusableQueryRequest
                                .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISIONING_JOBS)
                                .put(Constants.OPERATION, Constants.INSERT)
                                .put(Constants.DATA, new JsonObject()
                                        .put(Constants.DATABASE_CREDENTIAL_PROFILE_ID, credentialProfileId)
                                        .put(Constants.IP, data.getString(Constants.IP))
                                        .put(Constants.PORT, data.getLong(Constants.PORT))), reusableQueryBuilder);

                        reusableRequest.clear();

                        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableRequest.put(Constants.QUERY, insertQueryResult.query()).put(Constants.PARAMS, insertQueryResult.params()), insertReply ->
                        {
                            if (insertReply.succeeded())
                            {
                                if (insertReply.result().body().getLong(Constants.ID) == null)
                                {
                                    context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);

                                    return;
                                }

                                var updateQuery = "UPDATE credential_profile SET in_use_by = in_use_by + 1 WHERE id = " + credentialProfileId;

                                reusableRequest.clear();

                                vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableRequest.put(Constants.QUERY, updateQuery), updateReply ->
                                {
                                    if (updateReply.succeeded())
                                    {
                                        apiResponse.clear();

                                        context.response().setStatusCode(200).end(apiResponse.put(Constants.STATUS, Constants.SUCCESS).put(Constants.MESSAGE, PROVISION_UPDATE_SUCCESSFUL).put(Constants.ID, insertReply.result().body().getLong(Constants.ID)).encode());
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
                                if (insertReply.cause().getMessage().contains(DUPLICATE_ERROR_CODE))
                                {
                                    reusableQueryBuilder.setLength(0);

                                    reusableQueryRequest.clear();

                                    reusableColumn.clear();

                                    reusableCondition.clear();

                                    var queryBuilder = QueryBuilder.buildQuery(reusableQueryRequest.put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISIONING_JOBS).put(Constants.OPERATION, Constants.SELECT).put(Constants.COLUMNS,reusableColumn.add(Constants.ID).add(Constants.DELETED)).put(Constants.CONDITION, reusableCondition.put(Constants.IP, data.getString(Constants.IP))), reusableQueryBuilder);

                                    reusableRequest.clear();

                                    vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableRequest.put(Constants.QUERY, queryBuilder.query()).put(Constants.PARAMS, queryBuilder.params()), selectReply ->
                                    {
                                        if (selectReply.succeeded())
                                        {
                                            if (selectReply.result().body().getJsonArray(Constants.DATA).isEmpty())
                                            {
                                                context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);

                                                return;
                                            }

                                            int existingId = selectReply.result().body().getJsonArray(Constants.DATA).getJsonObject(0).getInteger(Constants.ID);

                                            if (selectReply.result().body().getJsonArray(Constants.DATA).getJsonObject(0).getBoolean(Constants.DELETED))
                                            {
                                                reusableQueryBuilder.setLength(0);

                                                reusableQueryRequest.clear();

                                                var updateStatusQueryBuilder = QueryBuilder.buildQuery(reusableQueryRequest.put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISIONING_JOBS).put(Constants.OPERATION, Constants.UPDATE).put(Constants.DATA, new JsonObject().put(Constants.DELETED, false)).put(Constants.CONDITION, new JsonObject().put(Constants.ID, existingId)), reusableQueryBuilder);

                                                reusableRequest.clear();

                                                vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableQueryRequest.put(Constants.QUERY, updateStatusQueryBuilder.query()).put(Constants.PARAMS, updateStatusQueryBuilder.params()), updateStatusReply ->
                                                {
                                                    var updateQuery = "UPDATE credential_profile SET in_use_by = in_use_by + 1 WHERE id = " + credentialProfileId;

                                                    reusableRequest.clear();

                                                    vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableRequest.put(Constants.QUERY, updateQuery), updateReply ->
                                                    {
                                                        if (updateReply.succeeded())
                                                        {
                                                            if (updateStatusReply.succeeded())
                                                            {
                                                                apiResponse.clear();

                                                                context.response()
                                                                        .setStatusCode(200)
                                                                        .end(apiResponse
                                                                                .put(Constants.STATUS, Constants.SUCCESS)
                                                                                .put(Constants.MESSAGE, PROVISION_UPDATE_SUCCESSFUL)
                                                                                .put(Constants.ID, existingId)
                                                                                .encode());
                                                            }
                                                            else
                                                            {
                                                                logger.error("Failed to update deleted flag: {}", updateStatusReply.cause().getMessage());

                                                                context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);

                                                            }
                                                        }
                                                        else
                                                        {
                                                            context.response().setStatusCode(500).end(new JsonObject().put(Constants.STATUS, Constants.FAIL).put(Constants.MESSAGE, MESSAGE_CREDENTIAL_UPDATE_FAILED).encode());

                                                            logger.error("Failed to update in_use_by: {}", updateReply.cause().getMessage());
                                                        }
                                                    });


                                                });
                                            }
                                            else
                                            {
                                                apiResponse.clear();

                                                context.response().setStatusCode(409).end(apiResponse.put(Constants.STATUS, Constants.FAIL).put(Constants.MESSAGE, Constants.MESSAGE_POLLING_STARTED).encode());
                                            }
                                        }
                                        else
                                        {
                                            logger.error("Failed to fetch existing record: {}", selectReply.cause().getMessage());

                                            context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
                                        }
                                    });
                                }
                                else
                                {
                                    logger.error("Provisioning insert failed: {}", insertReply.cause().getMessage());

                                    context.response().setStatusCode(409).end(Constants.MESSAGE_POLLING_STARTED);
                                }
                            }
                        });
                    }
                    else
                    {
                        apiResponse.clear();

                        context.response().setStatusCode(404).end(apiResponse.put(Constants.STATUS,Constants.FAIL).put(Constants.MESSAGE,Constants.MESSAGE_NOT_FOUND).encode());
                    }
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
            reusableQueryBuilder.setLength(0);

            reusableQueryRequest.clear();

            reusableColumn.clear();

            reusableCondition.clear();

            var queryResult = QueryBuilder.buildQuery(reusableQueryRequest
                    .put(Constants.OPERATION, Constants.SELECT)
                    .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISION_DATA)
                    .put(Constants.COLUMNS, reusableColumn
                            .add(Constants.DATA)
                            .add(Constants.POLLED_AT))
                    .put(Constants.CONDITION, reusableCondition
                            .put(Constants.DATABASE_JOB_ID, Long.parseLong(jobId))),reusableQueryBuilder);

            reusableRequest.clear();

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableRequest.put(Constants.QUERY, queryResult.query() + " order by polled_at DESC").put(Constants.PARAMS, queryResult.params()), reply ->
            {
                if (reply.succeeded())
                {
                    if (reply.result().body() == null)
                    {
                        context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);

                        return;
                    }

                    if (reply.result().body().getJsonArray(Constants.DATA) == null || reply.result().body().getJsonArray(Constants.DATA).isEmpty())
                    {
                        apiResponse.clear();

                        context.response().setStatusCode(404).end(apiResponse.put(Constants.MESSAGE, DATA_NOT_FOUND + jobId).encode());

                        return;
                    }

                    var responseArray = new JsonArray();

                    reply.result().body().getJsonArray(Constants.DATA).forEach(row ->
                    {
                        var rowObj = (JsonObject) row;

                        responseArray.add(new JsonObject()
                                .put(Constants.DATA, rowObj.getJsonObject(Constants.DATA))
                                .put(Constants.POLLED_AT, DATE_FORMATTER.format(new java.util.Date(rowObj.getLong(Constants.POLLED_AT))))
                        );
                    });

                    apiResponse.clear();

                    context.response().setStatusCode(200).end(apiResponse.put(Constants.DATA, responseArray).encode());
                }
                else
                {
                    logger.error("Failed to fetch provision data: {}", reply.cause().getMessage());

                    apiResponse.clear();

                    context.response().setStatusCode(500).end(apiResponse.put(Constants.MESSAGE, Constants.MESSAGE_INTERNAL_SERVER_ERROR).encode());
                }
            });
        }
        catch (NumberFormatException e)
        {
            logger.error("Invalid job ID: {}", jobId);

            apiResponse.clear();

            context.response().setStatusCode(400).end(apiResponse.put(Constants.MESSAGE, Constants.MESSAGE_INVALID_PROFILE_ID).encode());
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
                    AND pd.data->'interfaces' IS NOT NULL
                    ORDER BY to_timestamp(pd.polled_at / 1000.0) DESC
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
                    AND pd.data->'interfaces' IS NOT NULL
                    ORDER BY to_timestamp(pd.polled_at / 1000.0) DESC
                    LIMIT 1
                ) latest_pd ON true
                CROSS JOIN jsonb_array_elements(latest_pd.interfaces) AS interface
                WHERE (interface->>'interface.speed') IS NOT NULL
                AND (interface->>'interface.speed') ~ '^[0-9]+$'
                AND (interface->>'interface.speed')::BIGINT > 0
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

    // Fetches top 10 devices based on uptime
    // @param context The RoutingContext containing the request and response.
    public void getDevicesByUptime(RoutingContext context)
    {
        var query = """
           WITH parsed_data AS (
                SELECT
                    pj.ip,
                    pd.job_id,
                    to_timestamp(pd.polled_at / 1000.0) AS polled_at_ts,
                    regexp_matches(pd.data->>'system.uptime', '([0-9]+) days, ([0-9]+) hours, ([0-9]+) minutes, ([0-9]+) seconds') AS matches,
                    pd.data->>'system.uptime' AS uptime
                FROM provision_data pd
                JOIN provisioning_jobs pj ON pd.job_id = pj.id
                WHERE pd.data ? 'system.uptime'
                AND to_timestamp(pd.polled_at / 1000.0) >= NOW() - INTERVAL '7 days'
            ),
            latest_uptime AS (
                SELECT DISTINCT ON (ip)
                    ip,
                    job_id,
                    uptime,
                    (matches[1])::int * 86400 + (matches[2])::int * 3600 + (matches[3])::int * 60 + (matches[4])::int AS uptime_seconds
                FROM parsed_data
                WHERE matches IS NOT NULL
                ORDER BY ip, polled_at_ts DESC
            )
            SELECT
                ip,
                job_id,
                uptime,
                DENSE_RANK() OVER (ORDER BY uptime_seconds DESC) AS rank
            FROM latest_uptime
            ORDER BY rank ASC
            LIMIT 10;
        """;

        executeAndRespond(context, query);
    }

    // Fetches top 10 interfaces based on downtime
    // @param context The RoutingContext containing the request and response.
    public void getInterfacesByDowntime(RoutingContext context)
    {
        var query = """
            WITH interface_status AS (
                  SELECT
                      pj.ip,
                      interface->>'interface.name' AS interface_name,
                      to_timestamp(pd.polled_at / 1000.0) AS polled_at_ts,
                      pd.polled_at AS polled_at_millis,
                      interface->>'interface.operational.status' AS status
                  FROM provisioning_jobs pj
                  JOIN provision_data pd ON pd.job_id = pj.id
                  CROSS JOIN jsonb_array_elements(pd.data->'interfaces') AS interface
                  WHERE pd.data->'interfaces' IS NOT NULL
                  AND to_timestamp(pd.polled_at / 1000.0) >= NOW() - INTERVAL '7 days'
              ),
              down_events AS (
                  SELECT
                      ip,
                      interface_name,
                      polled_at_millis,
                      status,
                      LAG(polled_at_millis) OVER (PARTITION BY ip, interface_name ORDER BY to_timestamp(polled_at_millis / 1000.0)) AS prev_polled_at_millis
                  FROM interface_status
                  WHERE status = '2'
              ),
              downtime_durations AS (
                  SELECT
                      ip,
                      interface_name,
                      SUM( (polled_at_millis - prev_polled_at_millis) / 1000 )::BIGINT AS total_downtime_seconds
                  FROM down_events
                  WHERE prev_polled_at_millis IS NOT NULL
                  GROUP BY ip, interface_name
              ),
              ranked_downtime AS (
                  SELECT
                      ip,
                      interface_name,
                      total_downtime_seconds,
                      CONCAT(
                          total_downtime_seconds / 86400, ' days ',
                          (total_downtime_seconds % 86400) / 3600, ' hrs ',
                          (total_downtime_seconds % 3600) / 60, ' mins ',
                          total_downtime_seconds % 60, ' secs'
                      ) AS human_readable_downtime,
                      DENSE_RANK() OVER (ORDER BY total_downtime_seconds DESC) AS rank
                  FROM downtime_durations
                  WHERE total_downtime_seconds > 0
              )
              SELECT ip, interface_name, human_readable_downtime, rank
              FROM ranked_downtime
              WHERE rank <= 10
              ORDER BY rank;
        """;

        executeAndRespond(context, query);
    }

    // Fetches all devices from provisioning jobs
    public void getDevices(RoutingContext context)
    {
        reusableQueryRequest.clear();

        reusableColumn.clear();

        reusableCondition.clear();

        executeQuery(context, reusableQueryRequest.put(Constants.TABLE_NAME,Constants.DATABASE_TABLE_PROVISIONING_JOBS).put(Constants.OPERATION,Constants.SELECT).put(Constants.COLUMNS,reusableColumn.add(Constants.ID).add(Constants.DATABASE_CREDENTIAL_PROFILE_ID).add(Constants.IP).add(Constants.PORT)).put(Constants.CONDITION,reusableCondition.put(Constants.DELETED,false)),200);
    }

    // Executes a query and responds with the result.
    // @param context The RoutingContext containing the request and response.
    // @param query The query to execute
    private void executeAndRespond(RoutingContext context, String query)
    {
        reusableRequest.clear();

        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableRequest.put(Constants.QUERY, query).put(Constants.PARAMS,new JsonArray()), reply ->
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

            reusableQueryBuilder.setLength(0);

            reusableQueryRequest.clear();

            reusableColumn.clear();

            reusableCondition.clear();

            var queryResult = QueryBuilder.buildQuery( (reusableQueryRequest.put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISIONING_JOBS).put(Constants.OPERATION, Constants.SELECT).put(Constants.COLUMNS,reusableColumn.add(Constants.DATABASE_CREDENTIAL_PROFILE_ID).add(Constants.DELETED)).put(Constants.CONDITION,reusableCondition.put(Constants.ID, parsedId))),reusableQueryBuilder);

            reusableRequest.clear();

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableRequest.put(Constants.QUERY, queryResult.query()).put(Constants.PARAMS,queryResult.params()), fetchReply ->
            {
                if (fetchReply.succeeded())
                {
                    if (fetchReply.result().body() == null || fetchReply.result().body().getJsonArray(Constants.DATA).isEmpty())
                    {
                        apiResponse.clear();

                        context.response().setStatusCode(404).end(apiResponse.put(Constants.STATUS,Constants.FAIL).put(Constants.MESSAGE,Constants.MESSAGE_JOB_NOT_FOUND).encode());

                        return;
                    }

                    reusableQueryBuilder.setLength(0);

                    reusableCondition.clear();

                    reusableQueryRequest.clear();

                    var deleteQuery = QueryBuilder.buildQuery(reusableQueryRequest
                            .put(Constants.TABLE_NAME, Constants.DATABASE_TABLE_PROVISIONING_JOBS)
                            .put(Constants.OPERATION, Constants.UPDATE)
                            .put(Constants.DATA, new JsonObject().put(Constants.DELETED, true))
                            .put(Constants.CONDITION, reusableCondition.put(Constants.ID, parsedId)),reusableQueryBuilder);

                    reusableRequest.clear();

                    vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, reusableRequest.put(Constants.QUERY, deleteQuery.query()).put(Constants.PARAMS, deleteQuery.params()), deleteReply ->
                    {
                        if (deleteReply.succeeded())
                        {
                            if (fetchReply.result().body().getJsonArray(Constants.DATA).getJsonObject(0).getLong(Constants.DATABASE_CREDENTIAL_PROFILE_ID) != null)
                            {
                                var updateQuery = "UPDATE credential_profile SET in_use_by = in_use_by - 1 WHERE id = " + fetchReply.result().body().getJsonArray(Constants.DATA).getJsonObject(0).getLong(Constants.DATABASE_CREDENTIAL_PROFILE_ID);

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
            apiResponse.clear();

            context.response().setStatusCode(400).end(apiResponse.put(Constants.STATUS,Constants.FAIL).put(Constants.MESSAGE,Constants.MESSAGE_INVALID_PROFILE_ID).encode());
        }

        catch (Exception e)
        {
            context.response().setStatusCode(500).end(Constants.MESSAGE_INTERNAL_SERVER_ERROR);
        }
    }
}