package io.vertx.nms;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.nms.database.DatabaseVerticle;
import io.vertx.nms.engine.PollingEngine;
import io.vertx.nms.http.HttpServerVerticle;
import io.vertx.nms.messaging.ZmqMessengerVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MainVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start()
    {
        vertx.deployVerticle(new HttpServerVerticle()).onSuccess(id->
        {
           logger.info("HTTP server verticle deployed" );
        }).onFailure(err->
        {
            logger.error("HTTP server verticle failed to deploy "+err.getMessage());
        });

        vertx.deployVerticle(new DatabaseVerticle());

        vertx.deployVerticle(ZmqMessengerVerticle.class,new DeploymentOptions().setInstances(5));

        vertx.deployVerticle(new PollingEngine());
    }

    public static void main(String[] args)
    {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new MainVerticle()).onSuccess(id->
        {
            logger.info("main vertcle deployed");

        }).onFailure(err->
        {
            logger.error("main verticle failed to deploy "+err.getMessage());
        });
    }
}
