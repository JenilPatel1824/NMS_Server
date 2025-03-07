package io.vertx.nms;


//service
//ping
//getsnmp

//todo constants,json object improvements,
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.nms.database.DatabaseVerticle;
import io.vertx.nms.engine.PollingEngineVerticle;
import io.vertx.nms.http.HttpServerVerticle;
import io.vertx.nms.messaging.ZmqMessengerVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args)
    {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new HttpServerVerticle())
                .compose(httpId ->
                {
                    logger.info("HTTP server verticle deployed with id: {}", httpId);

                    return vertx.deployVerticle(new DatabaseVerticle());
                })
                .compose(dbId ->
                {
                    logger.info("Database verticle deployed with id: {}", dbId);

                    return vertx.deployVerticle(new ZmqMessengerVerticle());
                })
                .compose(zmqId ->
                {
                    logger.info("ZMQ verticle deployed with id: {}", zmqId);
                    return vertx.deployVerticle(new PollingEngineVerticle());
                })
                .onSuccess(pollingId -> {
                    logger.info("Polling engine verticle deployed with id: {}", pollingId);
                    logger.info("All verticles deployed successfully.");
                })
                .onFailure(err -> {
                    logger.error("One or more verticles failed to deploy: {}", err.getMessage());

                    // Shut down Vertx to stop the app
                    vertx.close(closeRes -> {
                        if (closeRes.succeeded()) {
                            logger.error("Vert.x instance closed. Application stopped.");
                        } else {
                            logger.error("Failed to close Vert.x: {}", closeRes.cause().getMessage());
                        }
                    });
                });
    }
}
