package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.client.PythonGatewayClient;
import com.ceawse.giftdiscovery.dto.external.PythonInventoryResponse;
import com.ceawse.giftdiscovery.dto.external.PythonMetadataResponse;
import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.repository.UniqueGiftRepository;
import com.ceawse.giftdiscovery.service.EnrichmentService;
import com.ceawse.giftdiscovery.service.PriceEstimationService;
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
    private final PriceEstimationService priceEstimationService;

    @Override
    public void processInventoryBatch(String userId, List<PythonInventoryResponse.InventoryItem> items) {
        for (var item : items) {
            String slug = item.getSlug();

            // Проверяем, есть ли этот ТИП подарка в нашем глобальном справочнике
            // Используем existsById, так как slug у нас является первичным ключом
            if (!uniqueGiftRepository.existsById(slug)) {
                log.info("New gift type detected: {}. Fetching heavy metadata...", slug);
                enrichNewGiftType(slug);
            }

            // Здесь в будущем можно добавить логику связывания конкретного
            // экземпляра юзера с этими метаданными (денормализация в user_inventory)
        }
    }

    private void enrichNewGiftType(String slug) {
        try {
            // Запрос к Python-шлюзу (MTProto)
            PythonMetadataResponse meta = pythonClient.getMetadataFast(slug);

            UniqueGiftDocument doc = UniqueGiftDocument.builder()
                    .id(meta.getSlug())
                    .name(meta.getTitle())
                    .giftNum(meta.getSerial_number())
                    .giftTotal(meta.getTotal_issued())
                    .model(extractAttribute(meta, "model"))
                    .backdrop(extractAttribute(meta, "backdrop"))
                    .symbol(extractAttribute(meta, "symbol"))
                    .isResalable(meta.is_resalable())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            uniqueGiftRepository.save(doc);
            log.debug("Successfully enriched and saved global gift: {}", slug);


        } catch (Exception e) {
            log.error("Failed to fetch heavy metadata for slug {}: {}", slug, e.getMessage());
        }
    }

    private String extractAttribute(PythonMetadataResponse meta, String type) {
        if (meta.getAttributes() == null) return null;
        return meta.getAttributes().stream()
                .filter(a -> type.equalsIgnoreCase(a.getType()))
                .map(PythonMetadataResponse.Attribute::getName)
                .findFirst()
                .orElse("Original");
    }
}