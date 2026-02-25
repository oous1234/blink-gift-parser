package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.client.PythonGatewayClient;
import com.ceawse.giftdiscovery.dto.external.PythonInventoryResponse;
import com.ceawse.giftdiscovery.dto.external.PythonMetadataResponse;
import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.repository.mongo.UniqueGiftRepository;
import com.ceawse.giftdiscovery.service.EnrichmentService;
import com.ceawse.giftdiscovery.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrichmentServiceImpl implements EnrichmentService {

    private final UniqueGiftRepository uniqueGiftRepository;
    private final PythonGatewayClient pythonClient;
    private final MarketDataService marketDataService;

    @Override
    public UniqueGiftDocument getOrCreateUniqueGift(String slug) {
        return uniqueGiftRepository.findById(slug)
                .orElseGet(() -> enrichNewGiftType(slug));
    }

    private UniqueGiftDocument enrichNewGiftType(String slug) {
        try {
            log.info("Enriching new gift type from Python Gateway: {}", slug);
            PythonMetadataResponse meta = pythonClient.getMetadataFast(slug);

            UniqueGiftDocument doc = UniqueGiftDocument.builder()
                    .id(meta.getSlug())
                    .name(meta.getTitle())
                    .slug(meta.getSlug())
                    .giftNum(meta.getSerial_number())
                    .giftTotal(meta.getTotal_issued())
                    .model(extractAttribute(meta, "model"))
                    .backdrop(extractAttribute(meta, "backdrop"))
                    .symbol(extractAttribute(meta, "symbol"))
                    .isResalable(meta.is_resalable())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            enrichWithMarketData(doc);

            log.debug("Saving enriched gift to unique_gifts: {}", slug);
            return uniqueGiftRepository.save(doc);
        } catch (Exception e) {
            log.error("Failed to fetch metadata for slug {}: {}", slug, e.getMessage());
            return createFallbackGift(slug);
        }
    }

    private void enrichWithMarketData(UniqueGiftDocument gift) {
        try {
            String colAddr = marketDataService.resolveCollectionAddress(gift.getName(), null);
            gift.setCollectionAddress(colAddr);

            var modelData = marketDataService.getAttributeData(colAddr, "Model", gift.getModel());
            var backdropData = marketDataService.getAttributeData(colAddr, "Backdrop", gift.getBackdrop());

            UniqueGiftDocument.MarketData marketData = UniqueGiftDocument.MarketData.builder()
                    .collectionFloorPrice(marketDataService.getCollectionFloor(colAddr))
                    .modelFloorPrice(modelData != null ? modelData.getPrice() : null)
                    .backdropFloorPrice(backdropData != null ? backdropData.getPrice() : null)
                    .priceUpdatedAt(Instant.now())
                    .build();

            marketData.setEstimatedPrice(marketData.getCollectionFloorPrice());
            gift.setMarketData(marketData);
        } catch (Exception e) {
            log.warn("Could not enrich market data for {}: {}", gift.getId(), e.getMessage());
        }
    }

    private String extractAttribute(PythonMetadataResponse meta, String type) {
        if (meta.getAttributes() == null) return "Original";
        return meta.getAttributes().stream()
                .filter(a -> type.equalsIgnoreCase(a.getType()))
                .map(PythonMetadataResponse.Attribute::getName)
                .findFirst()
                .orElse("Original");
    }

    private UniqueGiftDocument createFallbackGift(String slug) {
        return UniqueGiftDocument.builder()
                .id(slug)
                .name("Unknown Gift")
                .model("Unknown")
                .backdrop("Unknown")
                .symbol("Unknown")
                .build();
    }

    @Override
    public void processInventoryBatch(String userId, List<PythonInventoryResponse.InventoryItem> items) {
        for (var item : items) {
            getOrCreateUniqueGift(item.getSlug());
        }
    }
}