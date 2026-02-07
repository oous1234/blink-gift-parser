package com.ceawse.giftdiscovery.service;

import com.ceawse.giftdiscovery.dto.external.PythonInventoryResponse;

import java.util.List;

public interface EnrichmentService {

    void processInventoryBatch(String userId, List<PythonInventoryResponse.InventoryItem> items);
}
