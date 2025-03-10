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

    private static final String DISCOVERY_PROFILE_ID_URL = "/:discoveryProfileId";

    private static final String DISCOVERY_RUN_URL = "/:discoveryProfileId/run";

    public DiscoveryHandler(Vertx vertx)
    {
        this.discoveryService = new DiscoveryService(vertx);

        this.vertx = vertx;
    }

    //Creates and returns a router for handling discovery-related HTTP requests.
    public Router createRouter()
    {
        var discoveryRouter = Router.router(vertx);

        discoveryRouter.get(DISCOVERY_PROFILE_ID_URL).handler(context ->
        {
            var discoveryProfileId = context.pathParam(Constants.DISCOVERY_PROFILE_ID);

            logger.info("Discovery Get/:");

            if (discoveryProfileId == null || discoveryProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_ID);

                return;
            }

            discoveryService.getDiscoveryById(discoveryProfileId, context);
        });

        discoveryRouter.get().handler(discoveryService::getAllDiscoveries);

        discoveryRouter.post("/").handler(context ->
        {
            logger.info("Discovery Post/:");

            context.request().bodyHandler(buffer ->
            {
                try
                {
                    var requestBody = buffer.toJsonObject();

                    if (requestBody.isEmpty())
                    {
                        context.response().setStatusCode(400).end(Constants.MESSAGE_EMPTY_REQUEST);

                        return;
                    }

                    discoveryService.createDiscovery(requestBody, context);
                }
                catch (Exception e)
                {
                    context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_JSON);
                }
            });
        });

        discoveryRouter.put(DISCOVERY_PROFILE_ID_URL).handler(context ->
        {
            logger.info("Discovery Put/:");

            var discoveryProfileId = context.pathParam(Constants.DISCOVERY_PROFILE_ID);

            if (discoveryProfileId == null || discoveryProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_ID);

                return;
            }

            context.request().bodyHandler(buffer ->
            {
                try
                {
                    var updateRequest = buffer.toJsonObject();

                    if (updateRequest.isEmpty())
                    {
                        context.response().setStatusCode(400).end(Constants.MESSAGE_EMPTY_REQUEST);

                        return;
                    }

                    discoveryService.updateDiscovery(discoveryProfileId, updateRequest, context);
                }
                catch (Exception e)
                {
                    context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_JSON);
                }
            });

        });

        discoveryRouter.delete(DISCOVERY_PROFILE_ID_URL).handler(context ->
        {
            logger.info("Discovery Delete/:");

            var discoveryProfileId = context.pathParam(Constants.DISCOVERY_PROFILE_ID);

            if (discoveryProfileId == null || discoveryProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_ID);

                return;
            }

            discoveryService.deleteDiscovery(discoveryProfileId, context);
        });

        discoveryRouter.post(DISCOVERY_RUN_URL).handler(context ->
        {
            logger.info("Discovery Run/:");

            var discoveryProfileId = context.pathParam(Constants.DISCOVERY_PROFILE_ID);

            if (discoveryProfileId == null || discoveryProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_ID);

                return;
            }

            discoveryService.runDiscovery(discoveryProfileId, context);
        });

        return discoveryRouter;
    }
}
