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

    private ZMQ.Poller poller;

    private static final int POLLING_INTERVAL_MS = 500;

    private static final long REQUEST_TIMEOUT_MS = 20_000;

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

    @Override
    public void start(Promise<Void> startPromise)
    {
        logger.info("zmq message verticle "+Thread.currentThread().getName());

        context = ZMQ.context(1);

        dealer = context.socket(ZMQ.DEALER);

        dealer.setReceiveTimeOut(0);

        dealer.connect(Config.ZMQ_ADDRESS);

        poller = context.poller(1);

        poller.register(dealer, ZMQ.Poller.POLLIN);

        vertx.eventBus().consumer("zmq.send", this::handleRequest);

        vertx.setPeriodic(POLLING_INTERVAL_MS, id -> {
            checkResponses();

            checkTimeouts();
        });

        startPromise.complete();

    }

    private void handleRequest(Message<JsonObject> message)
    {
        JsonObject request = message.body();

        String requestId = request.getString("request_id", UUID.randomUUID().toString());

        request.put("request_id", requestId);

        logger.info(Thread.currentThread().getName()+" zmq.send request: after putting req id {}", request);

        pendingRequests.put(requestId, new PendingRequest(message, System.currentTimeMillis()));

        dealer.send("", ZMQ.SNDMORE);

        dealer.send(request.toString());
    }

    private void checkResponses()
    {
        if (poller.poll(0) > 0)
        {
            while (poller.pollin(0))
            {
                String response = dealer.recvStr();

                if (response == null || response.trim().isEmpty())
                {
                    return;
                }

                try
                {
                    JsonObject replyJson = new JsonObject(response);

                    String requestId = replyJson.getString("request_id");

                    PendingRequest pendingRequest = pendingRequests.remove(requestId);

                    if (pendingRequest != null)
                    {
                        logger.info(Thread.currentThread().getName()+" Replying "+replyJson);

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
    }

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

                pendingRequest.message.fail(408, "Request timed out after 60 seconds");

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
