package com.ceawse.giftdiscovery.repository.redis;

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
    private static final String FLOOR_KEY = KEY_PREFIX + "floor:";
    private static final String ATTR_KEY = KEY_PREFIX + "attr:";
    private static final String ATTR_COUNT_KEY = KEY_PREFIX + "attr_count:";

    public void saveCollectionFloor(String collectionAddress, BigDecimal price) {
        String key = FLOOR_KEY + collectionAddress;
        redisTemplate.opsForValue().set(key, price.toString(), Duration.ofHours(24));
    }

    public BigDecimal getCollectionFloor(String collectionAddress) {
        String val = redisTemplate.opsForValue().get(FLOOR_KEY + collectionAddress);
        return val != null ? new BigDecimal(val) : BigDecimal.ZERO;
    }

    public void saveAttributePrice(String collectionAddress, String traitType, String value, BigDecimal price) {
        String key = String.format("%s%s:%s:%s", ATTR_KEY, collectionAddress, traitType, value);
        redisTemplate.opsForValue().set(key, price.toString(), Duration.ofHours(24));
    }

    public BigDecimal getAttributePrice(String collectionAddress, String traitType, String value) {
        String key = String.format("%s%s:%s:%s", ATTR_KEY, collectionAddress, traitType, value);
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? new BigDecimal(val) : null;
    }

    public void saveAttributeCount(String collectionAddress, String traitType, String value, Integer count) {
        String key = String.format("%s%s:%s:%s", ATTR_COUNT_KEY, collectionAddress, traitType, value);
        redisTemplate.opsForValue().set(key, count.toString(), Duration.ofHours(24));
    }

    public Integer getAttributeCount(String collectionAddress, String traitType, String value) {
        String key = String.format("%s%s:%s:%s", ATTR_COUNT_KEY, collectionAddress, traitType, value);
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Integer.parseInt(val) : 0;
    }
}