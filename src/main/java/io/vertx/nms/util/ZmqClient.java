package io.vertx.nms.util;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.nms.http.router.DiscoveryRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ZmqClient extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ZmqClient.class);

    private ZMQ.Context context;
    private ZMQ.Socket dealer;
    private ZMQ.Poller poller;  // Persistent poller
    private static final int POLLING_INTERVAL_MS = 500; // Reduced to check frequently
    private Map<String, Message<JsonObject>> pendingRequests = new HashMap<>();

    @Override
    public void start(Promise<Void> startPromise) {
        context = ZMQ.context(1);
        dealer = context.socket(ZMQ.DEALER);
        dealer.setReceiveTimeOut(0);  // Non-blocking mode
        dealer.connect("tcp://localhost:5555");

        poller = context.poller(1);
        poller.register(dealer, ZMQ.Poller.POLLIN);

        vertx.eventBus().consumer("zmq.send", this::handleRequest);

        vertx.setPeriodic(POLLING_INTERVAL_MS, id -> checkResponses());

        startPromise.complete();
    }

    private void handleRequest(Message<JsonObject> message) {
        JsonObject request = message.body();
        String requestId = request.getString("request_id", UUID.randomUUID().toString());
        request.put("request_id", requestId);

        logger.info("zmq.send wakedup "+request);

        pendingRequests.put(requestId, message);

        dealer.send("", ZMQ.SNDMORE);  // Empty identity
        dealer.send(request.toString());

//        logger.info("sent "+sent);
//        if (!sent) {
//            message.fail(500, "Failed to send request via ZMQ");
//            pendingRequests.remove(requestId); // Cleanup failed request
//        }
    }

    private void checkResponses() {
        if (poller.poll(0) > 0) {
            while (poller.pollin(0)) {
                String response = dealer.recvStr();

                if (response == null || response.trim().isEmpty()) {
                    return;
                }

                try {
                    JsonObject replyJson = new JsonObject(response);
                    String requestId = replyJson.getString("request_id");

                    Message<JsonObject> originalMessage = pendingRequests.remove(requestId);
                    if (originalMessage != null) {
                        originalMessage.reply(replyJson);
                    } else {
                        logger.warn("No pending request found for request_id: " + requestId);
                    }
                } catch (Exception e) {
                    logger.error("Failed to parse response as JSON: " + response, e);
                }
            }
        }
    }


    @Override
    public void stop(Promise<Void> stopPromise) {
        if (dealer != null) {
            dealer.close();
        }
        if (context != null) {
            context.close();
        }
        stopPromise.complete();
    }
}
