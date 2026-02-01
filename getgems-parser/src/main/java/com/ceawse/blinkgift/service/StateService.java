package com.ceawse.blinkgift.service;


import com.ceawse.blinkgift.model.IngestionState;

public interface StateService {
    IngestionState getState(String processId);
    void updateState(String processId, Long timestamp, String cursor);
    void markFinished(String processId);
}
