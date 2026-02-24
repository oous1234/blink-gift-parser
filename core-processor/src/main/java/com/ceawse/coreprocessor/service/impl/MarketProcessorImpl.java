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
            case CANCELSALE -> currentSaleRepository.deleteByAddress(event.getAddress());
            case SOLD -> handleSold(event);
        }
    }

    private void handleSold(GiftHistoryDocument event) {
        currentSaleRepository.deleteByAddress(event.getAddress());
        soldGiftRepository.save(mapper.toSoldGift(event));
    }
}