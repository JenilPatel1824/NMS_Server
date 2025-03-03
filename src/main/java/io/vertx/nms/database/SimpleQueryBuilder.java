package io.vertx.nms.database;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SimpleQueryBuilder
{
    public static String buildQuery(JsonObject request)
    {
        String tableName = request.getString("tableName");

        String operation = request.getString("operation").toLowerCase();

        JsonArray columns = request.getJsonArray("columns");

        JsonObject data = request.getJsonObject("data");

        String condition = request.getString("condition");

        StringBuilder query = new StringBuilder(operation + " ");

        if (operation.equals("select"))
        {
            String columnsStr = (columns == null || columns.isEmpty()) ? "*" : String.join(", ", columns.getList());

            query.append(columnsStr).append(" FROM ").append(tableName);

            if (condition != null && !condition.isEmpty())
            {
                query.append(" WHERE ").append(condition);
            }
        }

        else if (operation.equals("insert"))
        {
            StringBuilder columnsPart = new StringBuilder();

            StringBuilder valuesPart = new StringBuilder();

            data.forEach(entry ->
            {
                columnsPart.append(entry.getKey()).append(", ");

                valuesPart.append("'").append(entry.getValue()).append("', ");
            });

            columnsPart.setLength(columnsPart.length() - 2);

            valuesPart.setLength(valuesPart.length() - 2);

            query.append("INTO ").append(tableName).append(" (").append(columnsPart).append(") ").append("VALUES (").append(valuesPart).append(")");
        }

        else if (operation.equals("update"))
        {
            StringBuilder setClause = new StringBuilder();

            data.forEach(entry ->
            {
                setClause.append(entry.getKey()).append(" = '").append(entry.getValue()).append("', ");
            });

            setClause.setLength(setClause.length() - 2);

            query.append(tableName).append(" SET ").append(setClause);

            if (condition != null && !condition.isEmpty())
            {
                query.append(" WHERE ").append(condition);
            }
        }

        else if (operation.equals("delete"))
        {
            query.append("FROM ").append(tableName);

            if (condition != null && !condition.isEmpty()) {
                query.append(" WHERE ").append(condition);
            }
        }
        query.append(";");

        return query.toString();
    }
}

