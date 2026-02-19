package com.ceawse.giftdiscovery.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_inventory")
@CompoundIndexes({
        @CompoundIndex(name = "user_price_idx", def = "{'userId': 1, 'estimatedPrice': -1}"),
        @CompoundIndex(name = "user_model_idx", def = "{'userId': 1, 'model': 1}"),
        @CompoundIndex(name = "user_acquired_idx", def = "{'userId': 1, 'acquiredAt': -1}")
})
public class UserInventoryDocument {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String slug;
    private String name;
    private Integer serialNumber;

    private String model;
    private String backdrop;
    private String symbol;

    private BigDecimal estimatedPrice;
    private Instant acquiredAt;

    private String image;
}