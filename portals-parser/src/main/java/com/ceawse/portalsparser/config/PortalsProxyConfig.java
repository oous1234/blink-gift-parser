package com.ceawse.portalsparser.config;

import feign.Client;
import feign.RequestInterceptor;
import feign.okhttp.OkHttpClient;
import okhttp3.ConnectionPool;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

import feign.Logger;

public class PortalsProxyConfig {

    private String authToken;

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC; 
    }

    @Bean
    public Client feignClient() {
        okhttp3.OkHttpClient delegate = new okhttp3.OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .build();
        return new OkHttpClient(delegate);
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            template.header("Authorization", authToken);
            template.header("Accept", "application/json, text/plain, */*");

            template.header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8");
            template.header("Origin", "https://portal-market.com");
            template.header("Referer", "https://portal-market.com/");
            template.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");
            template.header("Connection", "keep-alive");
            String traceId = MDC.get("traceId");
            if (traceId != null) {
                template.header("X-Trace-Id", traceId);
            }
        };
    }
}