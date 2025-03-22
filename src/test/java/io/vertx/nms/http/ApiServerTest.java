package io.vertx.nms.http;

import com.github.javafaker.Faker;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxTestContext;
import io.vertx.junit5.VertxExtension;
import io.vertx.nms.ApiServer;
import io.vertx.nms.database.Database;
import io.vertx.nms.messaging.ZmqMessenger;
import io.vertx.nms.util.Constants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(VertxExtension.class)
class ApiServerTest
{
    private static Vertx vertx;

    private static WebClient webClient;

    private final Faker faker = new Faker();

    @BeforeAll
    static void setup(VertxTestContext testContext)
    {
        vertx = Vertx.vertx();

        webClient = WebClient.create(vertx);

        vertx.deployVerticle(new ApiServer(vertx))
                .compose(id -> vertx.deployVerticle(new Database()))
                .compose(id -> vertx.deployVerticle(new ZmqMessenger()))
                .onSuccess(id -> testContext.completeNow())
                .onFailure(testContext::failNow);
    }

    @AfterAll
    static void tearDown(VertxTestContext testContext)
    {
        vertx.close(testContext.succeeding(v -> testContext.completeNow()));
    }

    // Test Case for creating a credential profile
    @Test
    void testCredentialProfileCreate(VertxTestContext testContext)
    {
        var requestBody = new JsonObject()
                .put("credential_profile_name", faker.name().firstName())
                .put("system_type", "snmp")
                .put("credentials", new JsonObject().put("community", "public").put("version", "2c"));

        webClient.post(8080, "localhost", "/credential/")
                .sendJsonObject(requestBody, response ->
                {
                    assertEquals(201, response.result().statusCode(), "Expected status code 201");

                    JsonObject responseBody = response.result().bodyAsJsonObject();

                    assertTrue(responseBody.containsKey("id"), "Response should contain an ID");

                    testContext.completeNow();
                });
    }

    //Test case for getting a credential profile
    @Test
    void testCredentialProfileGet(VertxTestContext testContext)
    {
        var testProfileId = "67";

        webClient.get(8080, "localhost", "/credential/" + testProfileId)
                .send(response ->
                {
                    assertEquals(200, response.result().statusCode(), "Expected status code 200");

                    JsonObject responseBody = response.result().bodyAsJsonObject();

                    assertEquals(testProfileId, responseBody.getJsonArray("data").getJsonObject(0).getString("id"), "Expected profile ID to match");

                    testContext.completeNow();
                });
    }

    // Test Case for updating a credential profile
    @Test
    void testCredentialProfileUpdate(VertxTestContext testContext)
    {
        var testProfileId = "67";

        var updatedRequestBody = new JsonObject()
                .put("credential_profile_name", faker.lorem().word())
                .put("system_type", "snmp")
                .put("credentials", new JsonObject().put("community", "public").put("version", "2c"));

        webClient.put(8080, "localhost", "/credential/" + testProfileId)
                .sendJsonObject(updatedRequestBody, response ->
                {
                    assertEquals(200, response.result().statusCode(), "Expected status code 200");

                    testContext.completeNow();
                });
    }

    // Test Case for creating a discovery profile
    @Test
    void testDiscoveryProfileCreate(VertxTestContext testContext)
    {
        var requestBody = new JsonObject()
                .put("discovery_profile_name", faker.name().firstName())
                .put(Constants.DATABASE_CREDENTIAL_PROFILE_ID, 67)
                .put("ip", faker.internet().ipV4Address());

        webClient.post(8080, "localhost", "/discovery/")
                .sendJsonObject(requestBody, response ->
                {
                    assertEquals(201, response.result().statusCode(), "Expected status code 201");

                    var responseBody = response.result().bodyAsJsonObject();

                    assertTrue(responseBody.containsKey("id"), "Response should contain an ID");

                    testContext.completeNow();
                });
    }

