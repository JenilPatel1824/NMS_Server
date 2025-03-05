package io.vertx.nms.messaging;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.nms.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ZmqMessengerVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(ZmqMessengerVerticle.class);

    private ZMQ.Context context;

    private ZMQ.Socket dealer;

    private static final int RESPONSE_CHECK_INTERVAL_MS = 500;

    private static final long REQUEST_TIMEOUT_MS = 59000;

    private static final long REQUEST_TIMEOUT_CHECK_INTERVAL = 10000;

    private Map<String, PendingRequest> pendingRequests = new HashMap<>();

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
    // Listens for event bus messages on "zmq.send".
    // Starts periodic checks for responses and timeouts.
    @Override
    public void start(Promise<Void> startPromise)
    {
        logger.debug("zmq message verticle "+Thread.currentThread().getName());

        context = ZMQ.context(1);

        dealer = context.socket(ZMQ.DEALER);

        dealer.setReceiveTimeOut(0);

        dealer.connect(Config.ZMQ_ADDRESS);

        vertx.eventBus().consumer("zmq.send", this::handleRequest);

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
        logger.info(Thread.currentThread().getName()+" zmq.send request");

        JsonObject request = message.body();

        String requestId = request.getString("request_id", UUID.randomUUID().toString());

        request.put("request_id", requestId);

        pendingRequests.put(requestId, new PendingRequest(message, System.currentTimeMillis()));

        dealer.send("", ZMQ.SNDMORE);

        dealer.send(request.toString());
    }

     // Checks for and processes any incoming responses from the ZMQ dealer socket.
     // Polls for new responses.
     // Parses the response and matches it to a pending request using the request ID.
     private void checkResponses()
     {
         String response;

         // Keep reading while messages are available
         while ((response = dealer.recvStr(ZMQ.DONTWAIT)) != null)
         {
             if (response.trim().isEmpty())
             {
                 continue;
             }

             try
             {
                 JsonObject replyJson = new JsonObject(response);

                 String requestId = replyJson.getString("request_id");

                 replyJson.remove("request_id");

                 PendingRequest pendingRequest = pendingRequests.remove(requestId);

                 if (pendingRequest != null)
                 {
                     logger.info(Thread.currentThread().getName() + " Replying ");

                     pendingRequest.message.reply(replyJson);
                 }
                 else
                 {
                     logger.warn("No pending request found for request_id: " + requestId);
                 }
             }
             catch (Exception e)
             {
                 logger.error("Failed to parse response as JSON: " + response, e);
             }
         }
     }

    // Checks for and handles any pending requests that have timed out.
     // If the request has exceeded the timeout threshold, it logs a warning and sends a failure response to the original message.
     // Removes the timed-out request from the pending requests map.
    private void checkTimeouts()
    {
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<String, PendingRequest>> iterator = pendingRequests.entrySet().iterator();

        while (iterator.hasNext())
        {
            Map.Entry<String, PendingRequest> entry = iterator.next();

            PendingRequest pendingRequest = entry.getValue();

            if (now - pendingRequest.timestamp >= REQUEST_TIMEOUT_MS)
            {
                logger.warn("Request {} timed out", entry.getKey());

                pendingRequest.message.fail(408, "Request timed out");

                iterator.remove();
            }
        }
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
