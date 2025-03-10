package io.vertx.nms.http.handler;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.nms.service.CredentialService;
import io.vertx.nms.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CredentialHandler
{
    private static final Logger logger = LoggerFactory.getLogger(CredentialHandler.class);

    private final CredentialService credentialService;

    private final Vertx vertx;

    private static final String CREDENTIAL_PROFILE_ID = "credentialProfileId";

    public static final String CREDENTIAL_PROFILE_ID_URL = "/:credentialProfileId";

    private static final String MESSAGE_REQUIRED_CREDENTIAL_PROFILE_ID = "credential profile id is required.";

    public CredentialHandler(Vertx vertx)
    {
        var eventBus = vertx.eventBus();

        this.vertx = vertx;

        this.credentialService = new CredentialService(eventBus);
    }

    //Creates and returns a router for handling credential-related HTTP requests.
    public Router createRouter()
    {
        var credentialRouter = Router.router(vertx);

        credentialRouter.post("/").handler(context ->
        {
            logger.debug("CredentialHandler Post");

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

                    credentialService.createCredential(requestBody, context);
                }
                catch (DecodeException e)
                {
                    context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_JSON);
                }
            });
        });

        credentialRouter.get(CREDENTIAL_PROFILE_ID_URL).handler(context ->
        {
            logger.debug("CredentialHandler Get/:");

            var credentialProfileId = context.pathParam(CREDENTIAL_PROFILE_ID);

            if (credentialProfileId == null || credentialProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end(MESSAGE_REQUIRED_CREDENTIAL_PROFILE_ID);

                return;
            }
            credentialService.getCredentialById(credentialProfileId, context);
        });

        credentialRouter.get().handler(credentialService::getAllCredentials);

        credentialRouter.put(CREDENTIAL_PROFILE_ID_URL).handler(context ->
        {
            logger.debug("CredentialHandler Put/:");

            var credentialProfileId = context.pathParam(CREDENTIAL_PROFILE_ID);

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

                    credentialService.updateCredential(credentialProfileId, requestBody, context);
                }

                catch (DecodeException e)
                {
                    context.response().setStatusCode(400).end(Constants.MESSAGE_INVALID_JSON);
                }
            });
        });

        credentialRouter.delete(CREDENTIAL_PROFILE_ID_URL).handler(context ->
        {
            logger.debug("CredentialHandler Delete");

            var credentialProfileId = context.pathParam(CREDENTIAL_PROFILE_ID);

            if (credentialProfileId == null || credentialProfileId.isEmpty())
            {
                context.response().setStatusCode(400).end(MESSAGE_REQUIRED_CREDENTIAL_PROFILE_ID);

                return;
            }

            credentialService.deleteCredential(credentialProfileId, context);
        });

        return credentialRouter;
    }
}
