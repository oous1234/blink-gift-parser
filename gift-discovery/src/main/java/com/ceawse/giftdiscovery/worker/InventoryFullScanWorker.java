package com.ceawse.giftdiscovery.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryFullScanWorker {

    private final PythonGatewayClient pythonClient;
    private final EnrichmentService enrichmentService;

    /**
     * Процесс полного сканирования инвентаря пользователя.
     */
    public void performFullScan(String userId, int expectedTotal) {
        log.info("Starting full inventory scan for user: {}, expected: {}", userId, expectedTotal);

        String cursor = "";
        int processedCount = 0;
        boolean hasMore = true;

        while (hasMore) {
            PythonInventoryResponse batch = pythonClient.getInventoryLive(userId, cursor, 500);

            if (batch.getItems() == null || batch.getItems().isEmpty()) break;

            enrichmentService.processInventoryBatch(userId, batch.getItems());

            processedCount += batch.getItems().size();
            cursor = batch.getNext_offset();
            hasMore = cursor != null && !cursor.isEmpty();

            log.debug("Progress for {}: {}/{}", userId, processedCount, expectedTotal);
        }

        log.info("Finished full scan for user: {}", userId);
    }
}