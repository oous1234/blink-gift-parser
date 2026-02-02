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
        long startTime = System.currentTimeMillis();

        try {
            List<IndexerApiClient.CollectionDto> collections = indexerClient.getCollections();
            if (collections == null) return;

            for (var col : collections) {
                MDC.put("context", col.address);
                processCollection(col.address, snapshotId);
            }

            finishSnapshot(snapshotId, startTime, marketplace);
        } catch (Exception e) {
            log.error("Snapshot CRITICAL error: {}", e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    private void processCollection(String collectionAddress, String snapshotId) {
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            try {
                var response = getGemsClient.getOnSale(collectionAddress, 100, cursor);
                if (response == null || response.getResponse() == null) break;

                List<GetGemsSaleItemDto> items = response.getResponse().getItems();
                if (items == null || items.isEmpty()) break;

                for (var item : items) {
                    if (item.getSale() == null) continue;

                    GiftHistoryDocument doc = eventMapper.toSnapshotEntity(item, snapshotId);

                    // Обогащаем каждый лот снапшота
                    try {
                        String slug = eventMapper.createSlug(item.getName());
                        if (slug != null) {
                            var meta = discoveryClient.getMetadata(slug);
                            eventMapper.enrichHistory(doc, meta);
                        }
                    } catch (Exception e) {
                        // Если Discovery не отвечает, сохраняем как есть (с пустыми атрибутами)
                    }

                    historyRepository.save(doc);
                }

                cursor = response.getResponse().getCursor();
                if (cursor == null) hasMore = false;
                else Thread.sleep(300);

            } catch (Exception e) {
                log.error("Collection process error: {}", e.getMessage());
                hasMore = false;
            }
        }
    }

    private void finishSnapshot(String snapshotId, long startTime, String marketplace) {
        GiftHistoryDocument doc = eventMapper.createSnapshotFinishEvent(snapshotId, startTime, marketplace);
        historyRepository.save(doc);
        log.info("Snapshot {} finished for {}", snapshotId, marketplace);
    }
}