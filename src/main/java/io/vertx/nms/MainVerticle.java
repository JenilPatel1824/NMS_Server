package io.vertx.nms;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.nms.database.DatabaseVerticle;
import io.vertx.nms.engine.PollingEngine;
import io.vertx.nms.http.HttpServerVerticle;
import io.vertx.nms.messaging.ZmqMessengerVerticle;


public class MainVerticle extends AbstractVerticle
{
    @Override
    public void start() {

        vertx.deployVerticle(new HttpServerVerticle())
                .onSuccess(id -> {

                })
                .onFailure(err ->
                {
                    System.err.println("Failed to deploy HTTP Server: " + err.getMessage());
                });

        vertx.deployVerticle(new DatabaseVerticle());

        vertx.deployVerticle(ZmqMessengerVerticle.class,new DeploymentOptions().setInstances(5));

        vertx.deployVerticle(new PollingEngine());


    }

    public static void main(String[] args)
    {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new MainVerticle());
    }
}
