package io.vertx.nms.http.handler;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.nms.util.Constants;
import io.vertx.nms.service.ProvisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProvisionHandler
{
    private static final Logger logger = LoggerFactory.getLogger(ProvisionHandler.class);

    private final Vertx vertx;

    private final ProvisionService provisionService;

    private static final String PROVISION_STATUS_URL = "/:discoveryProfileName/:status";

    private static final String PROVISION_DATA_URL = "/data/:discoveryProfileName";

    private static final String PROVISION_ALL_DATA_URL = "/data";

    public ProvisionHandler(Vertx vertx)
    {
        this.vertx = vertx;

        this.provisionService = new ProvisionService(vertx);
    }

    public Router createRouter()
    {
        Router provisionRouter = Router.router(vertx);

        provisionRouter.route().handler(BodyHandler.create());

        provisionRouter.post(PROVISION_STATUS_URL).handler(this::handleUpdateProvision);

        provisionRouter.get(PROVISION_DATA_URL).handler(this::handleGetProvisionData);

        provisionRouter.get(PROVISION_ALL_DATA_URL).handler(this::handleGetAllProvisionData);

        return provisionRouter;
    }

    private void handleUpdateProvision(RoutingContext ctx)
    {
        logger.debug("ProvisionHandler PUT /:discoveryProfileName/:status");

        String discoveryProfileName = ctx.pathParam(Constants.DISCOVERY_PROFILE_NAME);

        String status = ctx.pathParam(Constants.STATUS);

        if (discoveryProfileName == null || discoveryProfileName.isEmpty())
        {
            ctx.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_NAME);

            return;
        }

        if (status == null || status.isEmpty())
        {
            ctx.response().setStatusCode(400).end("Parameter 'status' is required.");

            return;
        }

        provisionService.updateProvisionStatus(discoveryProfileName, status, ctx);
    }

    private void handleGetProvisionData(RoutingContext ctx)
    {
        logger.debug("ProvisionHandler GET /data/:discoveryProfileName");

        String discoveryProfileName = ctx.pathParam(Constants.DISCOVERY_PROFILE_NAME);

        if (discoveryProfileName == null || discoveryProfileName.isEmpty())
        {
            ctx.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_NAME);

            return;
        }

        provisionService.getProvisionData(discoveryProfileName, ctx);
    }

    private void handleGetAllProvisionData(RoutingContext ctx)
    {
        logger.debug("ProvisionHandler GET /data");

        provisionService.getAllProvisionData(ctx);
    }
}
