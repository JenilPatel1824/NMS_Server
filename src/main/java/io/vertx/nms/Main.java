package io.vertx.nms;

import io.vertx.core.Vertx;
import io.vertx.nms.database.Database;
import io.vertx.nms.engine.PollingEngine;
import io.vertx.nms.http.HttpServerVerticle;
import io.vertx.nms.messaging.ZmqMessenger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[]         args)
    {
        var vertx = Vertx.vertx();

        vertx.deployVerticle(new HttpServerVerticle())
                .compose(httpId ->
                {
                    logger.info("HTTP server verticle deployed ");

                    return vertx.deployVerticle(new Database());
                })
                .compose(dbId ->
                {
                    logger.info("Database verticle deployed");

                    return vertx.deployVerticle(new ZmqMessenger());
                })
                .compose(zmqId ->
                {
                    logger.info("ZMQ verticle deployed ");

                    return vertx.deployVerticle(new PollingEngine());
                })
                .onSuccess(pollingId ->
                {
                    logger.info("Polling engine verticle deployed");

                    logger.info("All verticles deployed successfully.");
                })
                .onFailure(err ->
                {
                    logger.error("One or more verticles failed to deploy: {}", err.getMessage());

                    vertx.close(closeRes ->
                    {
                        if (closeRes.succeeded())
                        {
                            logger.error("Vert.x instance closed. Application stopped.");
                        }
                        else
                        {
                            logger.error("Failed to close Vert.x: {}", closeRes.cause().getMessage());
                        }
                    });
                });
    }
}
