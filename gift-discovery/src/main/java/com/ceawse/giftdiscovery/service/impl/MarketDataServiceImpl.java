package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.dto.MarketAttributeDataDto;
import com.ceawse.giftdiscovery.model.read.CollectionAttributeDocument;
import com.ceawse.giftdiscovery.model.read.CollectionRegistryDocument;
import com.ceawse.giftdiscovery.model.read.CollectionStatisticsDocument;
import com.ceawse.giftdiscovery.repository.redis.MarketDataRedisRepository;
import com.ceawse.giftdiscovery.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataServiceImpl implements MarketDataService {
    private final MongoTemplate mongoTemplate;
    private final MarketDataRedisRepository redisRepository;

    @Override
    public void refreshCache() {
        log.info("Starting global market data synchronization to Redis...");
        try {
            List<CollectionStatisticsDocument> stats = mongoTemplate.findAll(CollectionStatisticsDocument.class);
            stats.forEach(s -> {
                if (s.getFloorPrice() != null) {
                    redisRepository.saveCollectionFloor(s.getCollectionAddress(), s.getFloorPrice());
                }
            });

            List<CollectionAttributeDocument> attrs = mongoTemplate.findAll(CollectionAttributeDocument.class);
            attrs.forEach(a -> {
                if (a.getPrice() != null) {
                    redisRepository.saveAttributePrice(
                            a.getCollectionAddress(),
                            a.getTraitType(),
                            a.getValue(),
                            a.getPrice()
                    );
                }
                if (a.getItemsCount() != null) {
                    redisRepository.saveAttributeCount(
                            a.getCollectionAddress(),
                            a.getTraitType(),
                            a.getValue(),
                            a.getItemsCount()
                    );
                }
            });
            log.info("Successfully synced {} collections and {} attributes to Redis", stats.size(), attrs.size());
        } catch (Exception e) {
            log.error("Critical error during Redis synchronization", e);
        }
    }

    @Override
    public String resolveCollectionAddress(String giftName, String providedAddress) {
        if (providedAddress != null && (providedAddress.startsWith("EQ") || providedAddress.length() == 36)) {
            return providedAddress;
        }
        if (giftName == null) return providedAddress;
        String baseName = giftName.split("#")[0].trim().toLowerCase().replaceAll("[\\s\\-']", "");
        return mongoTemplate.findAll(CollectionRegistryDocument.class).stream()
                .filter(c -> normalize(c.getName()).equals(baseName) || normalize(c.getName()).equals(baseName + "s"))
                .map(CollectionRegistryDocument::getAddress)
                .findFirst()
                .orElse(providedAddress);
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