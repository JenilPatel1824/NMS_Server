package io.vertx.nms;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.nms.database.DatabaseVerticle;
import io.vertx.nms.engine.PollingEngineVerticle;
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
            logger.error("HTTP server verticle failed to deploy {}", err.getMessage());

        });

        vertx.deployVerticle(new DatabaseVerticle()).onSuccess(id->
        {
            logger.info("Database verticle deployed" );

        }).onFailure(err->
        {
            logger.error("Database verticle failed to deploy {}", err.getMessage());

        });

        vertx.deployVerticle(ZmqMessengerVerticle.class,new DeploymentOptions().setInstances(5)).onSuccess(id->
        {
            logger.info("Zmq verticle deployed");
        }).onFailure(err->
        {
            logger.error("Zmq verticle failed to deploy {}", err.getMessage());
        });

        vertx.deployVerticle(new PollingEngineVerticle()).onSuccess(id->
        {
            logger.info("Poling engine verticle deployed");
        }).onFailure(err->
        {
            logger.error("Poling engine verticle failed to deploy {}", err.getMessage());
        });
    }

    // Main method to deploy the main verticle.
    public static void main(String[] args)
    {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new MainVerticle()).onSuccess(id->
        {
            logger.info("main vertcle deployed");

        }).onFailure(err->
        {
            logger.error("main verticle failed to deploy {}", err.getMessage());
        });
    }
}
