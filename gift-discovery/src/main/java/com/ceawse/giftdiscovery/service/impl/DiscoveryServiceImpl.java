package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.client.GiftMetadataFeignClient;
import com.ceawse.giftdiscovery.dto.MetadataResponseDto;
import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.repository.UniqueGiftRepository;
import com.ceawse.giftdiscovery.service.DiscoveryService;
import com.ceawse.giftdiscovery.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryServiceImpl implements DiscoveryService {

    private final UniqueGiftRepository uniqueGiftRepository;
    private final GiftMetadataFeignClient metadataClient;
    private final MarketDataService marketDataService;
    private final MongoTemplate mongoTemplate;

    @Override
    public UniqueGiftDocument getOrPopulateMetadata(String slug) {
        Optional<UniqueGiftDocument> existing = uniqueGiftRepository.findById(slug);
        if (existing.isPresent()) {
            return existing.get();
        }

        log.info("Gift {} not in registry. Fetching fresh metadata...", slug);
        try {
            int lastDash = slug.lastIndexOf("-");
            if (lastDash == -1) throw new IllegalArgumentException("Invalid slug format");

            String baseSlug = slug.substring(0, lastDash);
            Integer giftId = Integer.parseInt(slug.substring(lastDash + 1));

            MetadataResponseDto raw = metadataClient.getMetadata(baseSlug, giftId);

            UniqueGiftDocument newGift = UniqueGiftDocument.builder()
                    .id(slug)
                    .name(raw.giftName())
                    .giftNum(raw.giftNum())
                    .giftMinted(raw.giftMinted())
                    .giftTotal(raw.giftTotal())
                    .model(raw.model())
                    .modelRare(raw.modelRare())
                    .backdrop(raw.backdrop())
                    .backdropRare(raw.backdropRare())
                    .symbol(raw.pattern())
                    .symbolRare(raw.patternRare())
                    .backdropCenterColor(raw.backdropCenterColor())
                    .backdropEdgeColor(raw.backdropEdgeColor())
                    .backdropPatternColor(raw.backdropPatternColor())
                    .modelUrl(raw.modelUrl())
                    .patternUrl(raw.patternUrl())
                    .pageUrl(raw.pageUrl())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            enrichWithMarketData(newGift);

            return uniqueGiftRepository.save(newGift);

        } catch (Exception e) {
            log.error("Error populating gift {}: {}", slug, e.getMessage());
            return null;
        }
    }

    private void enrichWithMarketData(UniqueGiftDocument gift) {
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
    }

    @Override
    public void processRegistryStream() {
        // Логика фоновой синхронизации реестра (если нужна)
    }

    @Override
    public void processHistoryStream() {
        // Логика фоновой обработки истории (если нужна)
    }
}