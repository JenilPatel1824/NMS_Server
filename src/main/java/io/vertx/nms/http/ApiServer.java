package io.vertx.nms.http;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
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

    private static final String PROVISION_START_URL = "/start/:discoveryProfileId";

    private static final String PROVISION_DATA_URL = "/data/:job_id";

    private static final String PROVISION_DELETE_URL = "/:discoveryProfileId";

    private static final String PROVISION_TOP_ERROR_URL = "/topError";

    private static final String PROVISION_TOP_SPEED_URL = "/topSpeed";

    private static final String PROVISION_TOP_UPTIME_URL = "/topRestarts";

    private static final String PROVISION_DEVICES_URL = "/devices";

    private static final String CREDENTIAL_PROFILE_ID = "credentialProfileId";

    private static final String MESSAGE_REQUIRED_CREDENTIAL_PROFILE_ID = "credential profile id is required.";

    @Override
    public void start()
    {
        var mainRouter = Router.router(vertx);

        mainRouter.route().handler(CorsHandler.create("*")
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.PUT)
                .allowedMethod(HttpMethod.DELETE)
                .allowedMethod(HttpMethod.OPTIONS)
                .allowedHeader("*"));

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

    // Creates a router for handling credential operations.
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

    // Creates a router for handling discovery operations.
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

    // Creates a router for handling provisioning operations.
    public Router createProvisionRouter()
    {
        var provisionRouter = Router.router(vertx);

        provisionRouter.route().handler(BodyHandler.create());

        provisionRouter.post(PROVISION_START_URL).handler(context ->
        {
            var discoveryProfileId = context.pathParam(Constants.DISCOVERY_PROFILE_ID);

            logger.info("ProvisionHandler PUT /:discoveryProfileId/:status {}", discoveryProfileId);

            if (discoveryProfileId == null || discoveryProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_ID);

                return;
            }

            service.updateProvisionStatus(discoveryProfileId, context);
        });

        provisionRouter.get(PROVISION_DATA_URL).handler(context->
        {
            var jobId = context.pathParam(Constants.DATABASE_JOB_ID);

            logger.info("ProvisionHandler GET /data/:discoveryProfileID {}", jobId);

            if (jobId == null || jobId.isEmpty())
            {
                context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_JOB_ID);

                return;
            }

            service.getProvisionData(jobId, context);
        });

        provisionRouter.delete(PROVISION_DELETE_URL).handler(context->
        {
            var discoveryProfileId = context.pathParam(Constants.DISCOVERY_PROFILE_ID);

            logger.info("ProvisionHandler delete /:discoveryProfileId {}", discoveryProfileId);

            if (discoveryProfileId == null || discoveryProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end(Constants.MESSAGE_REQUIRED_DISCOVERY_PROFILE_ID);

                return;
            }

            service.deleteProvisioningJob(discoveryProfileId, context);
        });
        provisionRouter.get(PROVISION_TOP_ERROR_URL).handler(context->
        {
            service.getInterfacesByError(context);
        });

        provisionRouter.get(PROVISION_TOP_SPEED_URL).handler(context->
        {
            service.getInterfacesBySpeed(context);
        });

        provisionRouter.get(PROVISION_TOP_UPTIME_URL).handler(context->
        {
            service.getInterfacesByUptime(context);
        });

        provisionRouter.get(PROVISION_DEVICES_URL).handler(context->
        {
            service.getDevices(context);
        });

        return provisionRouter;

    }
}
