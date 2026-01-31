package com.ceawse.blinkgift.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetGemsHistoryDto {
    private boolean success;
    private ResponseData response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseData {
        private String cursor;
        private List<GetGemsItemDto> items;
    }
}