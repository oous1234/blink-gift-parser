package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.dto.MarketAttributeDataDto;
import com.ceawse.giftdiscovery.model.read.CollectionAttributeDocument;
import com.ceawse.giftdiscovery.model.read.CollectionRegistryDocument;
import com.ceawse.giftdiscovery.model.read.CollectionStatisticsDocument;
import com.ceawse.giftdiscovery.repository.redis.MarketDataRedisRepository;
import com.ceawse.giftdiscovery.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataServiceImpl implements MarketDataService {
    private final MongoTemplate mongoTemplate;
    private final MarketDataRedisRepository redisRepository;

    @Override
    public void refreshCache() {
        MDC.put("traceId", "REDIS-" + UUID.randomUUID().toString().substring(0, 8));
        MDC.put("context", "SYNC");
        log.info("Starting market data synchronization from MongoDB to Redis...");

        try {
            List<CollectionStatisticsDocument> stats = mongoTemplate.findAll(CollectionStatisticsDocument.class);
            int statsCount = 0;
            for (var s : stats) {
                if (s.getFloorPrice() != null) {
                    redisRepository.saveCollectionFloor(s.getCollectionAddress(), s.getFloorPrice());
                    statsCount++;
                }
            }

            List<CollectionAttributeDocument> attrs = mongoTemplate.findAll(CollectionAttributeDocument.class);
            int attrCount = 0;
            for (var a : attrs) {
                if (a.getPrice() != null) {
                    redisRepository.saveAttributePrice(a.getCollectionAddress(), a.getTraitType(), a.getValue(), a.getPrice());
                }
                if (a.getItemsCount() != null) {
                    redisRepository.saveAttributeCount(a.getCollectionAddress(), a.getTraitType(), a.getValue(), a.getItemsCount());
                }
                attrCount++;
            }
            log.info("Redis Sync Completed: {} collections floors, {} attributes data entries", statsCount, attrCount);
        } catch (Exception e) {
            log.error("Redis synchronization failed: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    @Override
    public String resolveCollectionAddress(String giftName, String providedAddress) {
        if (providedAddress != null && (providedAddress.startsWith("EQ") || providedAddress.length() == 36)) {
            return providedAddress;
        }
        if (giftName == null) return providedAddress;

        String baseName = giftName.split("#")[0].trim();
        log.trace("Resolving collection address for gift name: {}", baseName);

        String resolved = mongoTemplate.findAll(CollectionRegistryDocument.class).stream()
                .filter(c -> normalize(c.getName()).equals(normalize(baseName)) || normalize(c.getName()).equals(normalize(baseName + "s")))
                .map(CollectionRegistryDocument::getAddress)
                .findFirst()
                .orElse(providedAddress);

        if (resolved != null && !resolved.equals(providedAddress)) {
            log.debug("Collection resolved: {} -> {}", baseName, resolved);
        }
        return resolved;
    }

    private String normalize(String input) {
        if (input == null) return "";
        return input.toLowerCase().replaceAll("[\\s\\-']", "").replace("ies", "y");
    }

    @Override
    public BigDecimal getCollectionFloor(String collectionAddress) {
        return redisRepository.getCollectionFloor(collectionAddress);
    }

    @Override
    public MarketAttributeDataDto getAttributeData(String collectionAddress, String traitType, String value) {
        BigDecimal price = redisRepository.getAttributePrice(collectionAddress, traitType, value);
        Integer count = redisRepository.getAttributeCount(collectionAddress, traitType, value);
        return new MarketAttributeDataDto(price, count);
    }
}