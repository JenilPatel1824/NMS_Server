package io.vertx.nms.engine;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollingEngine extends AbstractVerticle
{

    private static final Logger logger = LoggerFactory.getLogger(PollingEngine.class);

    private static final String DB_QUERY_ADDRESS = "database.query.execute";

    private static final String ZMQ_REQUEST_ADDRESS = "zmq.send";

    @Override
    public void start(Promise<Void> startPromise)
    {
        vertx.setTimer(5000, id -> fetchProvisionedDevices());

        startPromise.complete();
    }

    private void fetchProvisionedDevices()
    {
        logger.info("calling ");

        JsonObject queryRequest = new JsonObject().put("query",
                "SELECT d.id as discovery_id, d.discovery_profile_name, d.ip, " +
                        "c.credential_profile_name, c.community, c.version, c.system_type " +
                        "FROM discovery d " +
                        "JOIN credential c ON d.credential_profile_name = c.credential_profile_name " +
                        "WHERE d.provision = TRUE"
        );

        vertx.eventBus().request(DB_QUERY_ADDRESS, queryRequest, reply ->
        {
            if (reply.succeeded())
            {
                processDevices(reply.result().body());

            }
            else
            {
                System.err.println("Failed to fetch provisioned devices: " + reply.cause().getMessage());
            }
        });
    }

    private void processDevices(Object body)
    {
        if (!(body instanceof JsonObject)) return;

        JsonObject response = (JsonObject) body;

        if (!response.containsKey("data")) return;

        response.getJsonArray("data").forEach(entry ->
        {
            JsonObject device = (JsonObject) entry;

            sendZmqRequest(device);
        });
    }

    private void sendZmqRequest(JsonObject device)
    {
        JsonObject requestObject = new JsonObject()
                .put("ip", device.getString("ip"))
                .put("community", device.getString("community"))
                .put("version", device.getString("version"))
                .put("requestType","polling")
                .put("pluginType", device.getString("system_type"));

        vertx.eventBus().request(ZMQ_REQUEST_ADDRESS, requestObject, reply ->
        {
            if (reply.succeeded())
            {
                JsonObject snmpData;
                try {
                    snmpData = new JsonObject(reply.result().body().toString()); // Ensure conversion
                } catch (Exception e) {
                    logger.error("Failed to parse SNMP response: {}", reply.result().body(), e);
                    return;
                }

                storeSnmpData(snmpData, device.getString("discovery_profile_name"));
            }
            else
            {
                logger.error("Failed to get SNMP response: " + reply.cause().getMessage());
            }
        });
    }

    private void storeSnmpData(Object body, String discoveryProfileName)
    {
        logger.info("store snmp data ");

        if (!(body instanceof JsonObject)) return;

        JsonObject snmpData = (JsonObject) body;



        String snmpInsertQuery = String.format(
                "INSERT INTO snmp (discovery_profile_name, system_name, system_description, system_location, system_object_id, system_uptime, error) " +
                        "VALUES ('%s', '%s', '%s', '%s', '%s', '%s', '%s') RETURNING id",
                discoveryProfileName,
                sanitize(snmpData.getString("system.name")),
                sanitize(snmpData.getString("system.description")),
                sanitize(snmpData.getString("system.location")),
                sanitize(snmpData.getString("system.objectId")),
                sanitize(snmpData.getString("system.uptime")),
                sanitize(snmpData.getString("error"))
        );

        JsonObject queryRequest = new JsonObject().put("query", snmpInsertQuery);

        vertx.eventBus().request(DB_QUERY_ADDRESS, queryRequest, reply ->
        {
            if (reply.succeeded())
            {
                JsonObject response = (JsonObject) reply.result().body();

                Integer snmpIdObj = response.getInteger("id");
                if (snmpIdObj == null) {
                    logger.error("SNMP ID is null. Response: {}", response.encodePrettily());
                    return;
                }
                int snmpId = snmpIdObj;


                storeInterfaceData(snmpData.getJsonArray("interfaces"), snmpId);
            }
            else
            {
                logger.error("Failed to store SNMP data: {}", reply.cause().getMessage());
            }
        });
    }


    private void storeInterfaceData(JsonArray interfaces, int snmpId)
    {
        if (interfaces == null || interfaces.isEmpty()) return;

        interfaces.forEach(entry ->
        {
            JsonObject iface = (JsonObject) entry;

            String interfaceInsertQuery = String.format(
                    "INSERT INTO snmp_interface (snmp_id, interface_index, interface_name, interface_alias, interface_operational_status, " +
                            "interface_admin_status, interface_description, interface_sent_error_packet, interface_received_error_packet, interface_sent_octets, " +
                            "interface_received_octets, interface_speed, interface_physical_address, interface_discard_packets, interface_in_packets, interface_out_packets) " +
                            "VALUES (%d, %d, '%s', '%s', '%s', '%s', '%s', %d, %d, %d, %d, %d, '%s', %d, %d, %d)",
                    snmpId,
                    iface.getInteger("interface.index"),
                    sanitize(iface.getString("interface.name")),
                    sanitize(iface.getString("interface.alias")),
                    sanitize(iface.getString("interface.operational.status")),
                    sanitize(iface.getString("interface.admin_status")),
                    sanitize(iface.getString("interface.description")),
                    iface.getLong("interface.sent.error.packet"),
                    iface.getLong("interface.received.error.packet"),
                    iface.getLong("interface.sent.octets"),
                    iface.getLong("interface.received.octets"),
                    iface.getLong("interface.speed"),
                    sanitize(iface.getString("interface.physical.address")),
                    iface.getLong("interface.discard.packets"),
                    iface.getLong("interface.in.packets"),
                    iface.getLong("interface.out.packets")
            );

            JsonObject queryRequest = new JsonObject().put("query", interfaceInsertQuery);

            vertx.eventBus().request(DB_QUERY_ADDRESS, queryRequest);
        });
    }

    private String sanitize(String value)
    {
        if (value == null) return "";

        return value.replace("'", "''");
    }
}