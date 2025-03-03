package io.vertx.nms.http.handler;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.nms.service.ProvisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProvisionHandler
{
    private static final Logger logger = LoggerFactory.getLogger(ProvisionHandler.class);

    private final Vertx vertx;

    private final ProvisionService provisionService;

    public ProvisionHandler(Vertx vertx)
    {
        this.vertx = vertx;

        this.provisionService = new ProvisionService(vertx);
    }

    public Router createRouter()
    {
        Router provisionRouter = Router.router(vertx);

        provisionRouter.route().handler(BodyHandler.create());

        provisionRouter.post("/:discoveryProfileName/:status").handler(this::handleUpdateProvision);

        provisionRouter.get("/data/:discoveryProfileName").handler(this::handleGetProvisionData);

        provisionRouter.get("/data").handler(this::handleGetAllProvisionData);

        return provisionRouter;
    }

    private void handleUpdateProvision(RoutingContext ctx)
    {
        logger.debug("ProvisionHandler PUT /:discoveryProfileName/:status");

        String discoveryProfileName = ctx.pathParam("discoveryProfileName");

        String status = ctx.pathParam("status");

        if (discoveryProfileName == null || discoveryProfileName.isEmpty())
        {
            ctx.response().setStatusCode(400).end("Parameter 'discoveryProfileName' is required.");

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

        String discoveryProfileName = ctx.pathParam("discoveryProfileName");

        if (discoveryProfileName == null || discoveryProfileName.isEmpty())
        {
            ctx.response().setStatusCode(400).end("Parameter 'discoveryProfileName' is required.");

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
