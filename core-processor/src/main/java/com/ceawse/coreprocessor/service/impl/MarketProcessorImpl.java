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
import org.springframework.data.redis.core.RedisTemplate;
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
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public void processEvent(GiftHistoryDocument event) {
        MarketEventType type = MarketEventType.fromString(event.getEventType());
        switch (type) {
            case PUTUPFORSALE, SNAPSHOT_LIST -> handleListing(event);
            case CANCELSALE -> currentSaleRepository.deleteByAddress(event.getAddress());
            case SOLD -> handleSold(event);
            case SNAPSHOT_FINISH -> handleSnapshotFinish(event);
        }
    }

    private void handleListing(GiftHistoryDocument event) {
        BigDecimal price = new BigDecimal(event.getPrice() != null ? event.getPrice() : "0");
        String colAddr = event.getCollectionAddress();

        if (event.getModel() != null) {
            BigDecimal currentModelFloor = marketDataRedis.getModelFloor(colAddr, event.getModel());
            if (currentModelFloor == null || currentModelFloor.compareTo(BigDecimal.ZERO) == 0 || price.compareTo(currentModelFloor) < 0) {
                marketDataRedis.saveModelFloor(colAddr, event.getModel(), price);
            }
        }

        CurrentSaleDocument sale = currentSaleRepository.findByAddress(event.getAddress())
                .orElseGet(() -> mapper.toCurrentSale(event));
        mapper.updateCurrentSale(sale, event);
        currentSaleRepository.save(sale);

        publishToListingTopic(event, price);

        BigDecimal baseFloor = null;
        if (event.getModel() != null) baseFloor = marketDataRedis.getModelFloor(colAddr, event.getModel());
        if (baseFloor == null || baseFloor.compareTo(BigDecimal.ZERO) == 0) baseFloor = marketDataRedis.getCollectionFloor(colAddr);

        double dealScore = calculateDealScore(price, baseFloor);
        if (dealScore > 0) {
            notifyFrontend(event, price, baseFloor, dealScore);
        }
    }

    private void publishToListingTopic(GiftHistoryDocument event, BigDecimal price) {
        try {
            ListingEvent listing = ListingEvent.builder()
                    .id(event.getHash())
                    .name(event.getName())
                    .model(event.getModel())
                    .backdrop(event.getBackdrop())
                    .symbol(event.getSymbol())
                    .price(price)
                    .marketplace(event.getMarketplace())
                    .address(event.getAddress())
                    .isOffchain(Boolean.TRUE.equals(event.getIsOffchain()))
                    .timestamp(event.getTimestamp())
                    .build();

            redisTemplate.convertAndSend("listing_events", listing);
        } catch (Exception e) {
            log.error("Failed to publish listing event to Redis: {}", e.getMessage());
        }
    }

    private void handleSold(GiftHistoryDocument event) {
        currentSaleRepository.deleteByAddress(event.getAddress());
        soldGiftRepository.save(mapper.toSoldGift(event));
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
            log.error("Failed to notify core: {}", e.getMessage());
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
        long startTime = Long.parseLong(event.getEventPayload());
        Instant threshold = Instant.ofEpochMilli(startTime);
        Query query = new Query(Criteria.where("marketplace").is(event.getMarketplace())
                .and("lastSnapshotId").ne(snapshotId)
                .and("updatedAt").lt(threshold));
        mongoTemplate.remove(query, CurrentSaleDocument.class);
    }
}