package io.vertx.nms.http;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.nms.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class ApiServer extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);

    private Service service;

    private static final String HTTP_PATH_CREDENTIAL = "/credential/*";

    private static final String HTTP_PATH_DISCOVERY = "/discovery/*";

    private static final String HTTP_PATH_PROVISION = "/provision/*";

    private static final String CREDENTIAL_PROFILE_ID_URL = "/:credentialProfileId";

    private static final String DISCOVERY_PROFILE_ID_URL = "/:discoveryProfileId";

    private static final String DISCOVERY_RUN_URL = "/:discoveryProfileId/run";

    private static final String PROVISION_START_URL = "/:discoveryProfileId";

    private static final String PROVISION_DATA_URL = "/data/:job_id";

    private static final String PROVISION_DELETE_URL = "/:discoveryProfileId";

    private static final String PROVISION_TOP_ERROR_INTERFACES_URL = "/topError";

    private static final String PROVISION_TOP_SPEED_INTERFACES_URL = "/topSpeed";

    private static final String PROVISION_TOP_UPTIME_DEVICES_URL = "/topUpTimeDevices";

    private static final String PROVISION_TOP_DOWN_INTERFACES_URL = "/topDownTimeInterfaces";

    private static final String PROVISION_DEVICES_URL = "/devices";

    private static final String CREDENTIAL_PROFILE_ID = "credentialProfileId";

    private static final String MESSAGE_REQUIRED_CREDENTIAL_PROFILE_ID = "credential profile id is required.";

    @Override
    public void start(Promise<Void> startPromise)
    {
        this.service = new Service(vertx);

        var mainRouter = Router.router(vertx);

        mainRouter.route().handler(CorsHandler.create().addOrigin("*").allowedMethods(Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS)).allowedHeaders(Set.of("*")));

        mainRouter.route(HTTP_PATH_CREDENTIAL).subRouter(createCredentialRouter());

        mainRouter.route(HTTP_PATH_DISCOVERY).subRouter(createDiscoveryRouter());

        mainRouter.route(HTTP_PATH_PROVISION).subRouter(createProvisionRouter());

        vertx.createHttpServer().requestHandler(mainRouter).listen(8080, http ->
        {
            if (http.succeeded())
            {
                logger.info("HTTP Server is listening on port 8080");

                startPromise.complete();
            }
            else
            {
                logger.error("Failed to start HTTP server: {}", http.cause().getMessage());

                startPromise.fail(http.cause());
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
                    service.create(buffer.toJsonObject(), context);
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
                    service.update(credentialProfileId,  buffer.toJsonObject(), context);
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
                    if (buffer.toJsonObject().isEmpty())
                    {
                        context.response().setStatusCode(400).end(Constants.MESSAGE_EMPTY_REQUEST);

                        return;
                    }

                    service.update(discoveryProfileId, buffer.toJsonObject(), context);
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

            logger.info("ProvisionHandler PUT provision start/ {}", discoveryProfileId);

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

        provisionRouter.get(PROVISION_TOP_ERROR_INTERFACES_URL).handler(service::getInterfacesByError);

        provisionRouter.get(PROVISION_TOP_SPEED_INTERFACES_URL).handler(service::getInterfacesBySpeed);

        provisionRouter.get(PROVISION_TOP_UPTIME_DEVICES_URL).handler(service::getDevicesByUptime);

        provisionRouter.get(PROVISION_TOP_DOWN_INTERFACES_URL).handler(service::getInterfacesByDowntime);

        provisionRouter.get(PROVISION_DEVICES_URL).handler(service::getDevices);

        return provisionRouter;

    }
}
