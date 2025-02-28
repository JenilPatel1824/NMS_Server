package io.vertx.nms;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.nms.database.QueryExecutor;
import io.vertx.nms.http.HttpServerVerticle;


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

        vertx.deployVerticle(new QueryExecutor(),new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
    }

    public static void main(String[] args)
    {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new MainVerticle());
    }
}
