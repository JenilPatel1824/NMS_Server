package io.vertx.nms.http;

import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.nms.http.router.CredentialRouter;
import io.vertx.nms.http.router.DiscoveryRouter;
import io.vertx.nms.http.router.ProvisionRouter;
import io.vertx.nms.http.router.HealthRouter;
import io.vertx.nms.database.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServerVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(HttpServerVerticle.class);

    @Override
    public void start()
    {
        Router mainRouter = Router.router(vertx);

        QueryBuilder queryBuilder = new QueryBuilder();

        mainRouter.route("/credential/*").subRouter( new CredentialRouter(vertx,queryBuilder).createRouter());

        mainRouter.route("/discovery/*").subRouter( new DiscoveryRouter(vertx, queryBuilder).createRouter());

        mainRouter.route("/provision/*").subRouter( new ProvisionRouter(vertx,queryBuilder).createRouter());

        mainRouter.route("/health/*").subRouter( new HealthRouter(vertx).createRouter());

        vertx.createHttpServer().requestHandler(mainRouter).listen(8080, http ->
        {
            if (http.succeeded())
            {
                logger.info("HTTP Server is listening on port 8080");
            }
            else
            {
                logger.error("Failed to start HTTP server: {}", http.cause().getMessage());
            }
        });
    }
}
