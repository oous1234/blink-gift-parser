package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.service.DiscoveryTaskConsumer;
import com.ceawse.giftdiscovery.worker.InventoryFullScanWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryTaskConsumerImpl implements DiscoveryTaskConsumer {

    private final InventoryFullScanWorker inventoryFullScanWorker;

    // Пул потоков для параллельной обработки задач сканирования
    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(4);

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            log.info("New discovery task received. Stream ID: {}", message.getId());

            // 1. Извлекаем данные из сообщения Redis
            String rawUserId = message.getValue().get("userId");
            String rawTotalCount = message.getValue().get("totalCount");

            // 2. Очищаем данные от возможных JSON-кавычек (защита от Jackson)
            final String userId = cleanJsonQuotes(rawUserId);
            String cleanedTotalCount = cleanJsonQuotes(rawTotalCount);

            if (!StringUtils.hasText(userId)) {
                log.warn("Received discovery task with empty userId. Skipping.");
                return;
            }

            // 3. Преобразуем строку в число (int)
            int parsedTotalCount = 0;
            if (StringUtils.hasText(cleanedTotalCount)) {
                try {
                    parsedTotalCount = Integer.parseInt(cleanedTotalCount.trim());
                } catch (NumberFormatException e) {
                    log.error("Invalid totalCount format: '{}'. Cannot start scan.", cleanedTotalCount);
                    return;
                }
            }

            // Создаем финальную переменную для использования в лямбде
            final int totalCount = parsedTotalCount;

            log.info("Starting background scan for user: {} (Expected: {})", userId, totalCount);

            // 4. Запускаем выполнение в отдельном потоке
            taskExecutor.submit(() -> {
                try {
                    // Теперь типы данных (String, int) полностью совпадают с методом воркера
                    inventoryFullScanWorker.performFullScan(userId, totalCount);
                } catch (Exception e) {
                    log.error("Background scan failed for user {}: {}", userId, e.getMessage(), e);
                }
            });

        } catch (Exception e) {
            log.error("Error parsing message from discovery stream: {}", e.getMessage());
        }
    }

    /**
     * Удаляет лишние кавычки, если строка пришла в формате JSON-строки (например, ""123"").
     */
    private String cleanJsonQuotes(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}