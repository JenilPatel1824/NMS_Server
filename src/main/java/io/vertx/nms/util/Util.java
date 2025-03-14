package io.vertx.nms.util;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.nms.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Set;

public class Util
{
    private static final Logger logger = LoggerFactory.getLogger(Util.class);

    private static final String MISSING_REQUIRED_FIELD = "Missing or empty required field: ";

    /**
     * Validate a JSON request by determining the table name and required fields dynamically.
     *
     * @param requestBody The JSON request body.
     * @param context     The routing context for sending responses.
     * @return true if valid, false if validation fails.
     */
    public static boolean isValidRequest(JsonObject requestBody, RoutingContext context)
    {
        var tableName = getTableNameFromContext(context);

        if (tableName == null)
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_BAD_REQUEST);

            return false;
        }

        var isUpdateRequest = context.request().method().name().equalsIgnoreCase(Constants.PUT);

        logger.info("up: "+isUpdateRequest+ requestBody);

        if (Constants.DATABASE_TABLE_DISCOVERY_PROFILE.equals(tableName))
        {
            if (isUpdateRequest && requestBody.containsKey(Constants.IP))
            {
                context.response().setStatusCode(400).end("Field 'ip' cannot be updated in discovery_profiles");

                return false;
            }
        }

        var requiredFields = getRequiredFieldsForTable(tableName);

        for (var field : requiredFields)
        {
            if (isUpdateRequest && field.equals(Constants.IP))
            {
                continue;
            }
            if (!requestBody.containsKey(field) || requestBody.getValue(field) == null)
            {
                context.response().setStatusCode(400).end(MISSING_REQUIRED_FIELD + field);

                return false;
            }

            var value = requestBody.getValue(field);

            if (value instanceof String && ((String) value).trim().isEmpty())
            {
                context.response().setStatusCode(400).end("Field '" + field + "' cannot be empty");

                return false;
            }

            if (Constants.DATABASE_TABLE_CREDENTIAL_PROFILE.equals(tableName) && Constants.CREDENTIALS.equals(field))
            {
                if (!(value instanceof JsonObject))
                {
                    context.response().setStatusCode(400).end("Field 'credential' must be a valid JSON object");

                    return false;
                }

                var credentialJson = (JsonObject) value;

                var systemType = requestBody.getString(Constants.SYSTEM_TYPE, "").trim();

                if (Constants.SNMP.equalsIgnoreCase(systemType))
                {
                    if (!credentialJson.containsKey(Constants.COMMUNITY) || credentialJson.getValue(Constants.COMMUNITY) == null ||
                            (credentialJson.getValue(Constants.COMMUNITY) instanceof String && ((String) credentialJson.getValue(Constants.COMMUNITY)).trim().isEmpty()))
                    {
                        context.response().setStatusCode(400).end("Field 'credential.community' cannot be null or empty for system_type 'snmp'");

                        return false;
                    }

                    if (!credentialJson.containsKey(Constants.VERSION) || credentialJson.getValue(Constants.VERSION) == null ||
                            (credentialJson.getValue(Constants.VERSION) instanceof String && ((String) credentialJson.getValue(Constants.VERSION)).trim().isEmpty()))
                    {
                        context.response().setStatusCode(400).end("Field 'credential.version' cannot be null or empty for system_type 'snmp'");

                        return false;
                    }
                }
                else
                {
                    if(credentialJson.isEmpty())
                    {
                        context.response().setStatusCode(400).end("Field 'credential' cannot be empty");

                        return false;
                    }
                    for (var key : credentialJson.fieldNames())
                    {
                        var innerValue = credentialJson.getValue(key);

                        if (innerValue == null || (innerValue instanceof String && ((String) innerValue).trim().isEmpty()))
                        {
                            context.response().setStatusCode(400).end("Field 'credential." + key + "' cannot be null or empty");

                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Determines the table name from the request endpoint.
     */
    public static String getTableNameFromContext(RoutingContext context)
    {
        String path = context.request().path();

        if (path.startsWith("/credential")) return Constants.DATABASE_TABLE_CREDENTIAL_PROFILE;

        if (path.startsWith("/discovery")) return Constants.DATABASE_TABLE_DISCOVERY_PROFILE;

        if (path.startsWith("/provision")) return Constants.DATABASE_TABLE_PROVISIONING_JOBS;


        return null;
    }

    /**
     * Returns required fields based on the table name.
     */
    private static Set<String> getRequiredFieldsForTable(String tableName)
    {
        return switch (tableName)
        {
            case Constants.DATABASE_TABLE_CREDENTIAL_PROFILE -> Constants.REQUIRED_FIELDS_CREDENTIAL;

            case Constants.DATABASE_TABLE_DISCOVERY_PROFILE -> Constants.REQUIRED_FIELDS_DISCOVERY;

            default -> Set.of();
        };
    }

    // Pings the given IP address using ping to check its reachability.
    // @param ipAddress The IP address to ping.
    // @return true if the IP is reachable, false otherwise.
    public static boolean ping(String ipAddress)
    {
        try
        {
            var processBuilder = new ProcessBuilder("ping", "-c", "3", ipAddress);

            var process = processBuilder.start();

            var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;

            while ((line = reader.readLine()) != null)
            {

                if (line.contains("100% packet loss"))
                {
                    return false;
                }
                if(line.contains("3 received"))
                {
                    return true;
                }
            }
            process.waitFor();

            return true;
        }
        catch (Exception e)
        {
            logger.error("Failed to build Process");

            return false;
        }
    }
}
