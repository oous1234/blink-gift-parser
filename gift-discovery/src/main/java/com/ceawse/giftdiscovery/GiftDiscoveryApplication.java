package com.ceawse.giftdiscovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.ceawse.giftdiscovery")
@EnableFeignClients(basePackages = "com.ceawse.giftdiscovery.client")
@EnableMongoRepositories(basePackages = "com.ceawse.giftdiscovery.repository.mongo")
@EnableRedisRepositories(basePackages = "com.ceawse.giftdiscovery.repository.redis")
public class GiftDiscoveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(GiftDiscoveryApplication.class, args);
    }
}
