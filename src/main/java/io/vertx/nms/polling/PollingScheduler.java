package io.vertx.nms.polling;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.nms.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollingScheduler extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(PollingScheduler.class);

    private static final int FETCH_BATCH_SIZE = 1000;

    private static final long SCHEDULER_INTERVAL = 300_000;

    private static final String FETCH_BATCH_QUERY = "SELECT p.id AS job_id, p.ip, p.port, c.system_type, c.credentials " +
            "FROM provisioning_jobs p JOIN credential_profile c ON p.credential_profile_id = c.id " +
            "where p.deleted = FALSE "+
            "ORDER BY p.id LIMIT $1 OFFSET $2";

    private int currentOffset = 0;

    private final JsonObject queryRequest = new JsonObject();

    private final JsonArray queryParams = new JsonArray();

    @Override
    public void start(Promise<Void> startPromise)
    {
        logger.info("Scheduler started");

        vertx.setPeriodic(SCHEDULER_INTERVAL, id ->
        {
            fetchAllBatches();

        });

        startPromise.complete();
    }

    // Resets the offset and initiates fetching of data in batches.
    // Ensures batch retrieval starts from the beginning.
    private void fetchAllBatches()
    {
        currentOffset = 0;

        fetchBatch();
    }

    // Fetches a batch of provisioning jobs from the database using pagination.
    // Sends the fetched batch to the polling event bus and continues fetching until all data is processed.
    private void fetchBatch()
    {
        queryParams.clear();

        queryRequest.clear();

        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, queryRequest.put(Constants.QUERY, FETCH_BATCH_QUERY).put(Constants.PARAMS, queryParams.add(FETCH_BATCH_SIZE).add(currentOffset)), reply ->
        {
            if (reply.succeeded())
            {
                var data = reply.result().body().getJsonArray(Constants.DATA);

                if (data != null && !data.isEmpty())
                {
                    logger.info("Scheduling batch of {} devices (Offset: {})", data.size(), currentOffset);

                    vertx.eventBus().send(Constants.EVENTBUS_POLLING_BATCH_ADDRESS, data);

                    if (data.size() < FETCH_BATCH_SIZE)
                    {
                        logger.info("Last batch fetched (Size: {}). Stopping further fetches.", data.size());
                    }
                    else
                    {
                        currentOffset += FETCH_BATCH_SIZE;

                        fetchBatch();
                    }
                }
                else
                {
                    logger.info("All batches processed. Resetting offset.");
                }
            }
            else
            {
                logger.error("Batch fetch failed: {}", reply.cause().getMessage());
            }
        });
    }
}
