package com.ceawse.blinkgift.client;

import com.ceawse.blinkgift.config.GetGemsSpecificConfig;
import com.ceawse.blinkgift.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "getGemsClient",
        url = "https://api.getgems.io/public-api",
        configuration = GetGemsSpecificConfig.class
)
public interface GetGemsApiClient {

    @GetMapping("/v1/nfts/history/gifts")
    GetGemsHistoryDto getHistory(
            @RequestParam("minTime") Long minTime,
            @RequestParam(value = "maxTime", required = false) Long maxTime,
            @RequestParam("limit") int limit,
            @RequestParam(value = "after", required = false) String cursor,
            @RequestParam("types") List<String> types,
            @RequestParam("reverse") boolean reverse
    );

    @GetMapping("/v1/nfts/on-sale/{collectionAddress}")
    GetGemsSalePageDto getOnSale(
            @PathVariable("collectionAddress") String collectionAddress,
            @RequestParam("limit") int limit,
            @RequestParam(value = "after", required = false) String cursor
    );

    @GetMapping("/v1/nft/{address}")
    GetGemsNftDetailDto getNftDetails(@PathVariable("address") String address);

    @GetMapping("/v1/collection/attributes/{collectionAddress}")
    GetGemsAttributesDto getAttributes(
            @PathVariable("collectionAddress") String collectionAddress
    );

    @GetMapping("/v1/collection/stats/{collectionAddress}")
    GetGemsCollectionStatsDto getCollectionStats(
            @PathVariable("collectionAddress") String collectionAddress
    );

    @GetMapping("/v1/collections/{address}")
    GetGemsCollectionDto getCollection(@PathVariable("address") String address);
}