    // Test Case for getting a discovery profile
    @Test
    void testDiscoveryProfileGet(VertxTestContext testContext)
    {
        var testProfileId = "210129";

        webClient.get(8080, "localhost", "/discovery/" + testProfileId)
                .send(response ->
                {
                    assertEquals(200, response.result().statusCode(), "Expected status code 200");

                    var responseBody = response.result().bodyAsJsonObject();

                    assertEquals(testProfileId, responseBody.getJsonArray("data").getJsonObject(0).getString("id"), "Expected discovery profile ID to match");

                    testContext.completeNow();
                });
    }

    // Test Case for updating a discovery profile
    @Test
    void testDiscoveryProfileUpdate(VertxTestContext testContext)
    {
        var testProfileId = "210129";

        var updatedRequestBody = new JsonObject()
                .put("discovery_profile_name", faker.name().firstName())
                .put("ip","172.16.12.211")
                .put(Constants.DATABASE_CREDENTIAL_PROFILE_ID,67);

        webClient.put(8080, "localhost", "/discovery/" + testProfileId)
                .sendJsonObject(updatedRequestBody, response ->
                {
                    assertEquals(200, response.result().statusCode(), "Expected status code 200");

                    JsonObject responseBody = response.result().bodyAsJsonObject();

                    testContext.completeNow();
                });
    }

    //Test case for running a discovery
    @Test
    void testDiscoveryRun(VertxTestContext testContext)
    {
        var discoveryProfileId = "210210";

        webClient.post(8080, "localhost", "/discovery/" + discoveryProfileId + "/run")
                .send(response ->
                {
                    assertEquals(200, response.result().statusCode(), "Expected status code 200");

                    testContext.completeNow();
                });
    }

    //Test case for updating provision status
    @Test
    void testUpdateProvisionStatus(VertxTestContext testContext)
    {
        var discoveryId = "210128";

        webClient.post(8080, "localhost", "/provision/start"  + "/" +discoveryId)
                .send( response ->
                {
                    assertEquals(400, response.result().statusCode(), "Expected status code 400");

                    testContext.completeNow();
                });
    }

    // Test Case for GET polled data
    @Test
    void testGetProvisionData(VertxTestContext testContext)
    {
        var jobId = "10152";

        webClient.get(8080, "localhost", "/provision/data/" + jobId)
                .send(response ->
                {
                    assertEquals(200, response.result().statusCode(), "Expected status code 200");

                    var responseBody = response.result().bodyAsJsonObject();

                    assertNotNull(responseBody, "Response body should not be null");

                    testContext.completeNow();
                });
    }

    //Test Case for GET /toperror (Retrieve Interfaces with Errors)
    @Test
    void testGetInterfacesByError(VertxTestContext testContext)
    {
        webClient.get(8080, "localhost", "/provision/topError")
                .send(response ->
                {
                    assertEquals(200, response.result().statusCode(), "Expected status code 200");

                    var responseBody = response.result().bodyAsJsonObject();

                    assertNotNull(responseBody, "Response body should not be null");

                    testContext.completeNow();
                });
    }

    // Test Case for GET /topspeed (Retrieve Interfaces by Speed)
    @Test
    void testGetInterfacesBySpeed(VertxTestContext testContext)
    {
        webClient.get(8080, "localhost", "/provision/topSpeed")
                .send(response ->
                {
                    assertEquals(200, response.result().statusCode(), "Expected status code 200");

                    var responseBody = response.result().bodyAsJsonObject();

                    assertNotNull(responseBody, "Response body should not be null");

                    testContext.completeNow();
                });
    }

    // Test Case for GET /toprestarts (Retrieve Interfaces by restarts)
    @Test
    void testGetInterfacesByUptime(VertxTestContext testContext)
    {
        webClient.get(8080, "localhost", "/provision/topRestarts")
                .send(response ->
                {
                    assertEquals(200, response.result().statusCode(), "Expected status code 200");

                    var responseBody = response.result().bodyAsJsonObject();

                    assertNotNull(responseBody, "Response body should not be null");

                    testContext.completeNow();
                });
    }
}
