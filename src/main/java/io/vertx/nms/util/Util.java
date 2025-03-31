package io.vertx.nms.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util
{
    private static final Logger logger = LoggerFactory.getLogger(Util.class);

    private static final String MISSING_REQUIRED_FIELD = "Missing or empty required field: ";

    private static final String IPV4_REGX = "^((25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)$";

    private static final String INSERT_TABLE_NAME_REGEX = "insert\\s+into\\s+((\"[^\"]+\"|[^\\s(]+))";

    private static final String UPDATE_TABLE_NAME_REGEX = "update\\s+((\"[^\"]+\"|[^\\s]+))";

    private static final String DELETE_TABLE_NAME_REGEX = "delete\\s+from\\s+((\"[^\"]+\"|[^\\s]+))";

    private static final String PARSE_TABLE_REGX = "(?:from|join)\\s+([^\\s,)(]+)";

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

        for (var field : getRequiredFieldsForTable(tableName))
        {
            if (!requestBody.containsKey(field) || requestBody.getValue(field) == null)
            {
                context.response().setStatusCode(400).end(new JsonObject().put(Constants.STATUS,Constants.FAIL).put(Constants.MESSAGE,MISSING_REQUIRED_FIELD + field).encode());

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
                if (!isValidIpv4(requestBody.getString(Constants.IP, "").trim()))
                {
                    context.response().setStatusCode(400).end("Invalid IPv4 format for field 'ip'");

                    return false;
                }
            }

            if (Constants.PORT.equals(field))
            {
                if (!(value instanceof Integer))
                {
                    context.response().setStatusCode(400).end("Field 'port' must be an integer");

                    return false;
                }

                int port = (Integer) value;

                if (port <= 0 || port > 65535)
                {
                    context.response().setStatusCode(400).end("Field 'port' must be between 1 and 65535");

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

                if (Constants.SNMP.equalsIgnoreCase(requestBody.getString(Constants.SYSTEM_TYPE, "").trim()))
                {
                    if (!credentialJson.containsKey(Constants.COMMUNITY) || credentialJson.getValue(Constants.COMMUNITY) == null || (credentialJson.getValue(Constants.COMMUNITY) instanceof String && ((String) credentialJson.getValue(Constants.COMMUNITY)).trim().isEmpty()))
                    {
                        context.response().setStatusCode(400).end("Field 'credential.community' cannot be null or empty for system_type 'snmp'");

                        return false;
                    }

                    if (!credentialJson.containsKey(Constants.VERSION) || credentialJson.getValue(Constants.VERSION) == null || (credentialJson.getValue(Constants.VERSION) instanceof String && ((String) credentialJson.getValue(Constants.VERSION)).trim().isEmpty()))
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
                        if (credentialJson.getValue(key) == null || (credentialJson.getValue(key) instanceof String && ((String) credentialJson.getValue(key)).trim().isEmpty()))
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
        return Pattern.compile(IPV4_REGX).matcher(ip).matches();
    }

    // Returns the table name based on the request path.
    // @param context The routing context containing the request details.
    public static String getTableNameFromContext(RoutingContext context)
    {
        var path = context.request().path();

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
            var process = new ProcessBuilder("ping", "-c", "3", ipAddress).start();

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

    // Generates a cache key by hashing the query and parameters.
    // @param query  The SQL query string.
    // @param params The parameters used in the query as a JsonArray.
    // @return A SHA-1 hashed string representing the cache key.
    public static String generateCacheKey(String query, JsonArray params)
    {
        return DigestUtils.sha1Hex(query + params.toString());
    }

    // Extracts table names from an INSERT, UPDATE, or DELETE query using regex pattern matching.
    // @param query The SQL mutation query (INSERT, UPDATE, or DELETE).
    // @return A set of table names affected by the mutation.
    public static Set<String> parseTablesForMutation(String query)
    {
        var tables = new HashSet<String>();

        Matcher matcher;

        if (query.startsWith(Constants.INSERT))
        {
            var insertPattern = Pattern.compile(INSERT_TABLE_NAME_REGEX, Pattern.CASE_INSENSITIVE);

            matcher = insertPattern.matcher(query);

            if (matcher.find())
            {
                var table = matcher.group(1).replaceAll("\"", "");

                if (table.contains("."))
                {
                    table = table.substring(table.lastIndexOf('.') + 1);
                }

                tables.add(table);
            }
        }
        else if (query.startsWith(Constants.UPDATE))
        {
            matcher = Pattern.compile(UPDATE_TABLE_NAME_REGEX, Pattern.CASE_INSENSITIVE).matcher(query);

            if (matcher.find())
            {
                var table = matcher.group(1).replaceAll("\"", "");

                if (table.contains("."))
                {
                    table = table.substring(table.lastIndexOf('.') + 1);
                }

                tables.add(table);
            }
        }
        else if (query.startsWith(Constants.DELETE))
        {
            matcher = Pattern.compile(DELETE_TABLE_NAME_REGEX, Pattern.CASE_INSENSITIVE).matcher(query);

            if (matcher.find())
            {
                var table = matcher.group(1).replaceAll("\"", "");

                if (table.contains("."))
                {
                    table = table.substring(table.lastIndexOf('.') + 1);
                }

                tables.add(table);
            }
        }

        return tables;
    }

    // Extracts table names from a SELECT query using regex pattern matching.
    // @param query The SQL SELECT query string.
    // @return A set of table names found in the query.
    public static Set<String> parseTablesForSelect(String query)
    {
        var tables = new HashSet<String>();

        var matcher = Pattern.compile(PARSE_TABLE_REGX, Pattern.CASE_INSENSITIVE).matcher(query.toLowerCase());

        while (matcher.find())
        {
            var table = matcher.group(1).replaceAll("\"", "");

            if (table.contains("."))
            {
                table = table.substring(table.lastIndexOf('.') + 1);
            }

            tables.add(table);
        }
        return tables;
    }
}