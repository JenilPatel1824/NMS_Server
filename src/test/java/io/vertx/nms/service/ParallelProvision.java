package io.vertx.nms.service;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

import java.util.List;

public class ParallelProvision extends AbstractVerticle {

    private final List<Integer> ids = List.of(
            210126, 210127, 210128, 210131, 210133, 210134,
            210136, 210139, 210140, 210141, 210145, 210146,
            210147, 210149, 210150
    );

    private static final String BASE_URL = "http://localhost:8080/provision/start/200089";

    @Override
    public void start() {
        WebClient client = WebClient.create(vertx);

        for (Integer id : ids) {
            String url = BASE_URL + "?id=" + id;

            System.out.println("Sending request for ID: " + id);

            client.getAbs(url)
                    .send()
                    .onSuccess(response ->
                            System.out.println("Request completed for ID " + id + ", Status: " + response.statusCode()))
                    .onFailure(err ->
                            System.out.println("Failed for ID " + id + ": " + err.getMessage()));
        }
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new ParallelProvision());
    }
}
