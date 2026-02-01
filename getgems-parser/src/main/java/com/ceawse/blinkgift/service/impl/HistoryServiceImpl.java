package com.ceawse.blinkgift.service.impl;

import com.ceawse.blinkgift.client.DiscoveryInternalClient;
import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import com.ceawse.blinkgift.dto.GetGemsHistoryDto;
import com.ceawse.blinkgift.dto.GetGemsItemDto;
import com.ceawse.blinkgift.mapper.EventMapper;
import com.ceawse.blinkgift.repository.GiftHistoryRepository;
import com.ceawse.blinkgift.service.HistoryService;
import com.ceawse.blinkgift.service.StateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {
    private final GetGemsApiClient apiClient;
    private final GiftHistoryRepository repository;
    private final StateService stateService;
    private final EventMapper mapper;
    private final DiscoveryInternalClient discoveryClient;

    private static final String PROCESS_ID = "GETGEMS_LIVE";
    private static final List<String> TARGET_TYPES = List.of("sold", "cancelSale", "putUpForSale");

    @Override
    public void fetchRealtimeEvents() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);
        MDC.put("context", "REALTIME");

        try {
            long lastTime = stateService.getState(PROCESS_ID).getLastProcessedTimestamp();
            if (lastTime == 0) lastTime = System.currentTimeMillis() - 60_000;

            log.debug("Polling GetGems history. Since timestamp: {}", lastTime);

            GetGemsHistoryDto response = apiClient.getHistory(lastTime, null, 50, null, TARGET_TYPES, false);

            if (response == null || !response.isSuccess() || response.getResponse() == null) {
                log.warn("GetGems API returned unsuccessful response or null");
                return;
            }

            var items = response.getResponse().getItems();
            if (items == null || items.isEmpty()) {
                log.trace("No new events in GetGems history since {}", lastTime);
                return;
            }

            log.info("Processing {} new history events", items.size());

            int savedCount = 0;
            for (var item : items) {
                MDC.put("context", item.getAddress());
                try {
                    if (processSingleEvent(item)) {
                        savedCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to process event hash={}: {}", item.getHash(), e.getMessage(), e);
                } finally {
                    MDC.put("context", "REALTIME");
                }
            }

            long maxTimestamp = items.stream()
                    .mapToLong(i -> i.getTimestamp() != null ? i.getTimestamp() : 0L)
                    .max()
                    .orElse(lastTime);

            stateService.updateState(PROCESS_ID, maxTimestamp, null);
            log.debug("Poll cycle finished. Saved {}/{} items. New lastTimestamp: {}", savedCount, items.size(), maxTimestamp);

        } catch (Exception e) {
            log.error("Critical error in history service cycle: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    private boolean processSingleEvent(GetGemsItemDto item) {
        if (repository.existsByHash(item.getHash())) {
            log.trace("Event already exists in DB: {}", item.getHash());
            return false;
        }

        repository.save(mapper.toHistoryEntity(item));
        log.debug("Saved new event: [Type: {}] [Hash: {}]",
                (item.getTypeData() != null ? item.getTypeData().getType() : "UNKNOWN"),
                item.getHash());

        if (item.getTypeData() != null && "putUpForSale".equals(item.getTypeData().getType())) {
            log.info("New listing detected: {} ({} TON)", item.getName(), item.getTypeData().getPrice());
            processNewListing(item);
        }
        return true;
    }

    private void processNewListing(GetGemsItemDto historyItem) {
        try {
            var detailResponse = apiClient.getNftDetails(historyItem.getAddress());
            if (detailResponse == null || !detailResponse.isSuccess() || detailResponse.getResponse() == null) {
                log.warn("Could not fetch NFT details for enrichment: {}", historyItem.getAddress());
                return;
            }

            var details = detailResponse.getResponse();
            var numbers = mapper.parseNumbers(details.getName());

            // Собираем атрибуты для лога
            String model = null, backdrop = null;
            if (details.getAttributes() != null) {
                for (var attr : details.getAttributes()) {
                    if ("Model".equalsIgnoreCase(attr.getTraitType())) model = attr.getValue();
                    if ("Backdrop".equalsIgnoreCase(attr.getTraitType())) backdrop = attr.getValue();
                }
            }

            log.debug("Sending enrichment request. Model: {}, Backdrop: {}", model, backdrop);

            discoveryClient.enrich(DiscoveryInternalClient.EnrichmentRequest.builder()
                    .id(details.getAddress())
                    .giftName(details.getName())
                    .collectionAddress(details.getCollectionAddress())
                    .serialNumber(numbers.serialNumber())
                    .totalLimit(numbers.totalLimit())
                    .model(model)
                    .backdrop(backdrop)
                    .timestamp(historyItem.getTimestamp())
                    .build());

        } catch (Exception e) {
            log.error("Real-time enrichment failed for {}: {}", historyItem.getAddress(), e.getMessage());
        }
    }
}