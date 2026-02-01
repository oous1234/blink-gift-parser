package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.model.GiftHistoryDocument;
import com.ceawse.giftdiscovery.model.ItemRegistryDocument;
import com.ceawse.giftdiscovery.model.ProcessorState;
import com.ceawse.giftdiscovery.repository.ProcessorStateRepository;
import com.ceawse.giftdiscovery.repository.UniqueGiftRepository;
import com.ceawse.giftdiscovery.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryServiceImpl implements DiscoveryService {
    private final UniqueGiftRepository uniqueGiftRepository;
    private final ProcessorStateRepository stateRepository;
    private final MongoTemplate mongoTemplate;

    private static final int BATCH_SIZE = 1000;
    private static final String ID_REGISTRY = "DISCOVERY_REGISTRY";
    private static final String ID_HISTORY = "DISCOVERY_HISTORY";

    @Override
    public void processRegistryStream() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", "DSC-" + traceId);
        MDC.put("context", "REGISTRY");

        try {
            ProcessorState state = getState(ID_REGISTRY);
            Instant lastTime = Instant.ofEpochMilli(state.getLastProcessedTimestamp());

            log.debug("Checking Registry stream since: {}", lastTime);

            Query query = new Query(Criteria.where("lastSeenAt").gt(lastTime))
                    .with(Sort.by(Sort.Direction.ASC, "lastSeenAt"))
                    .limit(BATCH_SIZE);

            List<ItemRegistryDocument> items = mongoTemplate.find(query, ItemRegistryDocument.class);
            if (items.isEmpty()) return;

            log.info("Discovered {} items from Registry. Performing bulk upsert...", items.size());
            uniqueGiftRepository.bulkUpsertFromRegistry(items);

            long maxTime = items.stream()
                    .mapToLong(i -> i.getLastSeenAt().toEpochMilli())
                    .max()
                    .orElse(state.getLastProcessedTimestamp());

            saveState(state, maxTime);
            log.info("Registry discovery batch finished. New timestamp: {}", maxTime);

        } catch (Exception e) {
            log.error("Registry discovery failed: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void processHistoryStream() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", "DSC-" + traceId);
        MDC.put("context", "HISTORY");

        try {
            ProcessorState state = getState(ID_HISTORY);
            log.debug("Checking History stream since timestamp: {}", state.getLastProcessedTimestamp());

            Query query = new Query(Criteria.where("timestamp").gt(state.getLastProcessedTimestamp()))
                    .with(Sort.by(Sort.Direction.ASC, "timestamp"))
                    .limit(BATCH_SIZE);

            List<GiftHistoryDocument> events = mongoTemplate.find(query, GiftHistoryDocument.class);
            if (events.isEmpty()) return;

            log.info("Discovered {} events from History. Deduplicating...", events.size());
            List<GiftHistoryDocument> uniqueEvents = deduplicateEvents(events);

            uniqueGiftRepository.bulkUpsertFromHistory(uniqueEvents);

            long maxTime = events.get(events.size() - 1).getTimestamp();
            saveState(state, maxTime);
            log.info("History discovery batch finished. Processed {} events. New timestamp: {}", events.size(), maxTime);

        } catch (Exception e) {
            log.error("History discovery failed: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    private List<GiftHistoryDocument> deduplicateEvents(List<GiftHistoryDocument> events) {
        Map<String, GiftHistoryDocument> map = new HashMap<>();
        for (GiftHistoryDocument ev : events) {
            if (ev.getAddress() != null) {
                map.merge(ev.getAddress(), ev,
                        (oldV, newV) -> newV.getTimestamp() > oldV.getTimestamp() ? newV : oldV);
            }
        }
        return List.copyOf(map.values());
    }

    private ProcessorState getState(String id) {
        return stateRepository.findById(id).orElse(new ProcessorState(id, 0L, null));
    }

    private void saveState(ProcessorState state, long timestamp) {
        state.setLastProcessedTimestamp(timestamp);
        stateRepository.save(state);
    }
}