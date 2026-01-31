package com.ceawse.giftdiscovery.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "unique_gifts")
public class UniqueGiftDocument {
    @Id
    private String id;
    private String name;

    private Integer serialNumber;
    private Integer totalLimit;

    private String collectionAddress;
    private Boolean isOffchain;

    private Instant firstSeenAt;
    private Instant lastSeenAt;

    private GiftAttributes attributes;
    private GiftParameters parameters;
    private MarketData marketData;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GiftAttributes {
        private String model;
        private String backdrop;
        private String symbol;

        private BigDecimal modelPrice;
        private Integer modelRarityCount;

        private BigDecimal backdropPrice;
        private Integer backdropRarityCount;

        private BigDecimal symbolPrice;
        private Integer symbolRarityCount;

        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GiftParameters {
        private AttributeDetail model;
        private AttributeDetail backdrop;
        private AttributeDetail symbol;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributeDetail {
        private String value;
        private BigDecimal floorPrice;
        private Integer rarityCount;
        private Double rarityPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketData {
        private BigDecimal collectionFloorPrice;
        private BigDecimal estimatedPrice;
        private Instant priceUpdatedAt;
    }

    public boolean isOffchainSafe() {
        return Boolean.TRUE.equals(isOffchain);
    }
}