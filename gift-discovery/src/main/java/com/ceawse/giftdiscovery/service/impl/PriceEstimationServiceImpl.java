package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.service.MarketDataService;
import com.ceawse.giftdiscovery.service.PriceEstimationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceEstimationServiceImpl implements PriceEstimationService {

    private final MongoTemplate mongoTemplate;
    private final MarketDataService marketDataService;

    private static final int BATCH_SIZE = 1000;
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    @Override
    public void estimateGiftPrices() {
        // Ищем подарки, которые либо не оценены, либо оценка устарела (например, старше 1 часа)
        Instant oneHourAgo = Instant.now().minusSeconds(3600);

        Query query = new Query(Criteria.where("marketData.priceUpdatedAt").lt(oneHourAgo))
                .limit(BATCH_SIZE);

        List<UniqueGiftDocument> gifts = mongoTemplate.find(query, UniqueGiftDocument.class);

        if (gifts.isEmpty()) return;

        log.info("Updating price estimations for {} gifts...", gifts.size());

        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UniqueGiftDocument.class);
        int updatesCount = 0;

        for (UniqueGiftDocument gift : gifts) {
            try {
                String colAddr = gift.getCollectionAddress();
                if (colAddr == null) {
                    colAddr = marketDataService.resolveCollectionAddress(gift.getName(), null);
                }

                if (colAddr == null) continue;

                BigDecimal collectionFloor = marketDataService.getCollectionFloor(colAddr);
                var modelData = marketDataService.getAttributeData(colAddr, "Model", gift.getModel());
                var backdropData = marketDataService.getAttributeData(colAddr, "Backdrop", gift.getBackdrop());

                BigDecimal modelFloor = (modelData != null && modelData.getPrice() != null) ? modelData.getPrice() : collectionFloor;
                BigDecimal backdropFloor = (backdropData != null && backdropData.getPrice() != null) ? backdropData.getPrice() : collectionFloor;

                // Расчет оценки
                BigDecimal estimatedPrice = collectionFloor.multiply(TWO)
                        .add(modelFloor)
                        .add(backdropFloor)
                        .divide(FOUR, 2, RoundingMode.HALF_UP);

                // Обновляем только блок marketData
                Update update = new Update()
                        .set("collectionAddress", colAddr)
                        .set("marketData.collectionFloorPrice", collectionFloor)
                        .set("marketData.modelFloorPrice", modelFloor)
                        .set("marketData.backdropFloorPrice", backdropFloor)
                        .set("marketData.estimatedPrice", estimatedPrice)
                        .set("marketData.priceUpdatedAt", Instant.now());

                bulkOps.updateOne(new Query(Criteria.where("_id").is(gift.getId())), update);
                updatesCount++;
            } catch (Exception e) {
                log.error("Failed to estimate price for gift {}: {}", gift.getId(), e.getMessage());
            }
        }

        if (updatesCount > 0) {
            bulkOps.execute();
            log.debug("Price estimation batch completed. Updated: {}", updatesCount);
        }
    }
}