package com.ceawse.coreprocessor.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Builder
@Document(collection = "current_sales")
public class CurrentSaleDocument {
    @Id
    private String id;

    @Indexed(unique = true)
    private String address;

    @Indexed
    private String collectionAddress;

    private String name;
    private String price;

    @Indexed
    private Long priceNano;

    private String currency;
    private String seller;
    private Instant listedAt;
    private Instant updatedAt;
    private boolean isOffchain;

    @Indexed
    private String marketplace;

    @Indexed
    private String model;
    private Integer modelRare;

    @Indexed
    private String backdrop;
    private Integer backdropRare;

    private String symbol;
    private Integer symbolRare;
}