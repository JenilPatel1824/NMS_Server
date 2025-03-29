package io.vertx.nms.http;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.nms.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class QueryBuilder
{
    public record QueryResult(String query, JsonArray params) { }

    // Builds an SQL query string and parameters based on the requested operation.
    // @param request JSON object containing operation type, table name, columns, data, and conditions.
    // @return QueryResult containing the constructed query string and parameters.
    public static QueryResult buildQuery(JsonObject request,StringBuilder query)
    {
        var tableName = request.getString(Constants.TABLE_NAME);

        var data = request.getJsonObject(Constants.DATA, new JsonObject());

        var condition = request.getJsonObject(Constants.CONDITION, new JsonObject());

        var parameters = new ArrayList<>();

        var paramIndex = new AtomicInteger(1);

        switch (request.getString(Constants.OPERATION).toLowerCase())
        {
            case Constants.SELECT:

                query.append("SELECT ").append(request.getJsonArray(Constants.COLUMNS, new JsonArray()).isEmpty() ? Constants.DATABASE_ALL_COLUMN : String.join(", ", request.getJsonArray(Constants.COLUMNS, new JsonArray()).getList())).append(" FROM ").append(tableName);

                appendCondition(query, condition, parameters, paramIndex);

                break;

            case Constants.INSERT:

                var keys = new ArrayList<>(data.fieldNames());

                var placeholders = keys.stream().map(k -> "$" + paramIndex.getAndIncrement()).collect(Collectors.joining(", "));

                query.append("INSERT INTO ").append(tableName).append(" (").append(String.join(", ", keys)).append(") ").append("VALUES (").append(placeholders).append(")");

                keys.forEach(k -> parameters.add(data.getValue(k)));

                break;

            case Constants.UPDATE:

                query.append("UPDATE ").append(tableName).append(" SET ");

                var setClauses = new ArrayList<String>();

                for (var key : data.fieldNames())
                {
                    setClauses.add(key + " = $" + paramIndex.getAndIncrement());

                    parameters.add(data.getValue(key));
                }

                query.append(String.join(", ", setClauses));

                appendCondition(query, condition, parameters, paramIndex);

                break;

            case Constants.DELETE:

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

            var conditions = new ArrayList<String>();

            condition.forEach(entry ->
            {
                conditions.add(entry.getKey() + " = $" + paramIndex.getAndIncrement());

                parameters.add(entry.getValue());
            });

            query.append(String.join(" AND ", conditions));
        }
    }
}
