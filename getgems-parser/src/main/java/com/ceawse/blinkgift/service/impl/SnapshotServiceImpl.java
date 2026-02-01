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
import org.slf4j.MDC;
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
        String snapshotId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", "SNAP-" + snapshotId);
        MDC.put("context", "STARTUP");

        long startTime = System.currentTimeMillis();
        log.info(">>> STARTING SNAPSHOT PROCESS. Marketplace: {}", marketplace);

        try {
            List<IndexerApiClient.CollectionDto> collections = indexerClient.getCollections();
            if (collections == null || collections.isEmpty()) {
                log.error("Snapshot aborted: No collections received from indexer");
                return;
            }

            log.info("Found {} collections to process", collections.size());

            for (int i = 0; i < collections.size(); i++) {
                var col = collections.get(i);
                MDC.put("context", col.address);
                log.info("[{}/{}] Processing collection: {}", (i + 1), collections.size(), col.address);

                try {
                    processCollection(col.address, snapshotId);
                } catch (Exception e) {
                    log.error("Error processing collection {}: {}", col.address, e.getMessage(), e);
                }
            }

            finishSnapshot(snapshotId, startTime, marketplace);
        } catch (Exception e) {
            log.error("CRITICAL ERROR during snapshot execution: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    private void processCollection(String collectionAddress, String snapshotId) {
        String cursor = null;
        boolean hasMore = true;
        int totalProcessed = 0;

        while (hasMore) {
            try {
                var response = getGemsClient.getOnSale(collectionAddress, 100, cursor);
                if (response == null || !response.isSuccess() || response.getResponse() == null) {
                    log.warn("Unsuccessful response for collection {} at cursor {}", collectionAddress, cursor);
                    break;
                }

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

                items.forEach(this::sendToDiscovery);

                totalProcessed += items.size();
                log.debug("Batch processed: {} items. Total collection items: {}", items.size(), totalProcessed);

                cursor = response.getResponse().getCursor();
                if (cursor == null) {
                    hasMore = false;
                } else {
                    // Маленькая пауза, чтобы не забанили прокси
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                log.error("Exception during paginated processing for {}: {}", collectionAddress, e.getMessage());
                hasMore = false;
            }
        }
        log.info("Collection {} finished. Total items found: {}", collectionAddress, totalProcessed);
    }

    private void sendToDiscovery(GetGemsSaleItemDto item) {
        try {
            var numbers = eventMapper.parseNumbers(item.getName());
            String model = null, backdrop = null;
            if (item.getAttributes() != null) {
                for (var attr : item.getAttributes()) {
                    if ("Model".equalsIgnoreCase(attr.getTraitType())) model = attr.getValue();
                    if ("Backdrop".equalsIgnoreCase(attr.getTraitType())) backdrop = attr.getValue();
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
                    .timestamp(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            log.trace("Failed to send item to discovery: {} (Normal if discovery is down)", item.getName());
        }
    }

    private void finishSnapshot(String snapshotId, long startTime, String marketplace) {
        MDC.put("context", "FINALIZING");
        long duration = (System.currentTimeMillis() - startTime) / 1000;

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
        log.info(">>> SNAPSHOT COMPLETED. ID: {}. Total duration: {}s", snapshotId, duration);
    }
}