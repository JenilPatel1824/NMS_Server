package io.vertx.nms.polling;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.nms.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PollingProcessor extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(PollingProcessor.class);

    private static final int DATABASE_INSERT_BATCH_SIZE = 20;

    private static final long BATCH_FLUSH_INTERVAL = 20_000;

    private static final long BATCH_FLUSH_CHECK_INTERVAL = 10_000;

    private static final long RESPONSE_TIMEOUT = 270_000;

    private static final long CHECK_TIMEOUT_MS = 30_000;

    private final List<JsonObject> batchSnmpData = new ArrayList<>();

    private final Map<Long, Long> pendingRequests = new HashMap<>();

    private long lastFlushTime = System.currentTimeMillis();

    private final JsonObject requestJson = new JsonObject();

    private final StringBuilder queryBuilder = new StringBuilder();

    private final JsonArray params = new JsonArray();

    private final JsonObject dbRequest = new JsonObject();

    @Override
    public void start(Promise<Void> startPromise)
    {
        vertx.eventBus().<JsonArray>localConsumer(Constants.EVENTBUS_POLLING_BATCH_ADDRESS, message ->
        {
            logger.info("received batch of "+message.body().size());

            clearPendingRequests();

            processDevices(message.body());
        });

        vertx.eventBus().<JsonObject>localConsumer(Constants.EVENTBUS_POLLING_REPLY_ADDRESS, message ->
        {
            if(message.body() != null)
            {
                addToBatch(message.body().getJsonObject(Constants.DATA), Long.parseLong(message.body().getString(Constants.DATABASE_JOB_ID)));
            }

        });

        vertx.setPeriodic(CHECK_TIMEOUT_MS, id -> checkPendingTimeouts());

        vertx.setPeriodic(BATCH_FLUSH_CHECK_INTERVAL, id -> checkBatchTimeFlush());

        logger.info("Polling Worker started");

        startPromise.complete();
    }

    // Processes the fetched provisioned devices.
    // Extracts the Constants.DATA array from the response and iterates through each device.
    // Sends each device's details to the ZMQ request handler.
    // @param body The response object received from the database query, expected to be a JsonObject containing a Constants.DATA array.
    private void processDevices(JsonArray body)
    {
        body.forEach(entry ->
        {
            var device = (JsonObject) entry;

            pendingRequests.put(device.getLong(Constants.DATABASE_JOB_ID), System.currentTimeMillis());

            sendZmqRequest((JsonObject) entry);
        });
    }

    // Constructs a request JSON with device details and credentials.
    // Sends a polling request to the ZMQ service for the given device.
    // On success, parses the SNMP response and adds it to the batch for further processing.
    // @param device The JSON object containing device details, including IP, credentials, and system type.
    private void sendZmqRequest(JsonObject device)
    {
        requestJson.clear();

        vertx.eventBus().send(Constants.EVENTBUS_ZMQ_ADDRESS, requestJson
                        .put(Constants.IP, device.getString(Constants.IP))
                        .put(Constants.COMMUNITY, device.getJsonObject(Constants.CREDENTIALS).getString(Constants.COMMUNITY))
                        .put(Constants.VERSION, device.getJsonObject(Constants.CREDENTIALS).getString(Constants.VERSION))
                        .put(Constants.REQUEST_TYPE, Constants.POLLING)
                        .put(Constants.PORT, device.getLong(Constants.PORT))
                        .put(Constants.PLUGIN_TYPE, device.getString(Constants.SYSTEM_TYPE))
                        .put(Constants.DATABASE_JOB_ID, device.getLong(Constants.DATABASE_JOB_ID))
        );
    }

    // Adds SNMP data to the batch for bulk insertion.
    // If the batch size reaches the predefined limit, it triggers a flush operation.
    // @param snmpData The JSON object containing SNMP response data for the device.
    // @param jobId The ID of the job associated with the device.
    private void addToBatch(JsonObject snmpData, long jobId)
    {
        pendingRequests.remove(jobId);

        batchSnmpData.add(new JsonObject()
                    .put(Constants.DATABASE_JOB_ID, jobId)
                    .put(Constants.DATA, snmpData)
                    .put(Constants.POLLED_AT, System.currentTimeMillis()));

            if (batchSnmpData.size() >= DATABASE_INSERT_BATCH_SIZE)
            {
                flushBatchData();
            }
    }

    // Checks if batch flush interval has passed and flushes if needed.
    private void checkBatchTimeFlush()
    {
        if (!batchSnmpData.isEmpty() && (System.currentTimeMillis() - lastFlushTime >= BATCH_FLUSH_INTERVAL))
        {
            flushBatchData();
        }
    }

    // Flushes batch data and stores it.
    private void flushBatchData()
    {
        logger.info("flushing batch {}", batchSnmpData.size());

        if (!batchSnmpData.isEmpty())
        {
            var batchCopy = new ArrayList<>(batchSnmpData);

            batchSnmpData.clear();

            lastFlushTime = System.currentTimeMillis();

            storeSnmpDataBatch(batchCopy);
        }
    }

    // Stores SNMP data in batch.
    // @param snmpDataList List of JSON objects containing SNMP data to be stored.
    private void storeSnmpDataBatch(List<JsonObject> snmpDataList)
    {
        if (!snmpDataList.isEmpty())
        {
            logger.info("Storing {} SNMP records in batch...", snmpDataList.size());

            queryBuilder.setLength(0);

            params.clear();

            dbRequest.clear();

            queryBuilder.append("insert into provision_data (job_id, data, polled_at) values ");

            var index = 1;

            for (var data : snmpDataList)
            {
                queryBuilder.append("($").append(index++).append(", $").append(index++).append(", $").append(index++).append("),");

                params.add(data.getLong(Constants.DATABASE_JOB_ID))
                        .add(data.getJsonObject(Constants.DATA))
                        .add(data.getLong(Constants.POLLED_AT));
            }

            queryBuilder.setLength(queryBuilder.length() - 1);

            vertx.eventBus().send(Constants.EVENTBUS_DATABASE_ADDRESS, dbRequest.put(Constants.QUERY, queryBuilder.toString()).put(Constants.PARAMS, params));
        }
    }

    // Checks for pending requests that have exceeded the response timeout.
    // Logs error and removes the timed-out requests from the pending list.
    private void checkPendingTimeouts()
    {
        var now = System.currentTimeMillis();

        pendingRequests.entrySet().removeIf(entry -> {

            if (now - entry.getValue() > RESPONSE_TIMEOUT)
            {
                logger.error("Polling response timeout for jobId {}", entry.getKey());

                return true;
            }
            return false;
        });
    }

    // Logs all pending requests as errors and clears the pending requests map.
    // This is called when a new batch starts to reset old pending requests.
    private void clearPendingRequests()
    {
        if (!pendingRequests.isEmpty())
        {
            for (Map.Entry<Long, Long> entry : pendingRequests.entrySet())
            {
                var jobId = entry.getKey();

                long sentTime = entry.getValue();

                logger.error("pending request detected. jobId={}, sentAt={}", jobId, sentTime);
            }
            pendingRequests.clear();
        }
    }
}