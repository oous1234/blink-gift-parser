package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.repository.UniqueGiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnrichmentServiceImpl {

    private final UniqueGiftRepository uniqueGiftRepository;
    private final PythonGatewayClient pythonClient;

    @Override
    public void processInventoryBatch(String userId, List<PythonInventoryResponse.InventoryItem> items) {
        for (var item : items) {
            // 1. Проверяем, знаем ли мы этот ТИП подарка (slug) в глобальном каталоге
            if (!uniqueGiftRepository.existsBySlug(item.getSlug())) {
                // 2. Если не знаем — запрашиваем "тяжелые" метаданные у Python MTProto
                try {
                    PythonMetadataResponse heavyMeta = pythonClient.getMetadataFast(item.getSlug());

                    // 3. Сохраняем в глобальный каталог unique_gifts
                    saveToGlobalCatalog(heavyMeta);
                } catch (Exception e) {
                    log.error("Failed to enrich slug: {}", item.getSlug());
                }
            }

            // 4. Обновляем запись в инвентаре пользователя (привязываем к метаданным)
            // Здесь можно реализовать денормализацию (копирование модели/редкости в user_inventory)
        }
    }

    private void saveToGlobalCatalog(PythonMetadataResponse meta) {
        UniqueGiftDocument globalGift = UniqueGiftDocument.builder()
                .slug(meta.getSlug())
                .name(meta.getTitle())
                .model(findAttribute(meta, "model"))
                .backdrop(findAttribute(meta, "backdrop"))
                .symbol(findAttribute(meta, "symbol"))
                .giftTotal(meta.getTotal_issued())
                .isResalable(meta.is_resalable())
                .build();
        uniqueGiftRepository.save(globalGift);
    }

    private String findAttribute(PythonMetadataResponse meta, String type) {
        return meta.getAttributes().stream()
                .filter(a -> a.getType().equals(type))
                .map(a -> a.getName())
                .findFirst()
                .orElse(null);
    }
}