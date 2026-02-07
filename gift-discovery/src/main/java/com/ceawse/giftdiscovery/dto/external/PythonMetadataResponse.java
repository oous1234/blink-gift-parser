package com.ceawse.giftdiscovery.dto.external;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PythonMetadataResponse {
    private String id;
    private String title;
    private String slug;
    private Integer serial_number;
    private Integer total_issued;
    private Long owner_id;
    private String owner_name;
    private String owner_address;
    private List<Attribute> attributes;
    private boolean is_resalable;
    private Long price_amount;
    private String price_currency;

    @Data
    public static class Attribute {
        private String type;
        private String name;
        private Double rarity_percent;
        private Map<String, String> colors;
    }
}