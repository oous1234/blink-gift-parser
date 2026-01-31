package com.ceawse.coreprocessor.service.impl;

import com.ceawse.coreprocessor.model.CurrentSaleDocument;
import com.ceawse.coreprocessor.model.GiftHistoryDocument;
import com.ceawse.coreprocessor.model.MarketEventType;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketProcessorImpl implements MarketProcessor {

    private final CurrentSaleRepository currentSaleRepository;
    private final SoldGiftRepository soldGiftRepository;
    private final MarketDataRedisRepository marketDataRedis;
    private final MongoTemplate mongoTemplate;
    private final MarketEventMapper mapper;

    @Override
    @Transactional
    public void processEvent(GiftHistoryDocument event) {
        MarketEventType type = MarketEventType.fromString(event.getEventType());

        switch (type) {
            case PUTUPFORSALE, SNAPSHOT_LIST -> handleListing(event);
            case CANCELSALE -> currentSaleRepository.deleteByAddress(event.getAddress());
            case SOLD -> handleSold(event);
            case SNAPSHOT_FINISH -> handleSnapshotFinish(event);
            default -> log.debug("Event {} skipped", type);
        }
    }

    private void handleListing(GiftHistoryDocument event) {
        BigDecimal price = new BigDecimal(event.getPrice());
        BigDecimal floor = marketDataRedis.getCollectionFloor(event.getCollectionAddress());

        double dealScore = calculateDealScore(price, floor);

        CurrentSaleDocument sale = currentSaleRepository.findByAddress(event.getAddress())
                .orElseGet(() -> mapper.toCurrentSale(event));

        mapper.updateCurrentSale(sale, event);

        currentSaleRepository.save(sale);

        if (dealScore > 10.0) {
            log.info("🔥 GOOD DEAL: {} for {} TON (Floor: {}). Profit: {}%",
                    event.getName(), price, floor, String.format("%.2f", dealScore));
        }
    }

    private void handleSold(GiftHistoryDocument event) {
        currentSaleRepository.deleteByAddress(event.getAddress());
        soldGiftRepository.save(mapper.toSoldGift(event));
        log.debug("Item sold and moved to history: {}", event.getName());
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
        long startTime = Long.parseLong(event.getEventPayload());
        Instant threshold = Instant.ofEpochMilli(startTime);

        Query query = new Query(Criteria.where("marketplace").is(event.getMarketplace())
                .and("lastSnapshotId").ne(snapshotId)
                .and("updatedAt").lt(threshold));

        var result = mongoTemplate.remove(query, CurrentSaleDocument.class);
        log.info("Snapshot {} finalized. Removed {} stale listings.", snapshotId, result.getDeletedCount());
    }
}