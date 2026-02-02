package com.ceawse.coreprocessor.worker;

import com.ceawse.coreprocessor.model.GiftHistoryDocument;
import com.ceawse.coreprocessor.model.ProcessorState;
import com.ceawse.coreprocessor.repository.ProcessorStateRepository;
import com.ceawse.coreprocessor.service.MarketProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessorWorker {
    private final ProcessorStateRepository stateRepository;
    private final MarketProcessor marketProcessor;
    private final MongoTemplate mongoTemplate;

    private static final String PROCESSOR_ID = "MAIN_PROCESSOR";
    private static final int BATCH_SIZE = 1000;

    @Scheduled(fixedDelayString = "${app.worker.delay:1000}")
    public void processBatch() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", "PROC-" + traceId);
        MDC.put("context", "BATCH");

        try {
            ProcessorState state = getOrInitState();
            List<GiftHistoryDocument> events = fetchNextBatch(state);

            if (events.isEmpty()) {
                log.trace("No new events to process.");
                return;
            }

            log.info("Starting batch processing of {} events.", events.size());

            long maxTime = state.getLastProcessedTimestamp();
            String maxId = state.getLastProcessedId();
            boolean stateUpdated = false;

            for (GiftHistoryDocument event : events) {
                // Устанавливаем адрес предмета в контекст для логов внутри процессора
                MDC.put("context", event.getAddress() != null ? event.getAddress() : "SYSTEM");
                try {
                    marketProcessor.processEvent(event);
                    maxTime = event.getTimestamp();
                    maxId = event.getId();
                    stateUpdated = true;
                } catch (Exception e) {
                    log.error("Failed to process event ID={}: {}", event.getId(), e.getMessage(), e);
                }
            }

            if (stateUpdated) {
                updateState(state, maxTime, maxId);
            }
        } finally {
            MDC.clear();
        }
    }

    private ProcessorState getOrInitState() {
        return stateRepository.findById(PROCESSOR_ID)
                .orElse(new ProcessorState(PROCESSOR_ID, 0L, null));
    }

    private void updateState(ProcessorState state, long timestamp, String id) {
        state.setLastProcessedTimestamp(timestamp);
        state.setLastProcessedId(id);
        stateRepository.save(state);
        MDC.put("context", "BATCH");
        log.info("Batch completed. State updated to timestamp={}", timestamp);
    }

    private List<GiftHistoryDocument> fetchNextBatch(ProcessorState state) {
        Query query = new Query();
        long lastTime = state.getLastProcessedTimestamp();
        String lastId = state.getLastProcessedId();

        if (lastId != null) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("timestamp").gt(lastTime),
                    Criteria.where("timestamp").is(lastTime).and("id").gt(lastId)
            ));
        } else {
            query.addCriteria(Criteria.where("timestamp").gte(lastTime));
        }

        query.with(Sort.by(Sort.Direction.ASC, "timestamp", "id"));
        query.limit(BATCH_SIZE);
        return mongoTemplate.find(query, GiftHistoryDocument.class);
    }
}