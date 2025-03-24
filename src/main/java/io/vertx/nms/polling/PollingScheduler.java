package io.vertx.nms.polling;

import io.vertx.core.AbstractVerticle;
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

    private int currentOffset = 0;

    @Override
    public void start()
    {
        logger.info("Scheduler started");

        vertx.setPeriodic(3000, SCHEDULER_INTERVAL, id ->
        {
            fetchAllBatches();

        });
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
        var query = "SELECT p.id AS job_id, p.ip, c.system_type, c.credentials " +
                "FROM provisioning_jobs p JOIN credential_profile c ON p.credential_profile_id = c.id " +
                "ORDER BY p.id LIMIT $1 OFFSET $2";

        var params = new JsonArray().add(FETCH_BATCH_SIZE).add(currentOffset);

        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, new JsonObject().put(Constants.QUERY, query).put(Constants.PARAMS, params), reply ->
        {
            if (reply.succeeded())
            {
                var result = reply.result().body();

                var data = result.getJsonArray(Constants.DATA);

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
