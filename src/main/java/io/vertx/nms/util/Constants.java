package io.vertx.nms.util;

import zmq.io.coder.IDecoder;

import javax.swing.plaf.PanelUI;

//todo: _ -->  variable _ remove , use constants in go, change some names


public class Constants
{
    public static final String DB_HOST = "localhost";

    public static final int DB_PORT = 5432;

    public static final String DB_NAME = "NMS_Lite";

    public static final String DB_USER = "admin";

    public static final String DB_PASSWORD = "admin";

    public static final String ZMQ_ADDRESS = "tcp://localhost:5555";

    public static final String EVENTBUS_DATABASE_ADDRESS = "database.query.execute";

    public static final String EVENTBUS_ZMQ_ADDRESS = "zmq.send";

    public static final String DATABASE_TABLE_DISCOVERY_PROFILE = "discovery_profiles";

    public static final String DATABASE_TABLE_CREDENTIAL_PROFILE = "credential_profile";

    public static final String DATABASE_TABLE_PROVISION_DATA = "provision_data";

    public static final String DATABASE_OPERATION_SELECT = "select";

    public static final String DATABASE_OPERATION_UPDATE = "update";

    public static final String DATABASE_OPERATION_INSERT = "insert";

    public static final String DATABASE_OPERATION_DELETE = "delete";

    public static final String DATABASE_ALL_COLUMN = "*";

    public static final String CONDITION = "condition";

    public static final String DATA = "data";

    public static final String DATABASE_CREDENTIAL_PROFILE_NAME = "credential_profile_name";

    public static final String DATABASE_DISCOVERY_PROFILE_NAME = "discovery_profile_name";

    public static final String QUERY = "query";

    public static final String PARAMS = "params";

    public static final String SYSTEM_TYPE = "system_type";

    public static final String CREDENTIALS = "credentials";

    public static final String COMMUNITY = "community";

    public static final String VERSION = "version";

    public static final String IP = "ip";

    public static final String PLUGIN_TYPE = "pluginType";

    public static final String REQUEST_TYPE = "requestType";

    public static final String DISCOVERY = "discovery";

    public static final String SUCCESS = "success";

    public static final String STATUS = "status";

    public static final String DATABASE_COLUMN_POLLED_AT = "polled_at";

    public static final String MESSAGE = "message";

    public static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal Server Error";

    public static final String POLLING = "polling";

    public static final String TABLE_NAME = "tableName";

    public static final String OPERATION = "operation";

    public static final String COLUMNS = "columns";

    public static final String DISCOVERY_PROFILE_NAME = "discoveryProfileName";

    public static final String BAD_REQUEST_INVALID_JSON = "Bad Request: Invalid JSON";

    public static final String HTTP_REQUIRED_BODY  = "Request body is required.";

    public static final String MESSAGE_REQUIRED_DISCOVERY_PROFILE_NAME = " DiscoveryProfileName is required";

    public static final String HTTP_EMPTY_REQUEST = "Bad request: Empty message body";

}
