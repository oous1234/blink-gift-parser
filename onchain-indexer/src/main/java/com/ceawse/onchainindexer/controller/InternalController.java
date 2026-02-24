package com.ceawse.onchainindexer.controller;

import com.ceawse.onchainindexer.model.CollectionRegistryDocument;
import com.ceawse.onchainindexer.repository.CollectionRegistryRepository;
import com.ceawse.onchainindexer.worker.IndexerWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalController {

    private final CollectionRegistryRepository collectionRepository;

    @GetMapping("/collections")
    public List<CollectionRegistryDocument> getAllCollections() {
        return collectionRepository.findAll();
    }
}