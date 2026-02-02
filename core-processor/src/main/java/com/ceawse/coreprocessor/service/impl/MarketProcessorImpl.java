package com.ceawse.coreprocessor.service.impl;

import com.ceawse.coreprocessor.client.CoreInternalClient;
import com.ceawse.coreprocessor.model.*;
import com.ceawse.coreprocessor.repository.CurrentSaleRepository;
import com.ceawse.coreprocessor.repository.SoldGiftRepository;
import com.ceawse.coreprocessor.repository.redis.MarketDataRedisRepository;
import com.ceawse.coreprocessor.service.MarketEventMapper;
import com.ceawse.coreprocessor.service.MarketProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketProcessorImpl implements MarketProcessor {
    private final CurrentSaleRepository currentSaleRepository;
    private final SoldGiftRepository soldGiftRepository;
    private final MarketDataRedisRepository marketDataRedis;
    private final MongoTemplate mongoTemplate;
    private final MarketEventMapper mapper;
    private final CoreInternalClient coreInternalClient;

    @Override
    @Transactional
    public void processEvent(GiftHistoryDocument event) {
        MarketEventType type = MarketEventType.fromString(event.getEventType());
        log.debug("Processing event: {} for item: {}", type, event.getName());

        switch (type) {
            case PUTUPFORSALE, SNAPSHOT_LIST -> handleListing(event);
            case CANCELSALE -> {
                currentSaleRepository.deleteByAddress(event.getAddress());
                log.info("Sale cancelled for item: {}", event.getName());
            }
            case SOLD -> handleSold(event);
            case SNAPSHOT_FINISH -> handleSnapshotFinish(event);
            default -> log.warn("Unknown event type {} for item {}", type, event.getName());
        }
    }

    private void handleListing(GiftHistoryDocument event) {
        BigDecimal price = new BigDecimal(event.getPrice() != null ? event.getPrice() : "0");
        String colAddr = event.getCollectionAddress();

        // 1. Обновляем флор модели в Redis, если атрибут есть
        if (event.getModel() != null) {
            BigDecimal currentModelFloor = marketDataRedis.getModelFloor(colAddr, event.getModel());
            if (currentModelFloor == null || currentModelFloor.compareTo(BigDecimal.ZERO) == 0 || price.compareTo(currentModelFloor) < 0) {
                marketDataRedis.saveModelFloor(colAddr, event.getModel(), price);
            }
        }

        // 2. Определяем опорную цену (флор модели или флор коллекции)
        BigDecimal baseFloor = null;
        if (event.getModel() != null) {
            baseFloor = marketDataRedis.getModelFloor(colAddr, event.getModel());
        }
        if (baseFloor == null || baseFloor.compareTo(BigDecimal.ZERO) == 0) {
            baseFloor = marketDataRedis.getCollectionFloor(colAddr);
        }

        double dealScore = calculateDealScore(price, baseFloor);

        // 3. Обновляем БД текущих продаж
        CurrentSaleDocument sale = currentSaleRepository.findByAddress(event.getAddress())
                .orElseGet(() -> mapper.toCurrentSale(event));
        mapper.updateCurrentSale(sale, event);
        currentSaleRepository.save(sale);

        // 4. Логирование и уведомление фронтенда
        if (dealScore > 0) {
            log.info("🔥 DEAL: {} (Model: {}) for {} TON (Floor: {}, Score: {}%)",
                    event.getName(), event.getModel(), price, baseFloor, Math.round(dealScore));
            notifyFrontend(event, price, baseFloor, dealScore);
        } else {
            log.info("New Listing: {} for {} TON", event.getName(), price);
        }
    }

    private void handleSold(GiftHistoryDocument event) {
        currentSaleRepository.deleteByAddress(event.getAddress());
        soldGiftRepository.save(mapper.toSoldGift(event));
        log.info("💰 ITEM SOLD: {} for {} TON", event.getName(), event.getPrice());
    }

    private void notifyFrontend(GiftHistoryDocument event, BigDecimal price, BigDecimal floor, double dealScore) {
        try {
            Map<String, Object> deal = new HashMap<>();
            deal.put("id", event.getHash());
            deal.put("name", event.getName());
            deal.put("model", event.getModel());
            deal.put("price", price);
            deal.put("floor", floor);
            deal.put("dealScore", Math.round(dealScore));
            deal.put("marketplace", event.getMarketplace());
            deal.put("timestamp", System.currentTimeMillis());

            coreInternalClient.sendDealToFront(deal);
        } catch (Exception e) {
            log.error("Failed to notify core-backend about listing: {}", e.getMessage());
        }
    }

    private double calculateDealScore(BigDecimal price, BigDecimal floor) {
        if (floor == null || floor.compareTo(BigDecimal.ZERO) <= 0) return 0.0;
        if (price.compareTo(floor) >= 0) return 0.0;
        return floor.subtract(price)
                .divide(floor, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private void handleSnapshotFinish(GiftHistoryDocument event) {
        String snapshotId = event.getSnapshotId();
        log.info("Finalizing snapshot: {}", snapshotId);
        long startTime = Long.parseLong(event.getEventPayload());
        Instant threshold = Instant.ofEpochMilli(startTime);

        Query query = new Query(Criteria.where("marketplace").is(event.getMarketplace())
                .and("lastSnapshotId").ne(snapshotId)
                .and("updatedAt").lt(threshold));

        var result = mongoTemplate.remove(query, CurrentSaleDocument.class);
        log.info("Snapshot {} finalized. Removed {} stale listings.", snapshotId, result.getDeletedCount());
    }
}