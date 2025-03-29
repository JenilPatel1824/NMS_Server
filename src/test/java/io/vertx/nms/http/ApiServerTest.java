package io.vertx.nms.http;

import com.github.javafaker.Faker;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxTestContext;
import io.vertx.junit5.VertxExtension;
import io.vertx.nms.database.Database;
import io.vertx.nms.messaging.ZmqMessenger;
import io.vertx.nms.polling.PollingIntegrationTest;
import io.vertx.nms.util.Constants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(VertxExtension.class)
class ApiServerTest
{
    private static Vertx vertx;

    private static WebClient webClient;

    static Process goProcess;

    private final Faker faker = new Faker();

    private static final Logger logger = LoggerFactory.getLogger(ApiServerTest.class);

    @BeforeAll
    static void setup(VertxTestContext testContext)
    {
        try
        {
            try
            {
                var killPb = new ProcessBuilder("pkill", "-f", "go_plugin");

                killPb.start().waitFor();

                logger.info("Killed existing go_plugin if any.");
            }

            catch (Exception e)
            {
                logger.warn("No existing go_plugin found or failed to kill: {}", e.getMessage());
            }

            var projectDir = System.getProperty("user.dir");

            var goPlugin = new File(projectDir + "/go_executable/go_plugin");

            if (!goPlugin.exists())
            {
                logger.error("go_plugin not found at: {}", goPlugin.getAbsolutePath());

                System.exit(1);
            }

            if (!goPlugin.canExecute())
            {
                logger.error("go_plugin is not executable. Please run: chmod +x {}", goPlugin.getAbsolutePath());

                System.exit(1);
            }

            var pb = new ProcessBuilder(goPlugin.getAbsolutePath());

            goProcess = pb.start();

            logger.info("go_plugin started successfully.");

        }
        catch (Exception e)
        {
            logger.error("Failed to start go_plugin: {}", e.getMessage());

            System.exit(1);
        }

        vertx = Vertx.vertx();

        webClient = WebClient.create(vertx);

        vertx.deployVerticle(new ApiServer())
                .compose(id -> vertx.deployVerticle(new Database()))
                .compose(id -> vertx.deployVerticle(new ZmqMessenger()))
                .onSuccess(id -> testContext.completeNow())
                .onFailure(testContext::failNow);
    }

    @AfterAll
    static void tearDown(VertxTestContext testContext)
    {
        if (goProcess != null && goProcess.isAlive())
        {
            goProcess.destroy();

            logger.info("go_process destroyed.");
        }

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
        var testProfileId = "1";

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
        var testProfileId = "1";

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
                .put(Constants.DATABASE_CREDENTIAL_PROFILE_ID, 1)
                .put("ip", faker.internet().ipV4Address())
                .put(Constants.PORT,161);

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
        var testProfileId = "1";

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
        var testProfileId = "1";

        var updatedRequestBody = new JsonObject()
                .put("discovery_profile_name", faker.name().firstName())
                .put("ip","10.20.40.233")
                .put(Constants.PORT,161)
                .put(Constants.DATABASE_CREDENTIAL_PROFILE_ID,1);

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
        var discoveryProfileId = "97";

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
        var discoveryId = "2";

        webClient.post(8080, "localhost", "/provision"  + "/" +discoveryId)
                .send( response ->
                {
                    assertEquals(409, response.result().statusCode(), "Expected status code 400");

                    testContext.completeNow();
                });
    }

    // Test Case for GET polled data
    @Test
    void testGetProvisionData(VertxTestContext testContext)
    {
        var jobId = "1";

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
}
