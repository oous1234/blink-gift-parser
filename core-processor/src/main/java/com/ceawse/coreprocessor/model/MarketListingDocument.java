package com.ceawse.coreprocessor.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@Document(collection = "market_listings_history")
public class MarketListingDocument {
    @Id
    private String id;

    @Indexed
    private String name;

    @Indexed
    private String model;

    @Indexed
    private String backdrop;

    @Indexed
    private String symbol;

    private BigDecimal price;
    private String currency;

    @Indexed
    private String marketplace;

    @Indexed(name = "expire_after_48h", expireAfter = "48h")
    private Date createdAt;

    private String address;
    private String seller;
    private boolean isOffchain;
}