package io.vertx.nms.util;

import java.util.Set;

public class Constants
{
    public static final String DB_HOST = "localhost";

    public static final int DB_PORT = 5432;

    public static final String DB_NAME = "NMS_Lite";

    public static final String DB_USER = "admin";

    public static final String DB_PASSWORD = "admin";

    public static final String ZMQ_ADDRESS = "tcp://localhost:5555";


    public static final String DATABASE_TABLE_DISCOVERY_PROFILE = "discovery_profiles";

    public static final String DATABASE_TABLE_CREDENTIAL_PROFILE = "credential_profile";

    public static final String DATABASE_TABLE_PROVISION_DATA = "provision_data";

    public static final String DATABASE_TABLE_PROVISIONING_JOBS = "provisioning_jobs";

    public static final String DATABASE_ALL_COLUMN = "*";

    public static final String DATABASE_CREDENTIAL_PROFILE_NAME = "credential_profile_name";

    public static final String DATABASE_DISCOVERY_PROFILE_NAME = "discovery_profile_name";

    public static final String DATABASE_CREDENTIAL_PROFILE_ID = "credential_profile_id";


    public static final String EVENTBUS_DATABASE_ADDRESS = "database.query.execute";

    public static final String EVENTBUS_ZMQ_ADDRESS = "zmq.send";


    public static final String CONDITION = "condition";

    public static final String WITH = "with";

    public static final String DATA = "data";

    public static final String ID = "id";

    public static final String SELECT = "select";

    public static final String UPDATE = "update";

    public static final String INSERT = "insert";

    public static final String DELETE = "delete";

    public static final String POLLED_AT = "polled_at";

    public static final String QUERY = "query";

    public static final String PARAMS = "params";

    public static final String SYSTEM_TYPE = "system_type";

    public static final String CREDENTIALS = "credentials";

    public static final String COMMUNITY = "community";

    public static final String VERSION = "version";

    public static final String IP = "ip";

    public static final String PUT = "put";

    public static final String PLUGIN_TYPE = "pluginType";

    public static final String REQUEST_TYPE = "requestType";

    public static final String DISCOVERY = "discovery";

    public static final String SUCCESS = "success";

    public static final String STATUS = "status";

    public static final String MESSAGE = "message";

    public static final String POLLING = "polling";

    public static final String TABLE_NAME = "tableName";

    public static final String OPERATION = "operation";

    public static final String COLUMNS = "columns";

    public static final String DISCOVERY_PROFILE_ID = "discoveryProfileId";

    public static final String SNMP = "snmp";

    public static final String FAIL = "fail";

    public static final String JOB_ID = "job_id";


    public static final String MESSAGE_EMPTY_REQUEST = "Bad request: Empty message body";

    public static final String MESSAGE_INVALID_JSON = "Bad Request: Invalid JSON";

    public static final String MESSAGE_REQUIRED_BODY  = "Request body is required.";

    public static final String MESSAGE_REQUIRED_DISCOVERY_PROFILE_ID = " DiscoveryProfileId is required";

    public static final String MESSAGE_INVALID_PROFILE_ID = "Invalid profileId. It must be a numeric value.";

    public static final String MESSAGE_INTERNAL_SERVER_ERROR = "Internal Server Error.";

    public static final String MESSAGE_QUERY_SUCCESSFUL = "Query executed successfully";

    public static final String MESSAGE_ZMQ_NO_RESPONSE = "No response from ZMQ server";

    public static final String MESSAGE_BAD_REQUEST = "Bad Request";

    public static final String MESSAGE_CREDENTIAL_IN_USE = "Credential profile is in use cant delete";

    public static final String MESSAGE_IP_NOT_DISCOVERED = "IP not discovered.";

    public static final String MESSAGE_NULL_CREDENTIAL_ID = "credential profile id is null";

    public static final String MESSAGE_POLLING_STARTED = "Polling already started";

    public static final String MESSAGE_JOB_NOT_FOUND = "Provisioning job not found.";


    public static final Set<String> REQUIRED_FIELDS_CREDENTIAL = Set.of(DATABASE_CREDENTIAL_PROFILE_NAME, SYSTEM_TYPE, CREDENTIALS);

    public static final Set<String> REQUIRED_FIELDS_DISCOVERY = Set.of(DATABASE_DISCOVERY_PROFILE_NAME, IP, DATABASE_CREDENTIAL_PROFILE_ID);

    public static final String MESSAGE_NOT_FOUND = "Not found";

    public static final String DATABASE_IN_USE_BY = "in_use_by";

}
