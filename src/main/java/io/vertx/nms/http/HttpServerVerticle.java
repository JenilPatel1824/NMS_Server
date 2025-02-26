package io.vertx.nms.http;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServerVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(HttpServerVerticle.class);

    @Override
    public void start()
    {
        Router mainRouter = Router.router(vertx);

        Router credentialRouter = Router.router(vertx);

        EventBus eventBus = vertx.eventBus();

        credentialRouter.get("/:credentialProfileName").handler(ctx ->
        {
            String credentialProfileName = ctx.pathParam("credentialProfileName");

            if (!isValidId(credentialProfileName, ctx)) return;

            eventBus.request("service.credential.read", credentialProfileName, reply -> {

                if (reply.succeeded())
                {
                    ctx.response()
                            .setStatusCode(200)
                            .end(reply.result().body().toString());
                }
                else
                {
                    logger.error("Failed to process GET request: {}", reply.cause().getMessage());

                    ctx.response().setStatusCode(500).end("Internal Server Error");
                }
            });
        });

        credentialRouter.delete("/:credentialProfileName").handler(ctx -> {

            String credentialProfileName = ctx.pathParam("credentialProfileName");

            if (!isValidId(credentialProfileName, ctx)) return;

            eventBus.request("service.credential.delete", credentialProfileName, reply -> {

                if (reply.succeeded())
                {
                    ctx.response()
                            .setStatusCode(200)
                            .end(reply.result().body().toString());
                }
                else
                {
                    logger.error("Failed to process DELETE request: {}", reply.cause().getMessage());
                    ctx.response().setStatusCode(500).end("Internal Server Error");
                }
            });
        });

        credentialRouter.post().handler(ctx -> {

            ctx.request().bodyHandler(buffer -> {

                String body = buffer.toString();

                logger.info("POST Request body: {}", body);

                eventBus.request("service.credential.create", body, reply -> {

                    if (reply.succeeded())
                    {
                        ctx.response()
                                .setStatusCode(201)
                                .end(reply.result().body().toString());
                    }
                    else
                    {
                        logger.error("Failed to process POST request: {}", reply.cause().getMessage());
                        ctx.response().setStatusCode(500).end("Internal Server Error");
                    }
                });
            });
        });

        credentialRouter.put().handler(ctx -> {

            logger.info("put req recived: ");

            ctx.request().bodyHandler(buffer -> {

                String body = buffer.toString();

                logger.info("PUT Request body: {}", body);

                JsonObject updateRequest = new JsonObject(body);

                eventBus.request("service.credential.update", updateRequest, reply -> {

                    if (reply.succeeded())
                    {
                        ctx.response()
                                .setStatusCode(200)
                                .end(reply.result().body().toString());
                    }
                    else
                    {
                        logger.error("Failed to process PUT request: {}", reply.cause().getMessage());

                        ctx.response().setStatusCode(500).end("Internal Server Error");
                    }
                });
            });
        });

        mainRouter.mountSubRouter("/credential", credentialRouter);

        vertx.createHttpServer().requestHandler(mainRouter).listen(8080, http -> {
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

    private boolean isValidId(String id, RoutingContext ctx)
    {
        if (id == null || id.isEmpty())
        {
            ctx.response().setStatusCode(400).end("Bad Request: Credential Profile Name is required");

            return false;
        }
        return true;
    }

}
