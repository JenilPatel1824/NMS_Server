package io.vertx.nms.engine;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class PollingEngine extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(PollingEngine.class);

    private static final String DB_QUERY_ADDRESS = "database.query.execute";

    private static final String ZMQ_REQUEST_ADDRESS = "zmq.send";

    private static final int BATCH_SIZE = 10;

    private static final long BATCH_FLUSH_INTERVAL = 20_000;

    private final List<JsonObject> batchSnmpData = new ArrayList<>();

    private long lastFlushTime = System.currentTimeMillis();

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.setTimer(50000000, id -> fetchProvisionedDevices());

        vertx.setPeriodic(1000, id -> checkBatchTimeFlush());

        startPromise.complete();
    }

    private void fetchProvisionedDevices() {
        logger.info("-----------------Fetching provisioned devices...------------------------------------------");

        JsonObject queryRequest = new JsonObject().put("query",
                "SELECT d.discovery_profile_name, d.ip, " +
                        "c.system_type, c.credentials " +
                        "FROM discovery_profiles d " +
                        "JOIN credential_profile c ON d.credential_profile_name = c.credential_profile_name " +
                        "WHERE d.provision = TRUE"
        );


        vertx.eventBus().request(DB_QUERY_ADDRESS, queryRequest, reply -> {
            if (reply.succeeded()) {
                processDevices(reply.result().body());
            } else {
                logger.error("Failed to fetch provisioned devices: {}", reply.cause().getMessage());
            }
        });
    }

    private void processDevices(Object body) {
        if (!(body instanceof JsonObject)) return;

        JsonObject response = (JsonObject) body;

        if (!response.containsKey("data")) return;

        response.getJsonArray("data").forEach(entry -> {
            JsonObject device = (JsonObject) entry;

            sendZmqRequest(device);
        });
    }

    private void sendZmqRequest(JsonObject device) {
        JsonObject credentials = device.getJsonObject("credentials");

        JsonObject requestObject = new JsonObject()
                .put("ip", device.getString("ip"))
                .put("community", credentials.getString("community"))
                .put("version", credentials.getString("version"))
                .put("requestType", "polling")
                .put("pluginType", device.getString("system_type"));

        vertx.eventBus().request(ZMQ_REQUEST_ADDRESS, requestObject, reply -> {
            if (reply.succeeded()) {
                JsonObject snmpData;

                try {
                    snmpData = new JsonObject(reply.result().body().toString());
                } catch (Exception e) {
                    logger.error("Failed to parse SNMP response: {}", reply.result().body(), e);
                    return;
                }

                addToBatch(snmpData, device.getString("discovery_profile_name"));
            } else {
                logger.error("Failed to get SNMP response: {}", reply.cause().getMessage());
            }
        });
    }

    private void addToBatch(JsonObject snmpData, String discoveryProfileName) {
        JsonObject entry = new JsonObject()
                .put("discovery_profile_name", discoveryProfileName)
                .put("data", snmpData);

        batchSnmpData.add(entry);

        if (batchSnmpData.size() >= BATCH_SIZE) {
            flushBatchData();
        }
    }

    private void checkBatchTimeFlush() {
        long currentTime = System.currentTimeMillis();

        if (!batchSnmpData.isEmpty() && (currentTime - lastFlushTime >= BATCH_FLUSH_INTERVAL)) {
            flushBatchData();
        }
    }

    private void flushBatchData() {
        if (batchSnmpData.isEmpty()) return;

        List<JsonObject> batchCopy = new ArrayList<>(batchSnmpData);

        batchSnmpData.clear();

        lastFlushTime = System.currentTimeMillis();

        storeSnmpDataBatch(batchCopy);
    }

    private void storeSnmpDataBatch(List<JsonObject> snmpDataList) {
        if (snmpDataList.isEmpty()) return;

        logger.info("Storing {} SNMP records in batch...", snmpDataList.size());

        StringBuilder queryBuilder = new StringBuilder("INSERT INTO discovery_data (discovery_profile_name, data) VALUES ");

        JsonArray params = new JsonArray();

        int index = 1;

        for (JsonObject data : snmpDataList) {
            queryBuilder.append("($").append(index++).append(", $").append(index++).append("),");

            params.add(data.getString("discovery_profile_name")).add(data.getJsonObject("data"));
        }

        queryBuilder.setLength(queryBuilder.length() - 1);

        queryBuilder.append(" RETURNING id");

        JsonObject queryRequest = new JsonObject()
                .put("query", queryBuilder.toString())
                .put("params", params);

        vertx.eventBus().request(DB_QUERY_ADDRESS, queryRequest, reply -> {
            if (reply.succeeded()) {
                logger.info("Batch SNMP data stored successfully.");
            } else {
                logger.error("Failed to store SNMP data batch: {}", reply.cause().getMessage());
            }
        });
    }
}
