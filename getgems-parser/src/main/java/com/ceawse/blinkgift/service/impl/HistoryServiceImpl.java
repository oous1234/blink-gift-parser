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
import org.springframework.stereotype.Service;

import java.util.List;

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
        try {
            long lastTime = stateService.getState(PROCESS_ID).getLastProcessedTimestamp();
            if (lastTime == 0) lastTime = System.currentTimeMillis() - 60_000;

            GetGemsHistoryDto response = apiClient.getHistory(lastTime, null, 50, null, TARGET_TYPES, false);
            if (response == null || !response.isSuccess() || response.getResponse().getItems().isEmpty()) return;

            var items = response.getResponse().getItems();

            for (var item : items) {
                if (!repository.existsByHash(item.getHash())) {
                    repository.save(mapper.toHistoryEntity(item));
                }

                if (item.getTypeData() != null && "putUpForSale".equals(item.getTypeData().getType())) {
                    processNewListing(item);
                }
            }

            long maxTimestamp = items.stream().mapToLong(i -> i.getTimestamp() != null ? i.getTimestamp() : 0L).max().orElse(lastTime);
            stateService.updateState(PROCESS_ID, maxTimestamp, null);

        } catch (Exception e) {
            log.error("Realtime parser error: {}", e.getMessage());
        }
    }

    private void processNewListing(GetGemsItemDto historyItem) {
        try {
            var detailResponse = apiClient.getNftDetails(historyItem.getAddress());
            if (detailResponse == null || !detailResponse.isSuccess() || detailResponse.getResponse() == null) return;

            var details = detailResponse.getResponse();
            var numbers = mapper.parseNumbers(details.getName());

            String model = null, backdrop = null, symbol = null;
            if (details.getAttributes() != null) {
                for (var attr : details.getAttributes()) {
                    if ("Model".equalsIgnoreCase(attr.getTraitType())) model = attr.getValue();
                    if ("Backdrop".equalsIgnoreCase(attr.getTraitType())) backdrop = attr.getValue();
                    if ("Symbol".equalsIgnoreCase(attr.getTraitType())) symbol = attr.getValue();
                }
            }

            discoveryClient.enrich(DiscoveryInternalClient.EnrichmentRequest.builder()
                    .id(details.getAddress())
                    .giftName(details.getName())
                    .collectionAddress(details.getCollectionAddress())
                    .serialNumber(numbers.serialNumber())
                    .totalLimit(numbers.totalLimit())
                    .model(model)
                    .backdrop(backdrop)
                    .symbol(symbol)
                    .timestamp(historyItem.getTimestamp())
                    .build());

        } catch (Exception e) {
            log.warn("Real-time enrichment failed for {}: {}", historyItem.getAddress(), e.getMessage());
        }
    }
}