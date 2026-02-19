package com.ceawse.giftdiscovery.worker;

import com.ceawse.giftdiscovery.client.PythonGatewayClient;
import com.ceawse.giftdiscovery.dto.external.PythonInventoryResponse;
import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.model.UserInventoryDocument;
import com.ceawse.giftdiscovery.repository.UniqueGiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryFullScanWorker {

    private final PythonGatewayClient pythonClient;
    private final UniqueGiftRepository uniqueGiftRepository;
    private final MongoTemplate mongoTemplate;

    public void performFullScan(String userId, int expectedTotal) {
        log.info("Starting full inventory scan and persistence for user: {}", userId);
        String cursor = "";
        int processedCount = 0;

        try {
            // Очищаем старый инвентарь перед полной пересинхронизацией
            mongoTemplate.remove(new Query(Criteria.where("userId").is(userId)), "user_inventory");

            while (processedCount < expectedTotal) {
                PythonInventoryResponse batch = pythonClient.getInventoryLive(userId, cursor, 50);
                if (batch.getItems().isEmpty()) break;

                BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UserInventoryDocument.class);

                for (var item : batch.getItems()) {
                    // Ищем метаданные в глобальном реестре
                    UniqueGiftDocument globalGift = uniqueGiftRepository.findById(item.getSlug()).orElse(null);

                    UserInventoryDocument invItem = UserInventoryDocument.builder()
                            .id(item.getNft_address() != null ? item.getNft_address() : item.getGift_id())
                            .userId(userId)
                            .slug(item.getSlug())
                            .name(globalGift != null ? globalGift.getName() : "Gift #" + item.getSerial_number())
                            .serialNumber(item.getSerial_number())
                            .model(globalGift != null ? globalGift.getModel() : "Unknown")
                            .backdrop(globalGift != null ? globalGift.getBackdrop() : "Unknown")
                            .symbol(globalGift != null ? globalGift.getSymbol() : "Unknown")
                            .estimatedPrice(globalGift != null && globalGift.getMarketData() != null ?
                                    globalGift.getMarketData().getEstimatedPrice() : null)
                            .acquiredAt(item.getDate())
                            .image("https://nft.fragment.com/gift/" + item.getSlug().toLowerCase() + ".medium.jpg")
                            .build();

                    bulkOps.insert(invItem);
                }
                bulkOps.execute();

                processedCount += batch.getItems().size();
                cursor = batch.getNext_offset();
                if (cursor == null || cursor.isBlank()) break;
            }

            markSyncAsCompleted(userId, processedCount);
        } catch (Exception e) {
            log.error("Failed full scan for user {}: {}", userId, e.getMessage());
            markSyncAsFailed(userId);
        }
    }

    private void markSyncAsCompleted(String userId, int actualCount) {
        Query query = new Query(Criteria.where("_id").is(userId));
        Update update = new Update()
                .set("status", "COMPLETED")
                .set("isFullScanCompleted", true)
                .set("totalItemsCount", actualCount)
                .set("lastSyncAt", Instant.now());
        mongoTemplate.updateFirst(query, update, "user_sync_state");
    }

    private void markSyncAsFailed(String userId) {
        Query query = new Query(Criteria.where("_id").is(userId));
        Update update = new Update()
                .set("status", "FAILED")
                .set("lastSyncAt", Instant.now());
        mongoTemplate.updateFirst(query, update, "user_sync_state");
    }
}