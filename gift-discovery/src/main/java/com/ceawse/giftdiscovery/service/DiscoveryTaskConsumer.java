package com.ceawse.giftdiscovery.service;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;

public interface DiscoveryTaskConsumer extends StreamListener<String, MapRecord<String, String, String>> {

    @Override
    void onMessage(MapRecord<String, String, String> message);
}