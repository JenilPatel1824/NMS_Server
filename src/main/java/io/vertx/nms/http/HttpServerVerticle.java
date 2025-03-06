package io.vertx.nms.http;

import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.nms.http.handler.CredentialHandler;
import io.vertx.nms.http.handler.DiscoveryHandler;
import io.vertx.nms.http.handler.ProvisionHandler;
import io.vertx.nms.http.handler.PingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServerVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(HttpServerVerticle.class);

    private static final String HTTP_PATH_CREDENTIAL="/credential/*";

    private static final String HTTP_PATH_DISCOVERY="/discovery/*";

    private static final String HTTP_PATH_PROVISION="/provision/*";

    private static final String HTTP_PATH_PING="/ping/*";

    // Starts the HTTP server and sets up route handlers for different endpoints.
    // Initializes sub-routers for handling credential, discovery, provision, and health endpoints.
    // Listens on port 8080 for incoming requests.
    @Override
    public void start()
    {
        Router mainRouter = Router.router(vertx);

        mainRouter.route(HTTP_PATH_CREDENTIAL).subRouter( new CredentialHandler(vertx).createRouter());

        mainRouter.route(HTTP_PATH_DISCOVERY).subRouter( new DiscoveryHandler(vertx).createRouter());

        mainRouter.route(HTTP_PATH_PROVISION).subRouter( new ProvisionHandler(vertx).createRouter());

        mainRouter.route(HTTP_PATH_PING).subRouter( new PingHandler(vertx).createRouter());

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
