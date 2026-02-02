package com.ceawse.portalsparser.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "gift_history")
public class PortalsGiftHistoryDocument {
    @Id
    private String id;

    @Indexed
    private String collectionAddress;

    @Indexed
    private String address;

    @Indexed(unique = true)
    private String hash;

    private String name;
    private Long timestamp;
    private String eventType;
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

    @Indexed
    private String snapshotId;
    private String eventPayload;

    @Indexed
    private String marketplace;
}