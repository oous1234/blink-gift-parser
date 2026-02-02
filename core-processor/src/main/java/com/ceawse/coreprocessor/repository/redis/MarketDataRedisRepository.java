package com.ceawse.coreprocessor.repository.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class MarketDataRedisRepository {
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "nft:market:";
    private static final String FLOOR_COLLECTION = KEY_PREFIX + "floor:col:";
    private static final String FLOOR_MODEL = KEY_PREFIX + "floor:model:";
    private static final String STATS_AVG_MODEL = KEY_PREFIX + "stats:30d:avg:";

    // Флор коллекции
    public void saveCollectionFloor(String colAddr, BigDecimal price) {
        redisTemplate.opsForValue().set(FLOOR_COLLECTION + colAddr, price.toString(), Duration.ofHours(24));
    }

    public void saveModelDealsCount(String colAddr, String model, Long count) {
        String key = "nft:market:stats:30d:count:" + colAddr + ":" + model;
        redisTemplate.opsForValue().set(key, count.toString(), Duration.ofDays(7));
    }

    public BigDecimal getCollectionFloor(String colAddr) {
        String val = redisTemplate.opsForValue().get(FLOOR_COLLECTION + colAddr);
        return val != null ? new BigDecimal(val) : BigDecimal.ZERO;
    }

    // Флор конкретной модели (Ornament, и т.д.)
    public void saveModelFloor(String colAddr, String model, BigDecimal price) {
        String key = FLOOR_MODEL + colAddr + ":" + model;
        redisTemplate.opsForValue().set(key, price.toString(), Duration.ofHours(24));
    }

    public BigDecimal getModelFloor(String colAddr, String model) {
        String val = redisTemplate.opsForValue().get(FLOOR_MODEL + colAddr + ":" + model);
        return val != null ? new BigDecimal(val) : null;
    }

    public void saveModelAvgPrice(String colAddr, String model, BigDecimal avgPrice) {
        String key = STATS_AVG_MODEL + colAddr + ":" + model;
        redisTemplate.opsForValue().set(key, avgPrice.toString(), Duration.ofDays(7));
    }

    public BigDecimal getModelAvgPrice(String colAddr, String model) {
        String val = redisTemplate.opsForValue().get(STATS_AVG_MODEL + colAddr + ":" + model);
        return val != null ? new BigDecimal(val) : null;
    }
}