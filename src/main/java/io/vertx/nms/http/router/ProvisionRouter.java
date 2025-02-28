package io.vertx.nms.http.router;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.nms.database.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProvisionRouter
{
    private static final Logger logger = LoggerFactory.getLogger(ProvisionRouter.class);

    private final EventBus eventBus;

    private final QueryBuilder queryBuilder;

    private final Vertx vertx;


    public ProvisionRouter(Vertx vertx, QueryBuilder queryBuilder)
    {
        this.eventBus = vertx.eventBus();

        this.queryBuilder = queryBuilder;

        this.vertx = vertx;
    }

    public Router createRouter()
    {
        Router provisionRouter = Router.router(vertx);

        provisionRouter.route().handler(BodyHandler.create());

        provisionRouter.put("/:discoveryProfileName/:status").handler(this::handleUpdateProvision);

        return provisionRouter;
    }

    private void handleUpdateProvision(RoutingContext ctx)
    {
        String discoveryProfileName = ctx.pathParam("discoveryProfileName");

        String status = ctx.pathParam("status").toLowerCase();

        Boolean provisionStatus;

        if ("yes".equals(status))
        {
            provisionStatus = true;
        }
        else if ("no".equals(status))
        {
            provisionStatus = false;
        }
        else
        {
            ctx.response().setStatusCode(400).end("Bad Request: Status must be either 'yes' or 'no'");

            return;
        }

        String query = String.format(
                "UPDATE discovery SET provision = %b WHERE discovery_profile_name = '%s' AND discovery = true",
                provisionStatus, discoveryProfileName);


        eventBus.request("database.query.execute", new JsonObject().put("query", query), reply ->
        {
            if (reply.succeeded())
            {
                ctx.response().setStatusCode(200).end("Provision status updated successfully");

            }
            else
            {
                logger.error("[{}] Failed to process PUT request: {}", Thread.currentThread().getName(), reply.cause().getMessage());

                ctx.response().setStatusCode(500).end("Internal Server Error");
            }
        });

    }


}