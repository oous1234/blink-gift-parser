package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.client.GiftAttributesFeignClient;
import com.ceawse.giftdiscovery.client.TelegramNftFeignClient;
import com.ceawse.giftdiscovery.dto.GiftAttributesDto;
import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.service.AttributeEnrichmentService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AttributeEnrichmentServiceImpl implements AttributeEnrichmentService {
    private final MongoTemplate mongoTemplate;
    private final GiftAttributesFeignClient fragmentApiClient;
    private final TelegramNftFeignClient telegramApiClient;
    private final ExecutorService executor;

    private static final int BATCH_SIZE = 500;
    private static final Pattern OG_DESC_PATTERN = Pattern.compile("<meta property=\"og:description\" content=\"([\\s\\S]*?)\">");
    private static final Pattern ATTR_MODEL_PATTERN = Pattern.compile("Model:\\s*(.*?)(?:\\n|$)");
    private static final Pattern ATTR_BACKDROP_PATTERN = Pattern.compile("Backdrop:\\s*(.*?)(?:\\n|$)");
    private static final Pattern ATTR_SYMBOL_PATTERN = Pattern.compile("Symbol:\\s*(.*?)(?:\\n|$)");

    public AttributeEnrichmentServiceImpl(
            MongoTemplate mongoTemplate,
            GiftAttributesFeignClient fragmentApiClient,
            TelegramNftFeignClient telegramApiClient,
            @Qualifier("virtualThreadExecutor") ExecutorService executor) {
        this.mongoTemplate = mongoTemplate;
        this.fragmentApiClient = fragmentApiClient;
        this.telegramApiClient = telegramApiClient;
        this.executor = executor;
    }

    @Override
    public void enrichMissingAttributes() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", "ATTR-" + traceId);
        MDC.put("context", "BATCH-JOB");

        try {
            Query query = new Query(Criteria.where("attributes").exists(false)).limit(BATCH_SIZE);
            List<UniqueGiftDocument> gifts = mongoTemplate.find(query, UniqueGiftDocument.class);

            if (gifts.isEmpty()) {
                log.trace("No gifts without attributes found.");
                return;
            }

            log.info("Starting batch attribute enrichment for {} gifts", gifts.size());

            ConcurrentLinkedQueue<UniqueGiftDocument> processedGifts = new ConcurrentLinkedQueue<>();
            var futures = gifts.stream()
                    .map(gift -> CompletableFuture.runAsync(() -> {
                        // Внутри Virtual Thread нужно снова ставить MDC
                        MDC.put("traceId", "ATTR-" + traceId);
                        MDC.put("context", gift.getId());
                        try {
                            processSingleGift(gift, processedGifts);
                        } finally {
                            MDC.clear();
                        }
                    }, executor))
                    .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();

            log.info("Finished gathering attributes. Saving {} updates...", processedGifts.size());
            saveUpdates(processedGifts);

        } catch (Exception e) {
            log.error("Batch enrichment failure: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    private void processSingleGift(UniqueGiftDocument gift, ConcurrentLinkedQueue<UniqueGiftDocument> resultQueue) {
        try {
            String slug = formatSlug(gift.getName());
            log.debug("Fetching metadata for: {} (Slug: {})", gift.getName(), slug);

            UniqueGiftDocument.GiftAttributes attributes;
            if (gift.isOffchainSafe()) {
                String html = telegramApiClient.getNftPage(slug);
                attributes = parseAttributesFromHtml(html);
            } else {
                GiftAttributesDto dto = fragmentApiClient.getAttributes(slug);
                attributes = mapFragmentDtoToModel(dto);
            }

            if (attributes != null) {
                gift.setAttributes(attributes);
                resultQueue.add(gift);
                log.debug("Successfully parsed attributes for {}", gift.getName());
            } else {
                log.warn("Attributes not found for gift: {} (Slug: {})", gift.getName(), slug);
            }
        } catch (Exception e) {
            log.error("Error processing gift {}: {}", gift.getName(), e.getMessage());
        }
    }

    private void saveUpdates(ConcurrentLinkedQueue<UniqueGiftDocument> gifts) {
        if (gifts.isEmpty()) return;
        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UniqueGiftDocument.class);
            for (UniqueGiftDocument gift : gifts) {
                bulkOps.updateOne(
                        new Query(Criteria.where("_id").is(gift.getId())),
                        new Update().set("attributes", gift.getAttributes())
                );
            }
            bulkOps.execute();
            log.info("Bulk update completed for {} gifts", gifts.size());
        } catch (Exception e) {
            log.error("Bulk save failed: {}", e.getMessage(), e);
        }
    }

    private String formatSlug(String name) {
        if (name == null) return "";
        int hashIndex = name.lastIndexOf('#');
        if (hashIndex != -1) {
            String base = name.substring(0, hashIndex).replace(" ", "").replace("'", "").replace("’", "").replace("-", "").trim();
            String num = name.substring(hashIndex + 1).trim();
            return base + "-" + num;
        }
        return name.replaceAll("[\\s'’-]", "");
    }

    private UniqueGiftDocument.GiftAttributes mapFragmentDtoToModel(GiftAttributesDto dto) {
        if (dto == null || dto.getAttributes() == null || dto.getAttributes().isEmpty()) return null;
        var attrs = dto.getAttributes();
        return UniqueGiftDocument.GiftAttributes.builder()
                .model(getAttributeValue(attrs, 0))
                .backdrop(getAttributeValue(attrs, 1))
                .symbol(getAttributeValue(attrs, 2))
                .updatedAt(Instant.now())
                .build();
    }

    private String getAttributeValue(List<GiftAttributesDto.AttributeDto> list, int index) {
        return (list.size() > index) ? list.get(index).getValue() : null;
    }

    private UniqueGiftDocument.GiftAttributes parseAttributesFromHtml(String html) {
        Matcher descMatcher = OG_DESC_PATTERN.matcher(html);
        if (descMatcher.find()) {
            String content = descMatcher.group(1);
            String model = extractRegex(content, ATTR_MODEL_PATTERN);
            String backdrop = extractRegex(content, ATTR_BACKDROP_PATTERN);
            String symbol = extractRegex(content, ATTR_SYMBOL_PATTERN);
            if (model != null || backdrop != null || symbol != null) {
                return UniqueGiftDocument.GiftAttributes.builder()
                        .model(model).backdrop(backdrop).symbol(symbol)
                        .updatedAt(Instant.now()).build();
            }
        }
        return null;
    }

    private String extractRegex(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}