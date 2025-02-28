package io.vertx.nms.http.router;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthRouter
{
    private static final Logger logger = LoggerFactory.getLogger(HealthRouter.class);

    private final Vertx vertx;

    public HealthRouter(Vertx vertx)
    {
        this.vertx=vertx;
    }

    public Router createRouter()
    {
        Router router = Router.router(vertx);

        router.get().handler(ctx -> {

            String threadName = Thread.currentThread().getName();

            logger.info("[{}] Handling GET request for /health", threadName);

           ctx.request().response().setStatusCode(200).end("UP and Working");
        });

        return router;
    }
}
