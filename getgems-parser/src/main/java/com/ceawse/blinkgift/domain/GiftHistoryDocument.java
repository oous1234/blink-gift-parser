package com.ceawse.blinkgift.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "gift_history")
public class GiftHistoryDocument {
    @Id
    private String id;

    @Indexed
    private String collectionAddress;

    @Indexed
    private String address;

    @Indexed(unique = true)
    private String hash;

    private String lt;
    private String name;
    private Long timestamp;
    private String eventType;

    @Indexed
    private String marketplace;
    private Boolean isOffchain;

    private String price;
    private String priceNano;
    private String currency;

    private String oldOwner;
    private String newOwner;

    private String model;
    private Integer modelRare;
    private String backdrop;
    private Integer backdropRare;
    private String symbol;
    private Integer symbolRare;
    private Integer giftNum;
    private Integer giftTotal;
}