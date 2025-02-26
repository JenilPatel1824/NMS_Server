package io.vertx.nms.entity;

public class Credential {
    private String id;
    private String credentialProfileName;
    private String community;
    private String version;
    private String systemType;

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCredentialProfileName() {
        return credentialProfileName;
    }

    public void setCredentialProfileName(String credentialProfileName) {
        this.credentialProfileName = credentialProfileName;
    }

    public String getCommunity() {
        return community;
    }

    public void setCommunity(String community) {
        this.community = community;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }
}
