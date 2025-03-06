package io.vertx.nms.http.handler;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.nms.service.CredentialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CredentialHandler
{
    private static final Logger logger = LoggerFactory.getLogger(CredentialHandler.class);

    private final CredentialService credentialService;

    private final Vertx vertx;

    private static final String CREDENTIAL_PROFILE_NAME_KEY = "credentialProfileName";

    public static final String CREDENTIAL_PROFILE_NAME_PARAM = "/:credentialProfileName";

    public CredentialHandler(Vertx vertx)
    {
        EventBus eventBus = vertx.eventBus();

        this.vertx = vertx;

        this.credentialService = new CredentialService(eventBus);
    }

    //Creates and returns a router for handling credential-related HTTP requests.
    public Router createRouter()
    {
        Router credentialRouter = Router.router(vertx);

        credentialRouter.get(CREDENTIAL_PROFILE_NAME_PARAM).handler(ctx ->
        {
            logger.debug("CredentialHandler Get/:");

            String credentialProfileName = ctx.pathParam(CREDENTIAL_PROFILE_NAME_KEY);

            if (credentialProfileName == null || credentialProfileName.isEmpty())
            {
                ctx.response().setStatusCode(400).end("Parameter 'credentialProfileName' is required.");

                return;
            }
            credentialService.getCredentialByName(credentialProfileName, ctx);
        });

        credentialRouter.get().handler(credentialService::getAllCredentials);

        credentialRouter.post("/").handler(ctx ->
        {
            logger.debug("CredentialHandler Post");

            ctx.request().bodyHandler(buffer ->
            {
                if (buffer == null || buffer.length() == 0)
                {
                    ctx.response().setStatusCode(400).end("Request body is required.");

                    return;
                }
                try
                {
                    JsonObject requestBody = buffer.toJsonObject();

                    credentialService.createCredential(requestBody, ctx);
                }
                catch (DecodeException e)
                {
                    ctx.response().setStatusCode(400).end("Invalid JSON format.");
                }
            });
        });

        credentialRouter.put(CREDENTIAL_PROFILE_NAME_PARAM).handler(ctx ->
        {
            logger.debug("CredentialHandler Put/:");

            String credentialProfileName = ctx.pathParam(CREDENTIAL_PROFILE_NAME_KEY);

            if (credentialProfileName == null || credentialProfileName.isEmpty())
            {
                ctx.response().setStatusCode(400).end("Parameter 'credentialProfileName' is required.");

                return;
            }

            ctx.request().bodyHandler(buffer ->
            {
                if (buffer == null || buffer.length() == 0)
                {
                    ctx.response().setStatusCode(400).end("Request body is required.");

                    return;
                }

                try
                {
                    JsonObject requestBody = buffer.toJsonObject();

                    credentialService.updateCredential(credentialProfileName, requestBody, ctx);
                }

                catch (DecodeException e)
                {
                    ctx.response().setStatusCode(400).end("Invalid JSON format.");
                }
            });
        });

        credentialRouter.delete(CREDENTIAL_PROFILE_NAME_PARAM).handler(ctx ->
        {
            logger.debug("CredentialHandler Delete");

            String credentialProfileName = ctx.pathParam(CREDENTIAL_PROFILE_NAME_KEY);

            if (credentialProfileName == null || credentialProfileName.isEmpty())
            {
                ctx.response().setStatusCode(400).end("Parameter 'credentialProfileName' is required.");

                return;
            }
            credentialService.deleteCredential(credentialProfileName, ctx);
        });

        return credentialRouter;
    }
}
