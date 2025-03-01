package io.vertx.nms.util;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import java.nio.charset.StandardCharsets;

public class ZmqClient extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ZmqClient.class);

    private ZMQ.Socket socket;
    private ZMQ.Context context;
    private static final int TIMEOUT_MS = 10000; // Set to 5 seconds

    @Override
    public void start(Promise<Void> startPromise) {
        context = ZMQ.context(1);
        socket = context.socket(ZMQ.REQ);

        socket.setReceiveTimeOut(TIMEOUT_MS);  // Set receive timeout
        socket.setSendTimeOut(TIMEOUT_MS);     // Set send timeout

        socket.connect("tcp://localhost:5555");

        vertx.eventBus().consumer("zmq.send", (Message<JsonObject> message) -> {
            logger.info(Thread.currentThread().getName() + " req in zmq: " + message.body());

            JsonObject zmqRequest = message.body();

            vertx.executeBlocking(promise -> {
                try {
                    logger.info(Thread.currentThread().getName() + " trying to send in socket");

                    boolean sent = socket.send(zmqRequest.encode().getBytes(StandardCharsets.UTF_8));

                    if (!sent) {
                        throw new RuntimeException("ZMQ Send Timeout");
                    }
                    promise.complete();
                } catch (Exception e) {
                    promise.fail(e);
                }
            }).onSuccess(v -> {
                vertx.executeBlocking(promise2 -> {
                    try {
                        logger.info(Thread.currentThread().getName() + " trying to receive in socket");

                        byte[] replyBytes = socket.recv(0);

                        if (replyBytes == null) {
                            throw new RuntimeException("ZMQ Receive Timeout");
                        }

                        String reply = new String(replyBytes, StandardCharsets.UTF_8);
                        promise2.complete(reply);
                    } catch (Exception e) {
                        promise2.fail(e);
                    }
                }).onSuccess(reply -> {
                    logger.info("received from zmq: " + reply);
                    message.reply(reply);
                }).onFailure(err -> {
                    logger.error("Failed to receive ZMQ reply: {}", err.getMessage());
                    message.fail(504, "No response from ZMQ server");
                });
            }).onFailure(err -> {
                logger.error("Failed to send ZMQ message: {}", err.getMessage());
                message.fail(503, "Failed to send ZMQ message");
            });
        });

        logger.info("ZmqClientVerticle deployed and listening on EventBus address: zmq.send");
        startPromise.complete();
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (socket != null) {
            socket.close();
        }
        if (context != null) {
            context.close();
        }
        stopPromise.complete();
    }
}
