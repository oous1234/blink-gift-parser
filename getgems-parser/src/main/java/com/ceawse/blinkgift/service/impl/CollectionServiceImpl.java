package com.ceawse.blinkgift.service.impl;

import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.client.IndexerApiClient;
import com.ceawse.blinkgift.domain.CollectionAttributeDocument;
import com.ceawse.blinkgift.domain.CollectionStatisticsDocument;
import com.ceawse.blinkgift.repository.CollectionAttributeRepository;
import com.ceawse.blinkgift.repository.CollectionStatisticsRepository;
import com.ceawse.blinkgift.service.CollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionServiceImpl implements CollectionService {
    private final IndexerApiClient indexerClient;
    private final GetGemsApiClient getGemsClient;
    private final CollectionAttributeRepository attributeRepository;
    private final CollectionStatisticsRepository statisticsRepository;

    @Override
    public void updateAllCollectionsAttributes() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", "ATTR-" + traceId);
        log.info("Starting collection attributes update cycle");

        try {
            var collections = indexerClient.getCollections();
            for (var col : collections) {
                MDC.put("context", col.address);
                processAttributes(col.address);
                Thread.sleep(300);
            }
        } catch (Exception e) {
            log.error("Error in attributes update cycle: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void updateAllCollectionsStatistics() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", "STAT-" + traceId);
        log.info("Starting collection statistics update cycle");

        try {
            var collections = indexerClient.getCollections();
            for (var col : collections) {
                MDC.put("context", col.address);
                processStatistics(col.address);
                Thread.sleep(300);
            }
        } catch (Exception e) {
            log.error("Error in statistics update cycle: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    private void processAttributes(String collectionAddress) {
        try {
            var response = getGemsClient.getAttributes(collectionAddress);
            if (response == null || !response.isSuccess() || response.getResponse() == null) return;

            List<CollectionAttributeDocument> docs = new ArrayList<>();
            Instant now = Instant.now();
            var categories = response.getResponse().getAttributes();

            if (categories == null) return;

            for (var category : categories) {
                if (category.getValues() == null) continue;
                for (var val : category.getValues()) {
                    docs.add(CollectionAttributeDocument.builder()
                            .id(CollectionAttributeDocument.generateId(collectionAddress, category.getTraitType(), val.getValue()))
                            .collectionAddress(collectionAddress)
                            .traitType(category.getTraitType())
                            .value(val.getValue())
                            .price(parseBigDecimal(val.getMinPrice()))
                            .itemsCount(val.getCount())
                            .updatedAt(now)
                            .build());
                }
            }
            if (!docs.isEmpty()) {
                attributeRepository.saveAll(docs);
                log.debug("Updated {} attributes", docs.size());
            }
        } catch (Exception e) {
            log.error("Failed to update attributes: {}", e.getMessage());
        }
    }

    private void processStatistics(String address) {
        try {
            var dto = getGemsClient.getCollectionStats(address);
            if (dto == null || !dto.isSuccess() || dto.getResponse() == null) return;

            var stats = dto.getResponse();
            CollectionStatisticsDocument doc = CollectionStatisticsDocument.builder()
                    .collectionAddress(address)
                    .floorPrice(parseBigDecimal(stats.getFloorPrice()))
                    .itemsCount(stats.getItemsCount())
                    .updatedAt(Instant.now())
                    .build();
            statisticsRepository.save(doc);
            log.debug("Statistics updated: Floor={} TON", stats.getFloorPrice());
        } catch (Exception e) {
            log.error("Failed to update statistics: {}", e.getMessage());
        }
    }

    private BigDecimal parseBigDecimal(String val) {
        try { return val != null ? new BigDecimal(val) : null; } catch (Exception e) { return null; }
    }
}