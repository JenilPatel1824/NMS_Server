package io.vertx.nms.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;

public class ZmqClient {

    private static final Logger logger = LoggerFactory.getLogger(ZmqClient.class);


    public static String sendToZmq(String json)
    {
        logger.info(Thread.currentThread().getName() + " req in zmq: " + json);

        try (ZMQ.Context context = ZMQ.context(1); ZMQ.Socket socket = context.socket(ZMQ.REQ))
        {
            socket.connect("tcp://localhost:5555");

            logger.info("sending to zmq");

            socket.send(json);

            String reply = socket.recvStr();

            logger.info("received from zmq: " + reply);

            return reply;
        }
        catch (Exception e)
        {
            logger.error("ZMQ communication failed: {}", e.getMessage());

            return "error";
        }
    }

}

