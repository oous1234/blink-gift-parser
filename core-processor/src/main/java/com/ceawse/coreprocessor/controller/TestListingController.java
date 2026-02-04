package com.ceawse.coreprocessor.controller;

import com.ceawse.coreprocessor.dto.ListingEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestListingController {

    private final RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/mock-listing")
    public String mockListing(@RequestBody ListingEvent event) {
        if (event.getTimestamp() == 0) event.setTimestamp(System.currentTimeMillis());
        if (event.getId() == null) event.setId("test-hash-" + System.currentTimeMillis());

        redisTemplate.convertAndSend("listing_events", event);

        return "✅ Test listing for '" + event.getName() + "' sent to Redis!";
    }
}