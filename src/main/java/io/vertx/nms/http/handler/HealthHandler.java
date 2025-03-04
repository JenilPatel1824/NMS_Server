package io.vertx.nms.http.handler;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthHandler
{
    private static final Logger logger = LoggerFactory.getLogger(HealthHandler.class);

    private final Vertx vertx;

    public HealthHandler(Vertx vertx)
    {
        this.vertx=vertx;
    }

    //Creates and returns a router for handling basic ping for HTTP requests.
    public Router createRouter()
    {
        Router router = Router.router(vertx);

        router.get().handler(ctx -> {

            String threadName = Thread.currentThread().getName();

            logger.info("[{}] Handling GET request for /ping", threadName);

           ctx.request().response().setStatusCode(200).end("UP and Working");
        });

        return router;
    }
}
