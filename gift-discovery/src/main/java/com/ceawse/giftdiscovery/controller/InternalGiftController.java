package com.ceawse.giftdiscovery.controller;

import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/gifts")
@RequiredArgsConstructor
public class InternalGiftController {

    private final DiscoveryService discoveryService;

    @GetMapping("/metadata/{slug}")
    public ResponseEntity<UniqueGiftDocument> getMetadata(@PathVariable String slug) {
        UniqueGiftDocument gift = discoveryService.getOrPopulateMetadata(slug);
        if (gift == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(gift);
    }
}