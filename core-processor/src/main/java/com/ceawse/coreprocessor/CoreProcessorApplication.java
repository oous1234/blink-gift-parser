package com.ceawse.coreprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableFeignClients(basePackages = "com.ceawse.coreprocessor.client")
public class CoreProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreProcessorApplication.class, args);
    }

}
