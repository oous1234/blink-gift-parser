package com.ceawse.giftdiscovery.client;

import com.ceawse.giftdiscovery.dto.MetadataResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "giftMetadataClient", url = "http://localhost:7785")
public interface GiftMetadataFeignClient {

    @GetMapping("/api/v1/metadata/{slug}/{id}")
    MetadataResponseDto getMetadata(
            @PathVariable("slug") String slug,
            @PathVariable("id") Integer id
    );
}