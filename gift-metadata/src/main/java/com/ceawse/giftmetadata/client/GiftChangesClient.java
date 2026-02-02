package com.ceawse.giftmetadata.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

@FeignClient(
        name = "gift-changes-client",
        url = "https://api.changes.tg"
)
public interface GiftChangesClient {

    @GetMapping("/model/{gift}/{model}/info")
    Map<String, Object> getModelInfo(@PathVariable("gift") String gift, @PathVariable("model") String model);

    @GetMapping("/backdrop/{gift}/{backdrop}/info")
    Map<String, Object> getBackdropInfo(@PathVariable("gift") String gift, @PathVariable("backdrop") String backdrop);
}