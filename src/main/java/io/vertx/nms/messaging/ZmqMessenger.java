package io.vertx.nms.messaging;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.nms.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ZmqMessenger extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(ZmqMessenger.class);

    private ZMQ.Context context;

    private ZMQ.Socket push;

    private ZMQ.Socket pull;

    private static final int RESPONSE_CHECK_INTERVAL_MS = 500;

    private static final long REQUEST_TIMEOUT_MS = 260_000;

    private static final long REQUEST_TIMEOUT_CHECK_INTERVAL = 20000;

    private final Map<String, PendingRequest> pendingRequests = new HashMap<>();

    private static final String REQUEST_ID = "requestId";

    private static final String REQUEST_TIMED_OUT ="Request timed out";

    private static class PendingRequest
    {
        Message<JsonObject> message;

        long timestamp;

        PendingRequest(Message<JsonObject> message, long timestamp)
        {
            this.message = message;

            this.timestamp = timestamp;
        }
    }

    // Sets up a ZMQ DEALER socket for communication.
    // Registers the socket with a poller for incoming messages.
    // Listens for event bus messages on Constants.EVENTBUS_ZMQ_ADDRESS.
    // Starts periodic checks for responses and timeouts.
    @Override
    public void start(Promise<Void> startPromise)
    {
        logger.debug("zmq message verticle");

        try
        {
            context = ZMQ.context(1);

            push = context.socket(SocketType.PUSH);

            pull = context.socket(SocketType.PULL);

            boolean success = push.bind(Constants.ZMQ_PUSH_ADDRESS) && pull.bind(Constants.ZMQ_PULL_ADDRESS);

            if (!success)
            {
                startPromise.fail("Failed to bind PUSH or PULL socket");
            }
            else
            {
                pull.setReceiveTimeOut(0);

                vertx.eventBus().localConsumer(Constants.EVENTBUS_ZMQ_ADDRESS, this::handleRequest);

                vertx.setPeriodic(RESPONSE_CHECK_INTERVAL_MS, id -> checkResponses());

                vertx.setPeriodic(REQUEST_TIMEOUT_CHECK_INTERVAL, id -> checkTimeouts());

                startPromise.complete();
            }
        }
        catch (Exception e)
        {
            logger.error("Error starting ZMQ Verticle", e);

            startPromise.fail(e);
        }
    }

    // Handles incoming ZMQ requests.
    // Generates a unique request ID and adds it to the request.
    // Stores the request in the pendingRequests map with a timestamp.
    // Sends the request to the ZMQ dealer socket.
    // @param message The incoming message containing the ZMQ request.
    private void handleRequest(Message<JsonObject> message)
    {
        if(message.body().getString(Constants.REQUEST_TYPE).equalsIgnoreCase(Constants.DISCOVERY))
        {
            var requestId = UUID.randomUUID().toString();

            message.body().put(REQUEST_ID, requestId);

            pendingRequests.put(requestId, new PendingRequest(message, System.currentTimeMillis()));

        }

        if (!push.send(message.body().toString(),ZMQ.DONTWAIT))
        {
            logger.error("Sending Failed, queue is full");
        }
    }

     // Checks for and processes any incoming responses from the ZMQ dealer socket.
     // Polls for new responses.
     // Parses the response and matches it to a pending request using the request ID.
     private void checkResponses()
     {
         String response;

         while ((response = pull.recvStr()) != null)
         {
             if (response.trim().isEmpty())
             {
                 continue;
             }

             try
             {
                 var reply = new JsonObject(response);

                 if (reply.getString(Constants.REQUEST_TYPE).equalsIgnoreCase(Constants.DISCOVERY))
                 {
                     var pendingRequest = pendingRequests.remove(reply.getString(REQUEST_ID));

                     reply.remove(REQUEST_ID);

                     if (pendingRequest != null)
                     {
                         pendingRequest.message.reply(reply);
                     }
                     else
                     {
                         logger.error("No pending request found for request_id: {}", reply.getString(REQUEST_ID));
                     }
                 }
                 else
                 {
                     vertx.eventBus().send(Constants.EVENTBUS_POLLING_REPLY_ADDRESS, reply);
                 }
             }
             catch (Exception e)
             {
                 logger.error("Failed to parse response as JSON: {} from plugin", response, e);
             }
         }
     }

    // Checks for and handles any pending requests that have timed out.
    // If the request has exceeded the timeout threshold, it logs a warning and sends a failure response to the original message.
    // Removes the timed-out request from the pending requests map.
    private void checkTimeouts()
    {
        var timedOutRequests = pendingRequests.entrySet().stream()
                .filter(entry -> System.currentTimeMillis() - entry.getValue().timestamp >= REQUEST_TIMEOUT_MS)
                .toList();

        timedOutRequests.forEach(entry ->
        {
            logger.warn("Request {} timed out", entry.getKey());

            entry.getValue().message.fail(408, REQUEST_TIMED_OUT);

            pendingRequests.remove(entry.getKey());
        });
    }

    @Override
    public void stop(Promise<Void> stopPromise)
    {
        if (push != null) push.close();

        if (pull != null) pull.close();

        if (context != null) context.close();

        stopPromise.complete();
    }
}
