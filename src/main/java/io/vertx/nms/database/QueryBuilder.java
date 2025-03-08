package io.vertx.nms.database;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.nms.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class QueryBuilder
{
    public static class QueryResult
    {
        private final String query;

        private final JsonArray params;

        public QueryResult(String query, JsonArray params)
        {
            this.query = query;

            this.params = params;
        }

        public String getQuery()
        {
            return query;
        }

        public JsonArray getParams()
        {
            return params;
        }
    }

// Builds an SQL query string and parameters based on the requested operation.
// @param request JSON object containing operation type, table name, columns, data, and conditions.
// @return QueryResult containing the constructed query string and parameters.
    public static QueryResult buildQuery(JsonObject request)
    {
        String operation = request.getString(Constants.OPERATION, "").toLowerCase();

        String tableName = request.getString(Constants.TABLE_NAME);

        JsonArray columns = request.getJsonArray(Constants.COLUMNS, new JsonArray());

        JsonObject data = request.getJsonObject(Constants.DATA, new JsonObject());

        JsonObject condition = request.getJsonObject(Constants.CONDITION, new JsonObject());

        StringBuilder query = new StringBuilder();

        List<Object> parameters = new ArrayList<>();

        AtomicInteger paramIndex = new AtomicInteger(1);

        switch (operation)
        {
            case Constants.DATABASE_OPERATION_SELECT:

                query.append("SELECT ")
                        .append(columns.isEmpty() ? Constants.DATABASE_ALL_COLUMN : String.join(", ", columns.getList()))
                        .append(" FROM ").append(tableName);

                appendCondition(query, condition, parameters, paramIndex);

                break;

            case Constants.DATABASE_OPERATION_INSERT:

                List<String> keys = new ArrayList<>(data.fieldNames());

                String placeholders = keys.stream()
                        .map(k -> "$" + paramIndex.getAndIncrement())
                        .collect(Collectors.joining(", "));

                query.append("INSERT INTO ").append(tableName)
                        .append(" (").append(String.join(", ", keys)).append(") ")
                        .append("VALUES (").append(placeholders).append(")");

                keys.forEach(k -> parameters.add(data.getValue(k)));

                break;

            case Constants.DATABASE_OPERATION_UPDATE:

                query.append("UPDATE ").append(tableName).append(" SET ");

                List<String> setClauses = new ArrayList<>();

                for (String key : data.fieldNames())
                {
                    setClauses.add(key + " = $" + paramIndex.getAndIncrement());

                    parameters.add(data.getValue(key));
                }

                query.append(String.join(", ", setClauses));

                appendCondition(query, condition, parameters, paramIndex);

                break;

            case Constants.DATABASE_OPERATION_DELETE:

                query.append("DELETE FROM ").append(tableName);

                appendCondition(query, condition, parameters, paramIndex);

                break;
        }

        return new QueryResult(query.toString(), new JsonArray(parameters));
    }

// Appends a WHERE clause to the SQL query if conditions exist.
// @param query StringBuilder containing the query being built.
// @param condition JSON object with column-value pairs for filtering.
// @param parameters List to store query parameters for prepared statements.
    private static void appendCondition(StringBuilder query, JsonObject condition, List<Object> parameters, AtomicInteger paramIndex)
    {
        if (!condition.isEmpty())
        {
            query.append(" WHERE ");

            List<String> conditions = new ArrayList<>();

            condition.forEach(entry ->
            {
                conditions.add(entry.getKey() + " = $" + paramIndex.getAndIncrement());

                parameters.add(entry.getValue());
            });

            query.append(String.join(" AND ", conditions));
        }
    }
}
