package io.vertx.nms.http.handler;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.nms.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.nms.service.DiscoveryService;

public class DiscoveryHandler
{
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryHandler.class);

    private final Vertx vertx;

    private final DiscoveryService discoveryService;

    private static final String DISCOVERY_PROFILE_NAME_PARAM = "/:discoveryProfileName";

    private static final String DISCOVERY_RUN_URL = "/:discoveryProfileName/run";

    public DiscoveryHandler(Vertx vertx)
    {
        this.discoveryService = new DiscoveryService(vertx);

        this.vertx = vertx;
    }

    //Creates and returns a router for handling discovery-related HTTP requests.
    public Router createRouter()
    {
        Router discoveryRouter = Router.router(vertx);

        discoveryRouter.get(DISCOVERY_PROFILE_NAME_PARAM).handler(ctx ->
        {
            String discoveryProfileName = ctx.pathParam(Constants.DISCOVERY_PROFILE_NAME);

            logger.info("Discovery Get/:");

            if (discoveryProfileName == null || discoveryProfileName.isEmpty())
            {
                ctx.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_NAME);

                return;
            }

            discoveryService.getDiscoveryByProfileName(discoveryProfileName, ctx);
        });

        discoveryRouter.get().handler(discoveryService::getAllDiscoveries);

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
                    ctx.response().setStatusCode(400).end(Constants.BAD_REQUEST_INVALID_JSON);

                    return;
                }

                if (requestBody.isEmpty())
                {
                    ctx.response().setStatusCode(400).end(Constants.HTTP_EMPTY_REQUEST);

                    return;
                }

                discoveryService.createDiscovery(requestBody, ctx);
            });
        });

        discoveryRouter.put(DISCOVERY_PROFILE_NAME_PARAM).handler(ctx ->
        {
            logger.info("Discovery Put/:");

            String discoveryProfileName = ctx.pathParam(Constants.DISCOVERY_PROFILE_NAME);

            ctx.request().bodyHandler(buffer ->
            {
                JsonObject updateRequest;
                try
                {
                    updateRequest = buffer.toJsonObject();
                }
                catch (Exception e)
                {
                    ctx.response().setStatusCode(400).end(Constants.BAD_REQUEST_INVALID_JSON);
                    return;
                }

                if (updateRequest.isEmpty())
                {
                    ctx.response().setStatusCode(400).end(Constants.HTTP_EMPTY_REQUEST);

                    return;
                }

                discoveryService.updateDiscovery(discoveryProfileName, updateRequest, ctx);
            });
        });

        discoveryRouter.delete(DISCOVERY_PROFILE_NAME_PARAM).handler(ctx ->
        {
            logger.info("Discovery Delete/:");

            String discoveryProfileName = ctx.pathParam(Constants.DISCOVERY_PROFILE_NAME);

            discoveryService.deleteDiscovery(discoveryProfileName, ctx);
        });

        discoveryRouter.post(DISCOVERY_RUN_URL).handler(ctx ->
        {
            logger.info("Discovery Run/:");

            String discoveryProfileName = ctx.pathParam(Constants.DISCOVERY_PROFILE_NAME);

            discoveryService.runDiscovery(discoveryProfileName, ctx);
        });

        return discoveryRouter;
    }
}
