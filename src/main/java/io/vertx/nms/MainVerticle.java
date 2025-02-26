package io.vertx.nms;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.nms.http.HttpServerVerticle;
import io.vertx.nms.service.CredentialService;
import io.vertx.nms.service.DatabaseService;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;


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

        PgConnectOptions connectOptions = new PgConnectOptions().setHost("localhost").setPort(5432).setDatabase("NMS_Lite").setUser("admin").setPassword("admin");

        PoolOptions poolOptions = new PoolOptions().setMaxSize(10);

        PgPool pgPool = PgPool.pool(vertx, connectOptions, poolOptions);

        vertx.deployVerticle(new CredentialService());

        vertx.deployVerticle(new DatabaseService(pgPool));
    }

    public static void main(String[] args)
    {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new MainVerticle());
    }
}
