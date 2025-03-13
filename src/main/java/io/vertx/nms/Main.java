package io.vertx.nms;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.nms.database.Database;
import io.vertx.nms.engine.PollingEngine;
import io.vertx.nms.http.ApiServer;
import io.vertx.nms.messaging.ZmqMessenger;
import io.vertx.nms.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;

public class Main extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final String HEALTH_CHECK = "health_check";

    private static final String OK = "ok";

    @Override
    public void start()
    {
        try (var context = ZMQ.context(1);
             var socket = context.socket(ZMQ.REQ))
        {
            socket.connect(Constants.ZMQ_ADDRESS);

            socket.send(HEALTH_CHECK, ZMQ.NOBLOCK);

            socket.setReceiveTimeOut(500);

            var response = socket.recvStr();

            if (response == null)
            {
                logger.error("ZMQ server is not responding.");

                socket.close();

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

        vertx.deployVerticle(new ApiServer(vertx))
                .compose(apiRes ->
                {
                    logger.info("HTTP server verticle deployed");

                    return vertx.deployVerticle(new Database());
                })
                .compose(dbRes ->
                {
                    logger.info("Database verticle deployed");

                    return vertx.deployVerticle(new ZmqMessenger());
                })
                .compose(zmqRes ->
                {
                    logger.info("ZMQ Messenger verticle deployed");

                    return vertx.deployVerticle(new PollingEngine());
                })
                .onSuccess(pollingRes ->
                {
                    logger.info("Polling engine verticle deployed");

                    logger.info("All verticles deployed successfully.");
                })
                .onFailure(err ->
                {
                    logger.error("Failed to deploy verticles: {}", err.getMessage());

                    vertx.close();
                });
    }

    public static void main(String[] args)
    {
        try (var context = ZMQ.context(1);
             var socket = context.socket(ZMQ.REQ))
        {
            socket.connect(Constants.ZMQ_ADDRESS);

            socket.send(HEALTH_CHECK, ZMQ.NOBLOCK);

            socket.setReceiveTimeOut(500);

            var response = socket.recvStr();

            if (response == null)
            {
                logger.error("ZMQ server is not responding.");

                socket.close();

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

        vertx.deployVerticle(new ApiServer(vertx))
                .compose(apiRes ->
                {
                    logger.info("HTTP server verticle deployed");

                    return vertx.deployVerticle(new Database());
                })
                .compose(dbRes ->
                {
                    logger.info("Database verticle deployed");

                    return vertx.deployVerticle(new ZmqMessenger());
                })
                .compose(zmqRes ->
                {
                    logger.info("ZMQ Messenger verticle deployed");

                    return vertx.deployVerticle(new PollingEngine());
                })
                .onSuccess(pollingRes ->
                {
                    logger.info("Polling engine verticle deployed");

                    logger.info("All verticles deployed successfully.");
                })
                .onFailure(err ->
                {
                    logger.error("Failed to deploy verticles: {}", err.getMessage());

                    vertx.close();
                });
    }
}
