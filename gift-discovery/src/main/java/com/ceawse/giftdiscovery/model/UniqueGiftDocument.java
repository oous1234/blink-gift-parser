package com.ceawse.giftdiscovery.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
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
    private String slug;
    private Integer giftNum;
    private Integer giftMinted;
    private Integer giftTotal;

    @Indexed
    private String collectionAddress;
    private String collectionName;

    private String model;
    private Integer modelRare;

    private String backdrop;
    private Integer backdropRare;

    private String symbol;
    private Integer symbolRare;

    private Integer backdropCenterColor;
    private Integer backdropEdgeColor;
    private Integer backdropPatternColor;

    private String modelUrl;
    private String patternUrl;
    private String pageUrl;

    private MarketData marketData;
    private Boolean isResalable;

    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketData {
        private BigDecimal collectionFloorPrice;
        private BigDecimal estimatedPrice;

        private BigDecimal modelFloorPrice;
        private BigDecimal backdropFloorPrice;

        private Instant priceUpdatedAt;
    }
}