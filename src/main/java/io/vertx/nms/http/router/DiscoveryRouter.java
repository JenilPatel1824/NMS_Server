package io.vertx.nms.http.router;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.nms.database.QueryBuilder;
import io.vertx.nms.service.DiscoveryService;

public class DiscoveryRouter
{
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryRouter.class);

    private final Vertx vertx;

    private final DiscoveryService discoveryService;

    public DiscoveryRouter(Vertx vertx, QueryBuilder queryBuilder)
    {
        this.discoveryService = new DiscoveryService(vertx, queryBuilder);
        this.vertx = vertx;
    }

    public Router createRouter()
    {
        Router discoveryRouter = Router.router(vertx);

        // GET All Discoveries
        discoveryRouter.get("/").handler(ctx ->
        {
            discoveryService.getAllDiscoveries(ctx);
        });

        // GET By discoveryProfileName
        discoveryRouter.get("/:discoveryProfileName").handler(ctx ->
        {
            String discoveryProfileName = ctx.pathParam("discoveryProfileName");

            logger.info("Req for /discovery/:discoveryProfileName", discoveryProfileName);

            if (discoveryProfileName == null || discoveryProfileName.isEmpty())
            {
                ctx.response().setStatusCode(400).end("Parameter 'discoveryProfileName' is required.");
                return;
            }

            discoveryService.getDiscoveryByProfileName(discoveryProfileName, ctx);
        });

        discoveryRouter.post("/").handler(ctx ->
        {
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

        // PUT (Update) by discoveryProfileName
        discoveryRouter.put("/:discoveryProfileName").handler(ctx ->
        {
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
            String discoveryProfileName = ctx.pathParam("discoveryProfileName");
            discoveryService.deleteDiscovery(discoveryProfileName, ctx);
        });

        discoveryRouter.post("/:discoveryId/run").handler(ctx ->
        {
            String discoveryId = ctx.pathParam("discoveryId");

            discoveryService.runDiscovery(discoveryId, ctx);
        });

        return discoveryRouter;
    }
}
