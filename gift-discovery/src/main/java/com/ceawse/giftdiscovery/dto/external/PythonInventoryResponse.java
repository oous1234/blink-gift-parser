package com.ceawse.giftdiscovery.dto.external;

import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
public class PythonInventoryResponse {
    private String user_id;
    private int total_count;
    private List<InventoryItem> items;
    private String next_offset;

    @Data
    public static class InventoryItem {
        private String gift_id;
        private String slug;
        private Instant date;
        private String nft_address;
        private Integer serial_number;
    }
}