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

    private ZMQ.Socket dealer;

    private static final int RESPONSE_CHECK_INTERVAL_MS = 500;

    private static final long REQUEST_TIMEOUT_MS = 258000;

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
        logger.debug(" zmq message verticle ");

        context = ZMQ.context(1);

        dealer = context.socket(SocketType.DEALER);

        dealer.setReceiveTimeOut(0);

        dealer.setHWM(0);

        dealer.connect(Constants.ZMQ_ADDRESS);

        vertx.eventBus().<JsonObject>localConsumer(Constants.EVENTBUS_ZMQ_ADDRESS, this::handleRequest);

        vertx.setPeriodic(RESPONSE_CHECK_INTERVAL_MS, id ->
        {
            checkResponses();

        });
        vertx.setPeriodic(REQUEST_TIMEOUT_CHECK_INTERVAL, id ->
        {
            checkTimeouts();
        });

        startPromise.complete();
    }

    // Handles incoming ZMQ requests.
    // Generates a unique request ID and adds it to the request.
    // Stores the request in the pendingRequests map with a timestamp.
    // Sends the request to the ZMQ dealer socket.
    // @param message The incoming message containing the ZMQ request.
    private void handleRequest(Message<JsonObject> message)
    {
        var requestId =  UUID.randomUUID().toString();

        message.body().put(REQUEST_ID, requestId);

        pendingRequests.put(requestId, new PendingRequest(message, System.currentTimeMillis()));

        dealer.send("", ZMQ.SNDMORE);

        dealer.send(message.body().toString());
    }

     // Checks for and processes any incoming responses from the ZMQ dealer socket.
     // Polls for new responses.
     // Parses the response and matches it to a pending request using the request ID.
     private void checkResponses()
     {
         String response;

         while ((response = dealer.recvStr(ZMQ.DONTWAIT)) != null)
         {
             if (response.trim().isEmpty())
             {
                 continue;
             }

             try
             {
                 var replyJson = new JsonObject(response);

                 var requestId = replyJson.getString(REQUEST_ID);

                 replyJson.remove(REQUEST_ID);

                 var pendingRequest = pendingRequests.remove(requestId);

                 if (pendingRequest != null)
                 {
                     pendingRequest.message.reply(replyJson);

                 }
                 else
                 {
                     logger.error("No pending request found for request_id: {}", requestId);
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
        var now = System.currentTimeMillis();

        var timedOutRequests = pendingRequests.entrySet().stream()
                .filter(entry -> now - entry.getValue().timestamp >= REQUEST_TIMEOUT_MS)
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
        if (dealer != null)
        {
            dealer.close();
        }

        if (context != null)
        {
            context.close();
        }

        stopPromise.complete();
    }
}
