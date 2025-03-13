import com.github.javafaker.Faker;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxTestContext;
import io.vertx.junit5.VertxExtension;
import io.vertx.nms.Main;
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

        vertx.deployVerticle(new Main(), testContext.succeeding(id -> testContext.completeNow()));
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

    @Test
    void testCredentialProfileGet(VertxTestContext testContext)
    {
        var testProfileId = "3";

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
        var testProfileId = "3";

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
                .put("credential_profile_id", 3)
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
        var testProfileId = "200089";

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
        var testProfileId = "200089";

        var updatedRequestBody = new JsonObject()
                .put("discovery_profile_name", faker.name().firstName())
                .put("ip", "192.168.228.223")
                .put("credential_profile_id",3);

        webClient.put(8080, "localhost", "/discovery/" + testProfileId)
                .sendJsonObject(updatedRequestBody, response ->
                {
                    assertEquals(200, response.result().statusCode(), "Expected status code 200");

                    JsonObject responseBody = response.result().bodyAsJsonObject();

                    testContext.completeNow();
                });
    }

    @Test
    void testUpdateProvisionStatus(VertxTestContext testContext)
    {
        var monitorId = "61";

        var status = "yes";

        var requestBody = new JsonObject()
                .put("status", status);

        webClient.post(8080, "localhost", "/provision/" + monitorId + "/" + status)
                .sendJsonObject(requestBody, response ->
                {
                    assertEquals(200, response.result().statusCode(), "Expected status code 200");

                    testContext.completeNow();
                });
    }

    // Test Case for GET /data/:discoveryProfileId (Retrieve Provision Data)
    @Test
    void testGetProvisionData(VertxTestContext testContext)
    {
        var discoveryProfileId = "61";

        webClient.get(8080, "localhost", "/provision/data/" + discoveryProfileId)
                .send(response ->
                {
                    assertEquals(200, response.result().statusCode(), "Expected status code 200");

                    var responseBody = response.result().bodyAsJsonObject();

                    assertNotNull(responseBody, "Response body should not be null");

                    testContext.completeNow();
                });
    }

    //Test Case for GET /top/error (Retrieve Interfaces with Errors)
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

    // Test Case for GET /top/speed (Retrieve Interfaces by Speed)
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

    // Test Case for GET /top/uptime (Retrieve Interfaces by Uptime)
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

    @Test
    void testDiscoveryRun(VertxTestContext testContext)
    {
        var discoveryProfileId = "200089";

        webClient.post(8080, "localhost", "/discovery/" + discoveryProfileId + "/run")
                .send(response ->
                {
                    assertEquals(200, response.result().statusCode(), "Expected status code 200");

                    testContext.completeNow();
                });
    }
}
