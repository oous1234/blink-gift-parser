package com.ceawse.giftmetadata.controller;

import com.ceawse.giftmetadata.dto.NftMetadataResponse;
import com.ceawse.giftmetadata.service.GiftMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/metadata")
@RequiredArgsConstructor
public class GiftMetadataController {

    private final GiftMetadataService giftMetadataService;

    @GetMapping("/{slug}/{id}")
    public ResponseEntity<NftMetadataResponse> getGiftMetadata(
            @PathVariable String slug,
            @PathVariable Integer id
    ) {
        return ResponseEntity.ok(giftMetadataService.getMetadata(slug, id));
    }
}