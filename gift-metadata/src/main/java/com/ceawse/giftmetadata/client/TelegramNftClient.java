package com.ceawse.giftmetadata.client;

import com.ceawse.giftmetadata.config.GetGemsProxyConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "telegram-nft-client",
        url = "https://t.me",
        configuration = GetGemsProxyConfig.class
)
public interface TelegramNftClient {

    @GetMapping("/nft/{slug}-{id}")
    String getNftHtml(@PathVariable("slug") String slug, @PathVariable("id") Integer id);
}