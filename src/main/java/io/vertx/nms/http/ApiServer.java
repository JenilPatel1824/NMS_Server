package io.vertx.nms.http;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.nms.service.Service;
import io.vertx.nms.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiServer extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);

    private final Service service;

    public ApiServer(Vertx vertx)
    {
        this.service=new Service(vertx);
    }

    private static final String HTTP_PATH_CREDENTIAL = "/credential/*";

    private static final String HTTP_PATH_DISCOVERY = "/discovery/*";

    private static final String HTTP_PATH_PROVISION = "/provision/*";

    private static final String CREDENTIAL_PROFILE_ID_URL = "/:credentialProfileId";

    private static final String DISCOVERY_PROFILE_ID_URL = "/:discoveryProfileId";

    private static final String DISCOVERY_RUN_URL = "/:discoveryProfileId/run";

    private static final String PROVISION_STATUS_URL = "/:discoveryProfileId/:status";

    private static final String PROVISION_DATA_URL = "/data/:discoveryProfileId";

    private static final String CREDENTIAL_PROFILE_ID = "credentialProfileId";

    private static final String REQUIRED_STATUS = "Parameter 'status' is required.";

    private static final String MESSAGE_REQUIRED_CREDENTIAL_PROFILE_ID = "credential profile id is required.";

    @Override
    public void start()
    {
        var mainRouter = Router.router(vertx);

        mainRouter.route(HTTP_PATH_CREDENTIAL).subRouter(createCredentialRouter());

        mainRouter.route(HTTP_PATH_DISCOVERY).subRouter(createDiscoveryRouter());

        mainRouter.route(HTTP_PATH_PROVISION).subRouter(createProvisionRouter());

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

    private Router createCredentialRouter()
    {
        var credentialRouter = Router.router(vertx);

        credentialRouter.post("/").handler(context ->
        {
            logger.info("CredentialHandler Post");

            context.request().bodyHandler(buffer ->
            {
                if (buffer == null || buffer.length() == 0)
                {
                    context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_BODY);

                    return;
                }
                try
                {
                    var requestBody = buffer.toJsonObject();

                    service.create(requestBody, context);
                }
                catch (DecodeException e)
                {
                    context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_JSON);
                }
            });
        });

        credentialRouter.get(CREDENTIAL_PROFILE_ID_URL).handler(context ->
        {
            var credentialProfileId = context.pathParam(CREDENTIAL_PROFILE_ID);

            logger.info("CredentialHandler Get/:{}", credentialProfileId);

            if (credentialProfileId == null || credentialProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end(MESSAGE_REQUIRED_CREDENTIAL_PROFILE_ID);

                return;
            }
            service.getById(credentialProfileId, context);
        });

        credentialRouter.get().handler(service::getAll);

        credentialRouter.put(CREDENTIAL_PROFILE_ID_URL).handler(context ->
        {
            var credentialProfileId = context.pathParam(CREDENTIAL_PROFILE_ID);

            logger.info("CredentialHandler Put/:{}", credentialProfileId);

            if (credentialProfileId == null || credentialProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end(MESSAGE_REQUIRED_CREDENTIAL_PROFILE_ID);

                return;
            }

            context.request().bodyHandler(buffer ->
            {
                if (buffer == null || buffer.length() == 0)
                {
                    context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_BODY);

                    return;
                }

                try
                {
                    var requestBody = buffer.toJsonObject();

                    service.update(credentialProfileId, requestBody, context);
                }
                catch (DecodeException e)
                {
                    context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_JSON);
                }
            });
        });

        credentialRouter.delete(CREDENTIAL_PROFILE_ID_URL).handler(context ->
        {
            var credentialProfileId = context.pathParam(CREDENTIAL_PROFILE_ID);

            logger.info("CredentialHandler Delete {}", credentialProfileId);

            if (credentialProfileId == null || credentialProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end("credential profile id is required.");

                return;
            }

            service.delete(credentialProfileId, context);
        });

        return credentialRouter;
    }

    public Router createDiscoveryRouter()
    {
        var discoveryRouter = Router.router(vertx);

        discoveryRouter.get(DISCOVERY_PROFILE_ID_URL).handler(context ->
        {
            var discoveryProfileId = context.pathParam(Constants.DISCOVERY_PROFILE_ID);

            logger.info("Discovery Get/: {}", discoveryProfileId);

            if (discoveryProfileId == null || discoveryProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_ID);

                return;
            }

            service.getById(discoveryProfileId, context);
        });

        discoveryRouter.get().handler(service::getAll);

        discoveryRouter.post("/").handler(context ->
        {
            logger.info("Discovery Post/: ");

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

                    service.create(requestBody, context);
                }
                catch (Exception e)
                {
                    context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_JSON);
                }
            });
        });

        discoveryRouter.put(DISCOVERY_PROFILE_ID_URL).handler(context ->
        {
            var discoveryProfileId = context.pathParam(Constants.DISCOVERY_PROFILE_ID);

            logger.info("Discovery Put/:{}", discoveryProfileId);

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

                    service.update(discoveryProfileId, updateRequest, context);
                }
                catch (Exception e)
                {
                    context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_JSON);
                }
            });

        });

        discoveryRouter.delete(DISCOVERY_PROFILE_ID_URL).handler(context ->
        {
            var discoveryProfileId = context.pathParam(Constants.DISCOVERY_PROFILE_ID);

            logger.info("Discovery Delete/: {}", discoveryProfileId);

            if (discoveryProfileId == null || discoveryProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_ID);

                return;
            }

            service.delete(discoveryProfileId, context);
        });

        discoveryRouter.post(DISCOVERY_RUN_URL).handler(context ->
        {
            var discoveryProfileId = context.pathParam(Constants.DISCOVERY_PROFILE_ID);

            logger.info("Discovery Run/:{}", discoveryProfileId);

            if (discoveryProfileId == null || discoveryProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_ID);

                return;
            }

            service.runDiscovery(discoveryProfileId, context);
        });

        return discoveryRouter;
    }

    public Router createProvisionRouter()
    {
        var provisionRouter = Router.router(vertx);

        provisionRouter.route().handler(BodyHandler.create());

        provisionRouter.post(PROVISION_STATUS_URL).handler(context ->
        {
            var discoveryProfileId = context.pathParam(Constants.DISCOVERY_PROFILE_ID);

            var status = context.pathParam(Constants.STATUS);

            logger.info("ProvisionHandler PUT /:discoveryProfileId/:status {} {}", discoveryProfileId, status);

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

            service.updateProvisionStatus(discoveryProfileId, status, context);
        });

        provisionRouter.get(PROVISION_DATA_URL).handler(context->
        {
            var discoveryProfileId = context.pathParam(Constants.DISCOVERY_PROFILE_ID);

            logger.info("ProvisionHandler GET /data/:discoveryProfileName {}", discoveryProfileId);

            if (discoveryProfileId == null || discoveryProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_ID);

                return;
            }

            service.getProvisionData(discoveryProfileId, context);
        });

        return provisionRouter;
    }
}
