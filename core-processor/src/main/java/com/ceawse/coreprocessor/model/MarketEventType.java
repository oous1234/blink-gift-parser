package com.ceawse.coreprocessor.model;

public enum MarketEventType {
    PUTUPFORSALE,
    CANCELSALE,
    SOLD,
    UNKNOWN;

    public static MarketEventType fromString(String type) {
        if (type == null) return UNKNOWN;
        try {
            return valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}