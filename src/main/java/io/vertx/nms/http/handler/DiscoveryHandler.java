package io.vertx.nms.http.handler;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.nms.service.DiscoveryService;

public class DiscoveryHandler
{
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryHandler.class);

    private final Vertx vertx;

    private final DiscoveryService discoveryService;

    public DiscoveryHandler(Vertx vertx)
    {
        this.discoveryService = new DiscoveryService(vertx);

        this.vertx = vertx;
    }

    //Creates and returns a router for handling discovery-related HTTP requests.
    public Router createRouter()
    {
        Router discoveryRouter = Router.router(vertx);

        discoveryRouter.get("/").handler(discoveryService::getAllDiscoveries);

        discoveryRouter.get("/:discoveryProfileName").handler(ctx ->
        {
            String discoveryProfileName = ctx.pathParam("discoveryProfileName");

            logger.info("Discovery Get/:");

            if (discoveryProfileName == null || discoveryProfileName.isEmpty())
            {
                ctx.response().setStatusCode(400).end("Parameter 'discoveryProfileName' is required.");

                return;
            }

            discoveryService.getDiscoveryByProfileName(discoveryProfileName, ctx);
        });

        discoveryRouter.post("/").handler(ctx ->
        {
            logger.info("Discovery Post/:");

            ctx.request().bodyHandler(buffer ->
            {
                JsonObject requestBody;
                try
                {
                    requestBody = buffer.toJsonObject();
                }
                catch (Exception e)
                {
                    ctx.response().setStatusCode(400).end("Bad Request: Invalid JSON");

                    return;
                }

                if (requestBody.isEmpty())
                {
                    ctx.response().setStatusCode(400).end("Bad Request: Empty request body");

                    return;
                }

                discoveryService.createDiscovery(requestBody, ctx);
            });
        });

        discoveryRouter.put("/:discoveryProfileName").handler(ctx ->
        {
            logger.info("Discovery Put/:");

            String discoveryProfileName = ctx.pathParam("discoveryProfileName");

            ctx.request().bodyHandler(buffer ->
            {
                JsonObject updateRequest;
                try
                {
                    updateRequest = buffer.toJsonObject();
                }
                catch (Exception e)
                {
                    ctx.response().setStatusCode(400).end("Bad Request: Invalid JSON");
                    return;
                }

                if (updateRequest.isEmpty())
                {
                    ctx.response().setStatusCode(400).end("Bad Request: No fields to update");
                    return;
                }

                discoveryService.updateDiscovery(discoveryProfileName, updateRequest, ctx);
            });
        });

        discoveryRouter.delete("/:discoveryProfileName").handler(ctx ->
        {
            logger.info("Discovery Delete/:");

            String discoveryProfileName = ctx.pathParam("discoveryProfileName");

            discoveryService.deleteDiscovery(discoveryProfileName, ctx);
        });

        discoveryRouter.post("/:discoveryId/run").handler(ctx ->
        {
            logger.info("Discovery Run/:");

            String discoveryId = ctx.pathParam("discoveryId");

            discoveryService.runDiscovery(discoveryId, ctx);
        });

        return discoveryRouter;
    }
}
