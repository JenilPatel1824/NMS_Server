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

    private static final String PROVISION_STATUS_URL = "/:discoveryProfileId/:status";

    private static final String PROVISION_DATA_URL = "/data/:discoveryProfileId";

    private static final String PROVISION_ALL_DATA_URL = "/data";

    private static final String REQUIRED_STATUS = "Parameter 'status' is required.";

    public ProvisionHandler(Vertx vertx)
    {
        this.vertx = vertx;

        this.provisionService = new ProvisionService(vertx);
    }

    public Router createRouter()
    {
        var provisionRouter = Router.router(vertx);

        provisionRouter.route().handler(BodyHandler.create());

        provisionRouter.post(PROVISION_STATUS_URL).handler(this::handleUpdateProvision);

        provisionRouter.get(PROVISION_DATA_URL).handler(this::handleGetProvisionData);

        provisionRouter.get(PROVISION_ALL_DATA_URL).handler(this::handleGetAllProvisionData);

        return provisionRouter;
    }

    private void handleUpdateProvision(RoutingContext context)
    {
        logger.debug("ProvisionHandler PUT /:discoveryProfileId/:status");

        var discoveryProfileId = context.pathParam(Constants.DISCOVERY_PROFILE_ID);

        var status = context.pathParam(Constants.STATUS);

        if (discoveryProfileId == null || discoveryProfileId.isEmpty())
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_ID);

            return;
        }

        if (status == null || status.isEmpty())
        {
            context.response().setStatusCode(400).end(REQUIRED_STATUS);

            return;
        }

        provisionService.updateProvisionStatus(discoveryProfileId, status, context);
    }

    private void handleGetProvisionData(RoutingContext context)
    {
        logger.debug("ProvisionHandler GET /data/:discoveryProfileName");

        var discoveryProfileId = context.pathParam(Constants.DISCOVERY_PROFILE_ID);

        if (discoveryProfileId == null || discoveryProfileId.isEmpty())
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_ID);

            return;
        }

        provisionService.getProvisionData(discoveryProfileId, context);
    }

    private void handleGetAllProvisionData(RoutingContext context)
    {
        logger.debug("ProvisionHandler GET /data");

        provisionService.getAllProvisionData(context);
    }
}
