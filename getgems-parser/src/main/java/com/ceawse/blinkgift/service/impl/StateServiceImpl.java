package com.ceawse.blinkgift.service.impl;

import com.ceawse.blinkgift.model.IngestionState;
import com.ceawse.blinkgift.repository.IngestionStateRepository;
import com.ceawse.blinkgift.service.StateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StateServiceImpl implements StateService {

    private final IngestionStateRepository repository;

    @Override
    public IngestionState getState(String processId) {
        return repository.findById(processId)
                .orElse(new IngestionState(processId, 0L, null, "NEW"));
    }

    @Override
    public void updateState(String processId, Long timestamp, String cursor) {
        IngestionState state = getState(processId);
        state.setLastProcessedTimestamp(timestamp);
        state.setLastCursor(cursor);
        state.setStatus("RUNNING");
        repository.save(state);
    }

    @Override
    public void markFinished(String processId) {
        IngestionState state = getState(processId);
        state.setStatus("FINISHED");
        repository.save(state);
    }
}