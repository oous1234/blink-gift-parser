package com.ceawse.coreprocessor.worker;

import com.ceawse.coreprocessor.model.SoldGiftDocument;
import com.ceawse.coreprocessor.repository.redis.MarketDataRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticsWorker {
    private final MongoTemplate mongoTemplate;
    private final MarketDataRedisRepository redisRepository;

    @Scheduled(fixedDelay = 3600000)
    public void calculate30dStats() {
        log.info("Starting 30d statistics calculation...");
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("soldAt").gte(thirtyDaysAgo).and("model").exists(true)),
                Aggregation.group("collectionAddress", "model")
                        .avg("priceNano").as("avgPriceNano")
                        .count().as("dealsCount"),
                Aggregation.project("avgPriceNano", "dealsCount")
                        .and("_id.collectionAddress").as("collectionAddress")
                        .and("_id.model").as("model")
        );

        AggregationResults<ModelStats> results = mongoTemplate.aggregate(agg, SoldGiftDocument.class, ModelStats.class);
        List<ModelStats> statsList = results.getMappedResults();

        for (ModelStats stat : statsList) {
            BigDecimal avgPrice = BigDecimal.valueOf(stat.avgPriceNano)
                    .divide(BigDecimal.valueOf(1_000_000_000), 2, RoundingMode.HALF_UP);

            redisRepository.saveModelAvgPrice(stat.collectionAddress, stat.model, avgPrice);
            redisRepository.saveModelDealsCount(stat.collectionAddress, stat.model, stat.dealsCount);
        }

        log.info("Statistics updated for {} models.", statsList.size());
    }

    private static class ModelStats {
        String collectionAddress;
        String model;
        Double avgPriceNano;
        Long dealsCount;
    }
}