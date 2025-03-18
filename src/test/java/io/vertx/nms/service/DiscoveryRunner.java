package io.vertx.nms;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

import java.util.Iterator;
import java.util.List;

public class DiscoveryRunner extends AbstractVerticle {

    private final List<Integer> ids = List.of(
            210128, 210129, 210130, 210131, 210132, 210133, 210134, 210135,
            210136, 210137, 210138, 210139, 210140, 210141, 210142, 210143,
            210144, 210145, 210146, 210147, 210148, 210149, 210150
    );

    @Override
    public void start() {
        WebClient client = WebClient.create(vertx);
        Iterator<Integer> iterator = ids.iterator();

        sendNext(client, iterator);
    }

    private void sendNext(WebClient client, Iterator<Integer> iterator) {
        if (!iterator.hasNext()) {
            System.out.println("All requests completed.");
            return;
        }

        Integer id = iterator.next();
        String url = "/discovery/" + id + "/run";

        System.out.println("Sending request for ID: " + id);

        client.post(8080, "localhost", url)
                .send()
                .onSuccess(response -> {
                    System.out.println("Request completed for ID " + id + ", Status: " + response.statusCode());
                    sendNext(client, iterator); // Send the next request after this one completes
                })
                .onFailure(err -> {
                    System.out.println("Failed for ID " + id + ": " + err.getMessage());
                    sendNext(client, iterator); // Continue with the next request even if one fails
                });
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new DiscoveryRunner());
    }
}
