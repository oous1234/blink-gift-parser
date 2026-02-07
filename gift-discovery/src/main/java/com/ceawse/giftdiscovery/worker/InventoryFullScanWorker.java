package com.ceawse.giftdiscovery.worker;

import com.ceawse.giftdiscovery.client.PythonGatewayClient;
import com.ceawse.giftdiscovery.dto.external.PythonInventoryResponse;
import com.ceawse.giftdiscovery.service.EnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Воркер для полного фонового сканирования инвентаря пользователя.
 * Работает в Cold Path, не блокируя ответ пользователю.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryFullScanWorker {

    private final PythonGatewayClient pythonClient;
    private final EnrichmentService enrichmentService;
    private final MongoTemplate mongoTemplate;

    /**
     * Выполняет полный цикл сканирования всех подарков пользователя из Telegram.
     *
     * @param userId        ID пользователя Telegram
     * @param expectedTotal Общее количество подарков, заявленное Telegram
     */
    public void performFullScan(String userId, int expectedTotal) {
        log.info(">>> STARTING full inventory scan for user: {} (Expected items: {})", userId, expectedTotal);

        String cursor = "";
        int processedCount = 0;
        boolean hasMore = true;
        int batchSize = 500; // Оптимальный размер пачки для MTProto

        try {
            while (hasMore) {
                log.debug("Fetching batch for user {} from offset '{}'", userId, cursor);

                // 1. Запрашиваем пачку "легких" данных у Python Gateway
                PythonInventoryResponse batch = pythonClient.getInventoryLive(userId, cursor, batchSize);

                if (batch == null || batch.getItems() == null || batch.getItems().isEmpty()) {
                    log.info("No more items returned for user {}. Finishing scan.", userId);
                    break;
                }

                // 2. Отправляем пачку на обработку в EnrichmentService
                // Там произойдет проверка уникальности и докачка метаданных
                enrichmentService.processInventoryBatch(userId, batch.getItems());

                processedCount += batch.getItems().size();
                cursor = batch.getNext_offset();

                // Проверяем, есть ли следующая пачка
                hasMore = cursor != null && !cursor.isEmpty() && processedCount < batch.getTotal_count();

                log.info("Scan progress for user {}: {}/{} ({}%)",
                        userId, processedCount, batch.getTotal_count(),
                        (int)((double)processedCount / batch.getTotal_count() * 100));

                // Небольшая пауза, чтобы не нагружать MTProto сессию слишком сильно
                Thread.sleep(200);
            }

            // 3. Финализация: отмечаем успех в базе состояний
            markSyncAsCompleted(userId, processedCount);

        } catch (InterruptedException e) {
            log.error("Full scan interrupted for user {}: {}", userId, e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("CRITICAL error during full scan for user {}: {}", userId, e.getMessage(), e);
            markSyncAsFailed(userId);
        }
    }

    /**
     * Обновляет документ user_sync_state, выставляя статус COMPLETED.
     */
    private void markSyncAsCompleted(String userId, int actualCount) {
        try {
            Query query = new Query(Criteria.where("_id").is(userId));
            Update update = new Update()
                    .set("status", "COMPLETED")
                    .set("isFullScanCompleted", true)
                    .set("totalItemsCount", actualCount)
                    .set("lastSyncAt", Instant.now());

            // Пишем напрямую в коллекцию, которую читает Core
            mongoTemplate.updateFirst(query, update, "user_sync_state");

            log.info("<<< SUCCESS: Full scan completed for user: {}. Total items: {}", userId, actualCount);
        } catch (Exception e) {
            log.error("Failed to update sync state to COMPLETED for user {}: {}", userId, e.getMessage());
        }
    }

    private void markSyncAsFailed(String userId) {
        try {
            Query query = new Query(Criteria.where("_id").is(userId));
            Update update = new Update()
                    .set("status", "FAILED")
                    .set("lastSyncAt", Instant.now());

            mongoTemplate.updateFirst(query, update, "user_sync_state");
        } catch (Exception e) {
            log.error("Failed to update sync state to FAILED for user {}: {}", userId, e.getMessage());
        }
    }
}