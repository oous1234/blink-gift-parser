package com.ceawse.coreprocessor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListingEvent {
    private String id;
    private String name;
    private String model;
    private String backdrop;
    private String symbol;
    private BigDecimal price;
    private String marketplace;
    private String address;
    private boolean isOffchain;
    private long timestamp;
}