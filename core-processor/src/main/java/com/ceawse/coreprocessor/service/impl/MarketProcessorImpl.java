package com.ceawse.coreprocessor.service.impl;

import com.ceawse.coreprocessor.dto.ListingEvent;
import com.ceawse.coreprocessor.model.*;
import com.ceawse.coreprocessor.repository.CurrentSaleRepository;
import com.ceawse.coreprocessor.repository.SoldGiftRepository;
import com.ceawse.coreprocessor.service.MarketEventMapper;
import com.ceawse.coreprocessor.service.MarketProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketProcessorImpl implements MarketProcessor {
    private final CurrentSaleRepository currentSaleRepository;
    private final SoldGiftRepository soldGiftRepository;
    private final MongoTemplate mongoTemplate;
    private final MarketEventMapper mapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_CHANNEL = "listing_events";

    @Override
    public void processEvent(GiftHistoryDocument event) {
        MarketEventType type = MarketEventType.fromString(event.getEventType());
        if (type == MarketEventType.PUTUPFORSALE || type == MarketEventType.SNAPSHOT_LIST) {
            handleListing(event);
        } else if (type == MarketEventType.CANCELSALE) {
            currentSaleRepository.deleteByAddress(event.getAddress());
        } else if (type == MarketEventType.SOLD) {
            handleSold(event);
        }
    }

    private void handleListing(GiftHistoryDocument event) {
        BigDecimal price = new BigDecimal(event.getPrice() != null ? event.getPrice() : "0");

        CurrentSaleDocument sale = currentSaleRepository.findByAddress(event.getAddress())
                .orElseGet(() -> mapper.toCurrentSale(event));
        mapper.updateCurrentSale(sale, event);
        currentSaleRepository.save(sale);

        MarketListingDocument history = MarketListingDocument.builder()
                .id(event.getHash()).name(event.getName()).model(event.getModel())
                .backdrop(event.getBackdrop()).symbol(event.getSymbol())
                .price(price).currency(event.getCurrency()).marketplace(event.getMarketplace())
                .address(event.getAddress()).createdAt(new Date()).build();
        mongoTemplate.save(history);

        ListingEvent redisEvent = ListingEvent.builder()
                .id(event.getHash()).name(event.getName()).model(event.getModel())
                .backdrop(event.getBackdrop()).symbol(event.getSymbol())
                .price(price).marketplace(event.getMarketplace()).address(event.getAddress())
                .timestamp(System.currentTimeMillis()).build();

        redisTemplate.convertAndSend(REDIS_CHANNEL, redisEvent);
        log.debug("Published listing to Redis: {}", event.getName());
    }

    private void handleSold(GiftHistoryDocument event) {
        currentSaleRepository.deleteByAddress(event.getAddress());
        soldGiftRepository.save(mapper.toSoldGift(event));
    }
}