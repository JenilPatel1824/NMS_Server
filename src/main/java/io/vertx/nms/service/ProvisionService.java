package io.vertx.nms.service;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ProvisionService
{
    private static final Logger logger = LoggerFactory.getLogger(ProvisionService.class);

    private final EventBus eventBus;

    public ProvisionService(Vertx vertx)
    {
        this.eventBus = vertx.eventBus();
    }

    public void updateProvisionStatus(String discoveryProfileName, String status, RoutingContext ctx)
    {
        boolean provisionStatus;

        if ("yes".equalsIgnoreCase(status))
        {
            provisionStatus = true;
        }
        else if ("no".equalsIgnoreCase(status))
        {
            provisionStatus = false;
        }
        else
        {
            ctx.response().setStatusCode(400).end("Bad Request: Status must be either 'yes' or 'no'");

            return;
        }

        String query = "UPDATE discovery SET provision = " + provisionStatus +
                " WHERE discovery_profile_name = '" + discoveryProfileName + "' AND discovery = true";

        JsonObject request = new JsonObject().put("query", query);

        eventBus.request("database.query.execute", request, reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(200).end("Provision status updated successfully");
            }
            else
            {
                logger.error("[{}] Failed to update provision status: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end("Internal Server Error");
            }
        });
    }

    public void getProvisionData(String discoveryProfileName, RoutingContext ctx) {
        String query = "SELECT s.id, s.system_name, s.system_description, s.system_location, " +
                "s.system_object_id, s.system_uptime, s.error, " +  // Moved s.error here
                "i.interface_index, i.interface_name, i.interface_alias, " +
                "i.interface_operational_status, i.interface_admin_status, " +
                "i.interface_description, i.interface_sent_error_packet, " +
                "i.interface_received_error_packet, i.interface_sent_octets, " +
                "i.interface_received_octets, i.interface_speed, i.interface_physical_address, " +
                "i.interface_discard_packets, i.interface_in_packets, i.interface_out_packets " +
                "FROM snmp s " +
                "LEFT JOIN snmp_interface i ON s.id = i.snmp_id " +
                "WHERE s.discovery_profile_name = '" + discoveryProfileName.replace("'", "''") + "'";


        JsonObject request = new JsonObject().put("query", query);

        eventBus.request("database.query.execute", request, reply -> {
            if (reply.succeeded()) {
                Object body = reply.result().body();

                if (!(body instanceof JsonObject)) {
                    ctx.response().setStatusCode(500).end("Unexpected response format from database service");
                    return;
                }

                JsonObject resultObject = (JsonObject) body;
                JsonArray results = resultObject.getJsonArray("data");

                if (results == null || results.isEmpty()) {
                    ctx.response().setStatusCode(404).end(new JsonObject().put("message", "No data found for discoveryProfileName: " + discoveryProfileName).encode());
                    return;
                }

                Map<Integer, JsonObject> snmpMap = new HashMap<>();

                for (int i = 0; i < results.size(); i++) {
                    JsonObject row = results.getJsonObject(i);
                    Integer snmpId = row.getInteger("id");

                    JsonObject snmpData = snmpMap.computeIfAbsent(snmpId, k -> new JsonObject()
                            .put("system.name", row.getString("system_name"))
                            .put("system.description", row.getString("system_description"))
                            .put("system.location", row.getString("system_location"))
                            .put("system.objectId", row.getString("system_object_id"))
                            .put("system.uptime", row.getString("system_uptime"))
                            .put("error",row.getString("error"))
                            .put("system.interfaces", new JsonArray()));

                    Integer interfaceIndex = row.getInteger("interface_index");
                    if (interfaceIndex != null) {
                        JsonObject interfaceData = new JsonObject()
                                .put("interface.index", interfaceIndex)
                                .put("interface.name", row.getString("interface_name"))
                                .put("interface.alias", row.getString("interface_alias"))
                                .put("interface.operational.status", row.getString("interface_operational_status"))
                                .put("interface.admin.status", row.getString("interface_admin_status"))
                                .put("interface.description", row.getString("interface_description"))
                                .put("interface.sent.error.packet", row.getLong("interface_sent_error_packet"))
                                .put("interface.received.error.packet", row.getLong("interface_received_error_packet"))
                                .put("interface.sent.octets", row.getLong("interface_sent_octets"))
                                .put("interface.received.octets", row.getLong("interface_received_octets"))
                                .put("interface.speed", row.getLong("interface_speed"))
                                .put("interface.physical.address", row.getString("interface_physical_address"))
                                .put("interface.discard.packets", row.getLong("interface_discard_packets"))
                                .put("interface.in.packets", row.getLong("interface_in_packets"))
                                .put("interface.out.packets", row.getLong("interface_out_packets"));

                        snmpData.getJsonArray("system.interfaces").add(interfaceData);
                    }
                }

                JsonObject response = new JsonObject().put("snmp", new JsonArray(new ArrayList<>(snmpMap.values())));
                ctx.response().setStatusCode(200).end(response.encode());
            } else {
                logger.error("[{}] Failed to fetch provision data: {}", Thread.currentThread().getName(), reply.cause().getMessage());
                ctx.response().setStatusCode(500).end(new JsonObject().put("message", "Internal Server Error").encode());
            }
        });
    }




}
