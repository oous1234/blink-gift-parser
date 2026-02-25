package com.ceawse.giftdiscovery.repository.mongo;

import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UniqueGiftRepository extends MongoRepository<UniqueGiftDocument, String> {

    boolean existsById(String id);

    Optional<UniqueGiftDocument> findByName(String name);
}
