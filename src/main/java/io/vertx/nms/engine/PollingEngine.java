package io.vertx.nms.engine;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollingEngine extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(PollingEngine.class);

    @Override
    public void start(Promise<Void> startPromise) {
        EventBus eventBus = vertx.eventBus();

        // Poll every 5 seconds (5000 ms)
        vertx.setTimer(5000, timerId -> {
            logger.info("Starting polling process...");

            // Fetch all discovery profiles with provision true
            String discoveryQuery = "SELECT discovery_profile_name, ip, credential_profile_name " +
                    "FROM discovery WHERE provision = true";

            eventBus.request("database.query.execute", new JsonObject().put("query", discoveryQuery), discoveryReply -> {
                if (discoveryReply.succeeded()) {
                    JsonObject discoveryResult = (JsonObject) discoveryReply.result().body();
                    JsonArray discoveryData = discoveryResult.getJsonArray("data");

                    if (discoveryData != null && !discoveryData.isEmpty()) {
                        for (int i = 0; i < discoveryData.size(); i++) {
                            JsonObject discoveryProfile = discoveryData.getJsonObject(i);
                            String credentialProfileName = discoveryProfile.getString("credential_profile_name");

                            logger.info("Processing discovery profile: {}", discoveryProfile);

                            // Fetch credential details for the discovery profile, including system_type
                            String credentialQuery = String.format(
                                    "SELECT community, version, system_type FROM credential WHERE credential_profile_name = '%s'",
                                    credentialProfileName);

                            eventBus.request("database.query.execute", new JsonObject().put("query", credentialQuery), credentialReply -> {
                                if (credentialReply.succeeded()) {
                                    JsonObject credentialResult = (JsonObject) credentialReply.result().body();
                                    JsonArray credentialData = credentialResult.getJsonArray("data");

                                    if (credentialData != null && credentialData.size() > 0) {
                                        JsonObject credentialProfile = credentialData.getJsonObject(0);

                                        // Create the request object for polling
                                        JsonObject requestObject = new JsonObject()
                                                .put("ip", discoveryProfile.getString("ip"))
                                                .put("community", credentialProfile.getString("community"))
                                                .put("version", credentialProfile.getString("version"))
                                                .put("pluginType", credentialProfile.getString("system_type"))
                                                .put("requestType", "polling");

                                        logger.info("Sending polling request for " + requestObject);

                                        // Send request to ZMQ sender
                                        eventBus.request("zmq.send", requestObject, zmqResult -> {
                                            if (zmqResult.succeeded()) {
                                                String responseString = (String) zmqResult.result().body();
                                                JsonObject response = new JsonObject(responseString);
                                                logger.info("Polling request response: " + response);

                                                // Check for errors
                                                if (response.containsKey("error")) {
                                                    String error = response.getString("error");
                                                    logger.error("Polling Error: {}", error);

                                                    // Store the error in the snmp table
                                                    String insertSnmpErrorQuery = String.format(
                                                            "INSERT INTO snmp (discovery_profile_name, error) VALUES ('%s', '%s')",
                                                            discoveryProfile.getString("discovery_profile_name"),
                                                            error.replace("'", "''")
                                                    );
                                                    eventBus.request("database.query.execute", new JsonObject().put("query", insertSnmpErrorQuery));
                                                } else {
                                                    // Store SNMP data
                                                    JsonObject snmpData = response.getJsonObject("snmp");
                                                    String insertSnmpQuery = String.format(
                                                            "INSERT INTO snmp (discovery_profile_name, system_name, system_description, system_location, system_object_id, system_uptime) " +
                                                                    "VALUES ('%s', '%s', '%s', '%s', '%s', '%s') RETURNING id",
                                                            discoveryProfile.getString("discovery_profile_name"),
                                                            snmpData.getString("system.name"),
                                                            snmpData.getString("system.description"),
                                                            snmpData.getString("system.location"),
                                                            snmpData.getString("system.objectId"),
                                                            snmpData.getString("system.uptime")
                                                    );

                                                    eventBus.request("database.query.execute", new JsonObject().put("query", insertSnmpQuery), insertReply -> {
                                                        if (insertReply.succeeded()) {
                                                            String insertResultString = (String) insertReply.result().body();
                                                            JsonObject insertResult = new JsonObject(insertResultString);
                                                            int snmpId = insertResult.getInteger("id");

                                                            JsonArray interfaces = response.getJsonArray("snmp.interface");

                                                            for (int j = 0; j < interfaces.size(); j++) {
                                                                JsonObject iface = interfaces.getJsonObject(j);

                                                                // Store Interface data with foreign key to snmp table
                                                                String insertInterfaceQuery = String.format(
                                                                        "INSERT INTO interface (snmp_id, interface_index, interface_name, interface_alias, operational_status, admin_status, description, sent_error_packet, received_error_packet, sent_octets, received_octets, speed, physical_address, discard_packets, in_packets, out_packets) " +
                                                                                "VALUES (%d, '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s')",
                                                                        snmpId,
                                                                        iface.getString("interface.index"),
                                                                        iface.getString("interface.name"),
                                                                        iface.getString("interface.alias"),
                                                                        iface.getString("interface.operational.status"),
                                                                        iface.getString("interface.admin.status"),
                                                                        iface.getString("interface.description"),
                                                                        iface.getString("interface.sent.error.packet"),
                                                                        iface.getString("interface.received.error.packet"),
                                                                        iface.getString("interface.sent.octets"),
                                                                        iface.getString("interface.received.octets"),
                                                                        iface.getString("interface.speed"),
                                                                        iface.getString("interface.physical.address"),
                                                                        iface.getString("interface.discard.packets"),
                                                                        iface.getString("interface.in.packets"),
                                                                        iface.getString("interface.out.packets")
                                                                );

                                                                eventBus.request("database.query.execute", new JsonObject().put("query", insertInterfaceQuery));
                                                            }
                                                        }
                                                    });
                                                }
                                            }
                                        });
                                    }
                                }
                            });
                        }
                    }
                }
            });
        });

        startPromise.complete();
    }
}
