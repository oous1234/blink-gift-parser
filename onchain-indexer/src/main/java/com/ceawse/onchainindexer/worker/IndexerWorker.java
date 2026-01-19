package com.ceawse.onchainindexer.worker;

import com.ceawse.onchainindexer.repository.CollectionRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndexerWorker {

    private final CollectionRegistryRepository colRepo;

    public void runFullIndexing() {
        log.info("Full indexing is disabled. Only collection discovery is active.");
    }
    public void indexNextBatch() {
    }
}