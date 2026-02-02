package com.ceawse.portalsparser.service.impl;

import com.ceawse.portalsparser.client.DiscoveryInternalClient;
import com.ceawse.portalsparser.client.PortalsApiClient;
import com.ceawse.portalsparser.domain.PortalsGiftHistoryDocument;
import com.ceawse.portalsparser.domain.PortalsIngestionState;
import com.ceawse.portalsparser.dto.PortalsActionsResponseDto;
import com.ceawse.portalsparser.dto.PortalsNftDto;
import com.ceawse.portalsparser.dto.PortalsSearchResponseDto;
import com.ceawse.portalsparser.mapper.PortalsMapper;
import com.ceawse.portalsparser.repository.PortalsGiftHistoryRepository;
import com.ceawse.portalsparser.repository.PortalsIngestionStateRepository;
import com.ceawse.portalsparser.service.PortalsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalsServiceImpl implements PortalsService {
    private final PortalsApiClient apiClient;
    private final PortalsGiftHistoryRepository historyRepository;
    private final PortalsIngestionStateRepository stateRepository;
    private final PortalsMapper mapper;
    private final DiscoveryInternalClient discoveryClient;

    private static final String REALTIME_PROCESS_ID = "PORTALS_LIVE";

    @Override
    @Async("taskExecutor")
    public void runSnapshot() {
        String snapshotId = UUID.randomUUID().toString().substring(0, 8);
        log.info(">>> STARTING PORTALS SNAPSHOT ID={}", snapshotId);
        long startTime = System.currentTimeMillis();
        int offset = 0;
        int limit = 50;

        try {
            while (true) {
                PortalsSearchResponseDto response = apiClient.searchNfts(
                        offset, limit, "price asc", "listed", true
                );

                if (response == null || response.getResults() == null || response.getResults().isEmpty()) break;

                for (PortalsNftDto nft : response.getResults()) {
                    PortalsGiftHistoryDocument doc = mapper.mapSnapshotToHistory(nft, snapshotId);

                    // Обогащение
                    enrichWithDiscovery(doc, nft);

                    historyRepository.save(doc);
                }

                log.info("Portals Snapshot: processed offset {}", offset);
                offset += limit;
                Thread.sleep(200);
            }

            historyRepository.save(mapper.createSnapshotFinishEvent(snapshotId, startTime));
            log.info(">>> PORTALS SNAPSHOT {} FINISHED.", snapshotId);
        } catch (Exception e) {
            log.error("Portals Snapshot CRITICAL failed", e);
        }
    }

    @Override
    public void processRealtimeEvents() {
        try {
            PortalsIngestionState state = stateRepository.findById(REALTIME_PROCESS_ID)
                    .orElseGet(() -> new PortalsIngestionState(REALTIME_PROCESS_ID, System.currentTimeMillis() - 60000));

            PortalsActionsResponseDto response = apiClient.getMarketActivity(0, 50, "listed_at desc", "buy,listing,price_update");
            if (response == null || response.getActions() == null) return;

            long lastProcessedTime = state.getLastProcessedTimestamp();
            long newMaxTime = lastProcessedTime;

            for (PortalsActionsResponseDto.ActionDto action : response.getActions()) {
                long actionTime = parseTime(action.getCreatedAt());
                if (actionTime <= lastProcessedTime) continue;

                PortalsGiftHistoryDocument doc = mapper.mapActionToHistory(action, actionTime);

                if (!historyRepository.existsByHash(doc.getHash())) {
                    // Обогащение для реалтайм событий
                    if (action.getNft() != null) {
                        enrichWithDiscovery(doc, action.getNft());
                    }
                    historyRepository.save(doc);
                }

                if (actionTime > newMaxTime) newMaxTime = actionTime;
            }

            state.setLastProcessedTimestamp(newMaxTime);
            stateRepository.save(state);

        } catch (Exception e) {
            log.error("Portals Realtime Error: {}", e.getMessage());
        }
    }

    private void enrichWithDiscovery(PortalsGiftHistoryDocument doc, PortalsNftDto nft) {
        try {
            String slug = mapper.createSlug(nft);
            if (slug != null) {
                var meta = discoveryClient.getMetadata(slug);
                mapper.enrichHistory(doc, meta);
            }
        } catch (Exception e) {
            log.trace("Discovery skip for {}: {}", nft.getName(), e.getMessage());
        }
    }

    private long parseTime(String isoTime) {
        try { return Instant.parse(isoTime).toEpochMilli(); } catch (Exception e) { return System.currentTimeMillis(); }
    }
}