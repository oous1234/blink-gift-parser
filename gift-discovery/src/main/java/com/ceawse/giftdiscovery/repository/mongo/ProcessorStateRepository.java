package com.ceawse.giftdiscovery.repository.mongo;

import com.ceawse.giftdiscovery.model.ProcessorState;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessorStateRepository extends MongoRepository<ProcessorState, String> {
}