package com.ceawse.blinkgift;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = "com.ceawse.blinkgift.client")
public class BlinkGiftApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlinkGiftApplication.class, args);
    }

}
