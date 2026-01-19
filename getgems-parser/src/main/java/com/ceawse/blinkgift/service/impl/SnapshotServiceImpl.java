package com.ceawse.blinkgift.service.impl;

import com.ceawse.blinkgift.client.DiscoveryInternalClient;
import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.client.IndexerApiClient;
import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import com.ceawse.blinkgift.dto.GetGemsSaleItemDto;
import com.ceawse.blinkgift.mapper.EventMapper;
import com.ceawse.blinkgift.repository.GiftHistoryRepository;
import com.ceawse.blinkgift.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotServiceImpl implements SnapshotService {

    private final IndexerApiClient indexerClient;
    private final GetGemsApiClient getGemsClient;
    private final GiftHistoryRepository historyRepository;
    private final EventMapper eventMapper;
    private final DiscoveryInternalClient discoveryClient;

    @Async
    @Override
    public void runSnapshot(String marketplace) {
        String snapshotId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        log.info(">>> [SNAPSHOT START] ID: {}, Marketplace: {}", snapshotId, marketplace);

        try {
            List<IndexerApiClient.CollectionDto> collections = indexerClient.getCollections();
            if (collections == null || collections.isEmpty()) {
                log.warn(">>> [SNAPSHOT ABORTED] No collections found from Indexer client.");
                return;
            }

            int processedCount = 0;
            for (var col : collections) {
                processedCount++;
                log.info(">>> [COLLECTION {}/{}] Processing: {}", processedCount, collections.size(), col.address);
                processCollection(col.address, snapshotId);
            }

            finishSnapshot(snapshotId, startTime, marketplace);
        } catch (Exception e) {
            log.error(">>> [SNAPSHOT CRITICAL ERROR]", e);
        }
    }

    private void processCollection(String collectionAddress, String snapshotId) {
        String cursor = null;
        boolean hasMore = true;
        int batchSize = 100;

        while (hasMore) {
            try {
                var response = getGemsClient.getOnSale(collectionAddress, batchSize, cursor);
                if (response == null || !response.isSuccess() || response.getResponse() == null) break;

                List<GetGemsSaleItemDto> items = response.getResponse().getItems();
                if (items == null || items.isEmpty()) {
                    hasMore = false;
                    continue;
                }

                List<GiftHistoryDocument> historyDocs = items.stream()
                        .filter(i -> i.getSale() != null)
                        .map(i -> eventMapper.toSnapshotEntity(i, snapshotId))
                        .toList();

                if (!historyDocs.isEmpty()) {
                    historyRepository.saveAll(historyDocs);
                }

                for (var item : items) {
                    sendToDiscovery(item);
                }

                cursor = response.getResponse().getCursor();
                if (cursor == null) hasMore = false;

                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Error in processCollection for {}: {}", collectionAddress, e.getMessage());
                hasMore = false;
            }
        }
    }

    private void sendToDiscovery(GetGemsSaleItemDto item) {
        try {
            var numbers = eventMapper.parseNumbers(item.getName());

            String model = null, backdrop = null, symbol = null;
            if (item.getAttributes() != null) {
                for (var attr : item.getAttributes()) {
                    if ("Model".equalsIgnoreCase(attr.getTraitType())) model = attr.getValue();
                    if ("Backdrop".equalsIgnoreCase(attr.getTraitType())) backdrop = attr.getValue();
                    if ("Symbol".equalsIgnoreCase(attr.getTraitType())) symbol = attr.getValue();
                }
            }

            discoveryClient.enrich(DiscoveryInternalClient.EnrichmentRequest.builder()
                    .id(item.getAddress())
                    .giftName(item.getName())
                    .collectionAddress(item.getCollectionAddress())
                    .serialNumber(numbers.serialNumber())
                    .totalLimit(numbers.totalLimit())
                    .model(model)
                    .backdrop(backdrop)
                    .symbol(symbol)
                    .timestamp(System.currentTimeMillis())
                    .build());

        } catch (Exception e) {
            log.warn("Discovery enrichment FAILED for {}: {}", item.getName(), e.getMessage());
        }
    }

    private void finishSnapshot(String snapshotId, long startTime, String marketplace) {
        GiftHistoryDocument doc = new GiftHistoryDocument();
        doc.setMarketplace(marketplace);
        doc.setEventType("SNAPSHOT_FINISH");
        doc.setSnapshotId(snapshotId);
        doc.setTimestamp(System.currentTimeMillis());
        doc.setEventPayload(String.valueOf(startTime));
        doc.setHash("FINISH_" + snapshotId);
        doc.setAddress("SYSTEM");
        doc.setCollectionAddress("SYSTEM");

        historyRepository.save(doc);
        long duration = (System.currentTimeMillis() - startTime) / 1000;
        log.info(">>> [SNAPSHOT FINISHED] ID: {}. Duration: {} sec.", snapshotId, duration);
    }
}