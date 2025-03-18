package io.vertx.nms.util;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.nms.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.regex.Pattern;

public class Util
{
    private static final Logger logger = LoggerFactory.getLogger(Util.class);

    private static final String MISSING_REQUIRED_FIELD = "Missing or empty required field: ";

    // Validates the request body based on the table name.
    // Ensures that all required fields are present and not empty.
    // Ensures that the 'ip' field is not updated in discovery_profiles.
    // Ensures that 'credential.community' and 'credential.version' are present for system_type 'snmp'.
    // @param requestBody The JSON object containing the request body.
    // @param context The routing context containing the request details.
    public static boolean isValidRequest(JsonObject requestBody, RoutingContext context)
    {
        var tableName = getTableNameFromContext(context);

        if (tableName == null)
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_BAD_REQUEST);

            return false;
        }

        var requiredFields = getRequiredFieldsForTable(tableName);

        for (var field : requiredFields)
        {
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

            if (Constants.IP.equals(field))
            {
                var ip = requestBody.getString(Constants.IP, "").trim();

                if (!isValidIpv4(ip))
                {
                    context.response().setStatusCode(400).end("Invalid IPv4 format for field 'ip'");

                    return false;
                }
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
                    if (credentialJson.isEmpty())
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

    // function to validate IPv4
    // @param ip is ip for validation
    private static boolean isValidIpv4(String ip)
    {
        var ipv4Regex = "^((25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)$";

        return Pattern.compile(ipv4Regex).matcher(ip).matches();
    }

    // Returns the table name based on the request path.
    // @param context The routing context containing the request details.
    public static String getTableNameFromContext(RoutingContext context)
    {
        String path = context.request().path();

        if (path.startsWith("/credential")) return Constants.DATABASE_TABLE_CREDENTIAL_PROFILE;

        if (path.startsWith("/discovery")) return Constants.DATABASE_TABLE_DISCOVERY_PROFILE;

        if (path.startsWith("/provision")) return Constants.DATABASE_TABLE_PROVISIONING_JOBS;

        return null;
    }

    // Returns the required fields for the given table.
    // @param tableName The name of the table.
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
