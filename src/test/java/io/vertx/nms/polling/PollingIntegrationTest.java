package io.vertx.nms.polling;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.nms.messaging.ZmqMessenger;
import io.vertx.nms.util.Constants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
public class PollingIntegrationTest
{
    private static final int BATCH_SIZE = 15;

    private static final Logger logger = LoggerFactory.getLogger(PollingIntegrationTest.class);

    static Process goProcess;

    @BeforeEach
    void deployVerticles(Vertx vertx, VertxTestContext testContext)
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

        vertx.deployVerticle(new PollingScheduler())
                .compose(id -> vertx.deployVerticle(new PollingProcessor()))
                .compose(zmqId -> vertx.deployVerticle( new ZmqMessenger()))
                .onComplete(testContext.succeedingThenComplete());
    }

    // To destroy go process
    @AfterAll
    static void tearDown() {
        if (goProcess != null && goProcess.isAlive())
        {
            goProcess.destroy();

            logger.info("go_process destroyed.");
        }
    }

    //Tests Polling flow with mock database
    @Test
    void testFullPollingFlow(Vertx vertx, VertxTestContext testContext)
    {
        logger.info("Starting testFullPollingFlow");

        vertx.eventBus().<JsonObject>localConsumer(Constants.EVENTBUS_DATABASE_ADDRESS, dbMessage ->
        {
            var request = dbMessage.body();

            logger.info("Received database request: {}", request.encodePrettily());

            if (request.containsKey(Constants.QUERY))
            {
                var query = request.getString(Constants.QUERY);

                if (query.contains("SELECT")) {
                    var devices = new JsonArray();
                    for (int i = 0; i < BATCH_SIZE; i++) {
                        devices.add(new JsonObject()
                                .put("job_id", i + 1)
                                .put("ip", "192.168.1." + (i + 1))
                                .put("system_type", "snmp")
                                .put("port",161)
                                .put("credentials", new JsonObject()
                                        .put("community", "public")
                                        .put("version", "2c")
                                )
                        );
                    }
                    dbMessage.reply(new JsonObject().put(Constants.DATA, devices));
                }

                else if (query.startsWith("insert"))
                {
                    var params = request.getJsonArray(Constants.PARAMS);

                    assertEquals(BATCH_SIZE * 3, params.size(), "Batch insert parameters count mismatch");

                    testContext.completeNow();
                }
            }
        });

        vertx.setTimer(30000, timerId ->
        {
            if (!testContext.completed())
            {
                logger.error("Test didn't complete within 25 seconds");

                testContext.failNow(new TimeoutException("Test didn't complete within 10 seconds"));
            }
        });
    }
}