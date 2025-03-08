package io.vertx.nms.engine;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.nms.util.Constants;
import io.vertx.nms.database.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PollingEngine extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(PollingEngine.class);

    private static final String DB_QUERY_ADDRESS = Constants.EVENTBUS_DATABASE_ADDRESS;

    private static final String ZMQ_REQUEST_ADDRESS = Constants.EVENTBUS_ZMQ_ADDRESS;

    private static final int BATCH_SIZE = 10;

    private static final long BATCH_FLUSH_INTERVAL = 20_000;

    private static final long BATCH_FLUSH_CHECK_INTERVAL = 10_000;

    private static final long FETCH_DEVICE_INTERVAL = 300000;

    private final List<JsonObject> batchSnmpData = new ArrayList<>();

    private long lastFlushTime = System.currentTimeMillis();

    @Override
    public void start(Promise<Void> startPromise)
    {
        vertx.setPeriodic(FETCH_DEVICE_INTERVAL, id -> fetchProvisionedDevices());

        vertx.setPeriodic(BATCH_FLUSH_CHECK_INTERVAL, id -> checkBatchTimeFlush());

        startPromise.complete();
    }

    // Fetches provision enabled devices from the database and send them for process.
    // Constructs a query to retrieve relevant device details and sends it via the event bus.
    // On success, processes the retrieved device data; on failure, logs an error.
    private void fetchProvisionedDevices()
    {
        JsonObject request = new JsonObject()
                .put(Constants.OPERATION, Constants.DATABASE_OPERATION_SELECT)
                .put(Constants.TABLE_NAME, "discovery_profiles d JOIN credential_profile c ON d.credential_profile_name = c.credential_profile_name")
                .put(Constants.COLUMNS, new JsonArray()
                        .add("d.discovery_profile_name")
                        .add("d.ip")
                        .add("c.system_type")
                        .add("c.credentials"))
                .put(Constants.CONDITION, new JsonObject()
                        .put("d.provision", true)
                        .put("c.system_type", "snmp"));

        QueryBuilder.QueryResult queryResult = QueryBuilder.buildQuery(request);

        vertx.eventBus().request(DB_QUERY_ADDRESS, new JsonObject().put(Constants.QUERY,queryResult.getQuery()).put(Constants.PARAMS,queryResult.getParams()), reply ->
        {
            if (reply.succeeded())
            {
                logger.info("Device Fetch Successful");

                processDevices(reply.result().body());
            }
            else
            {
                logger.error("Failed to fetch provisioned devices: {}", reply.cause().getMessage());
            }
        });
    }

    // Processes the fetched provisioned devices.
    // Extracts the Constants.DATA array from the response and iterates through each device.
    // Sends each device's details to the ZMQ request handler.
    // @param body The response object received from the database query, expected to be a JsonObject containing a Constants.DATA array.
    private void processDevices(Object body)
    {
        if (!(body instanceof JsonObject response)) return;

        if (!response.containsKey(Constants.DATA)) return;

        response.getJsonArray(Constants.DATA).forEach(entry ->
        {
            JsonObject device = (JsonObject) entry;

            sendZmqRequest(device);
        });
    }

    // Constructs a request JSON with device details and credentials.
    // Sends a polling request to the ZMQ service for the given device.
    // On success, parses the SNMP response and adds it to the batch for further processing.
    // @param device The JSON object containing device details, including IP, credentials, and system type.
    private void sendZmqRequest(JsonObject device)
    {
        JsonObject credentials = device.getJsonObject(Constants.CREDENTIALS);

        JsonObject requestObject = new JsonObject()
                .put(Constants.IP, device.getString(Constants.IP))
                .put(Constants.COMMUNITY, credentials.getString(Constants.COMMUNITY))
                .put(Constants.VERSION, credentials.getString(Constants.VERSION))
                .put(Constants.REQUEST_TYPE, Constants.POLLING)
                .put(Constants.PLUGIN_TYPE, device.getString(Constants.SYSTEM_TYPE));

        vertx.eventBus().<JsonObject>request(ZMQ_REQUEST_ADDRESS, requestObject, reply ->
        {
            if (reply.succeeded() && reply.result().body().getString(Constants.STATUS).equalsIgnoreCase("success"))
            {
                addToBatch(reply.result().body().getJsonObject("data"), device.getString(Constants.DATABASE_DISCOVERY_PROFILE_NAME));
            }
            else
            {
                logger.error("Failed to get SNMP response: {}", reply.cause().getMessage());
            }
        });
    }

    // Adds SNMP data to the batch for bulk insertion.
    // If the batch size reaches the predefined limit, it triggers a flush operation.
    // @param snmpData The JSON object containing SNMP response data for the device.
    // @param discoveryProfileName The name of the discovery profile associated with the device.
    private void addToBatch(JsonObject snmpData, String discoveryProfileName)
    {
        ZonedDateTime istTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

        String timestamp = istTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        JsonObject entry = new JsonObject()
                .put(Constants.DATABASE_DISCOVERY_PROFILE_NAME, discoveryProfileName)
                .put(Constants.DATA, snmpData)
                .put(Constants.DATABASE_COLUMN_POLLED_AT, timestamp);

        batchSnmpData.add(entry);

        if (batchSnmpData.size() >= BATCH_SIZE)
        {
            flushBatchData();
        }
    }

    // Checks if batch flush interval has passed and flushes if needed.
    private void checkBatchTimeFlush()
    {
        long currentTime = System.currentTimeMillis();

        if (!batchSnmpData.isEmpty() && (currentTime - lastFlushTime >= BATCH_FLUSH_INTERVAL))
        {
            flushBatchData();
        }
    }

    // Flushes batch data and stores it.
    private void flushBatchData()
    {
        logger.info("flushing batch {}", batchSnmpData.size());

        if (batchSnmpData.isEmpty()) return;

        List<JsonObject> batchCopy = new ArrayList<>(batchSnmpData);

        batchSnmpData.clear();

        lastFlushTime = System.currentTimeMillis();

        storeSnmpDataBatch(batchCopy);
    }

    // Stores SNMP data in batch.
    // @param snmpDataList List of JSON objects containing SNMP data to be stored.
     private void storeSnmpDataBatch(List<JsonObject> snmpDataList)
     {
         if (snmpDataList.isEmpty()) return;

         logger.info("Storing {} SNMP records in batch...", snmpDataList.size());

         StringBuilder queryBuilder = new StringBuilder("INSERT INTO provision_data (discovery_profile_name, data, polled_at) VALUES ");

         JsonArray params = new JsonArray();

         int index = 1;

         for (JsonObject data : snmpDataList)
         {
             queryBuilder.append("($").append(index++).append(", $").append(index++).append(", $").append(index++).append("),");

             params.add(data.getString(Constants.DATABASE_DISCOVERY_PROFILE_NAME))
                     .add(data.getJsonObject(Constants.DATA))
                     .add(data.getString(Constants.DATABASE_COLUMN_POLLED_AT));
         }

         queryBuilder.setLength(queryBuilder.length() - 1);

         queryBuilder.append(" RETURNING id");

         JsonObject queryRequest = new JsonObject()
                 .put(Constants.QUERY, queryBuilder.toString())
                 .put(Constants.PARAMS, params);

         vertx.eventBus().request(DB_QUERY_ADDRESS, queryRequest, reply ->
         {
             if (reply.succeeded())
             {
                 logger.info("Batch SNMP data stored successfully.");
             }
             else
             {
                 logger.error("Failed to store SNMP data batch: {}", reply.cause().getMessage());
             }
         });
     }
}
