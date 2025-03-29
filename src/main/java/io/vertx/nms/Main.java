package io.vertx.nms;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.nms.database.Database;
import io.vertx.nms.polling.PollingProcessor;
import io.vertx.nms.polling.PollingScheduler;
import io.vertx.nms.http.ApiServer;
import io.vertx.nms.messaging.ZmqMessenger;
import io.vertx.nms.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

import java.io.File;

public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final String HEALTH_CHECK = "health_check";

    private static final String OK = "ok";

    private static Process goProcess;

    public static void main(String[] args)
    {
        startGoPlugin();

        try (var context = ZMQ.context(1);
             var push = context.socket(SocketType.PUSH);
             var pull = context.socket(SocketType.PULL);)
        {
            push.bind(Constants.ZMQ_PUSH_ADDRESS);

            pull.bind(Constants.ZMQ_PULL_ADDRESS);

            push.setSendTimeOut(500);

            push.send(HEALTH_CHECK );

            pull.setReceiveTimeOut(500);

            var response = pull.recvStr();

            if (response == null)
            {
                logger.error("ZMQ server is not responding. Port 5555 might be in use");

                pull.close();

                System.exit(1);
            }
            else if (response.equals(OK))
            {
                logger.info("ZMQ server is available. Starting Vert.x application...");
            }
        }
        catch (Exception e)
        {
            logger.error("Failed to connect to ZMQ server: {}", e.getMessage());

            logger.error("ZMQ server is not available. Exiting application.");

            return;
        }

        var vertx = Vertx.vertx();

        vertx.deployVerticle(new ApiServer())
                .compose(apiRes ->
                {
                    logger.info("HTTP server verticle deployed");

                    return vertx.deployVerticle(Database.class.getName());
                })
                .compose(databaseRes ->
                {
                    logger.info("Database verticle deployed");

                    return vertx.deployVerticle(ZmqMessenger.class.getName());
                })
                .compose(zmqRes ->
                {
                    logger.info("ZMQ Messenger verticle deployed");

                    return vertx.deployVerticle(PollingProcessor.class.getName(), new DeploymentOptions().setInstances(1));
                })
                .compose(pollingRes ->
                {
                    logger.info("Polling engine verticle deployed");

                    return vertx.deployVerticle(PollingScheduler.class.getName());
                })
                .onSuccess(schedulerRes ->
                {
                    logger.info("Scheduler verticle deployed");

                    logger.info("All verticles deployed successfully.");
                })
                .onFailure(err ->
                {
                    logger.error("Failed to deploy verticles: {}", err.getMessage());

                    vertx.close();
                });
    }

    // startGoPlugin starts go plugin using process builder
    private static void startGoPlugin()
    {
        try
        {
            try
            {
                var killProcessBuilder = new ProcessBuilder("pkill", "-f", "go_plugin");

                killProcessBuilder.start().waitFor();

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

            var processBuilder = new ProcessBuilder(goPlugin.getAbsolutePath());

            goProcess = processBuilder.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() ->
            {
                if (goProcess.isAlive())
                {
                    goProcess.destroy();

                    logger.info("go_plugin terminated on JVM shutdown.");
                }
            }));

            logger.info("go_plugin started successfully.");

        }
        catch (Exception e)
        {
            logger.error("Failed to start go_plugin: {}", e.getMessage());

            System.exit(1);
        }
    }
}
