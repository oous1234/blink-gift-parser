package com.ceawse.blinkgift.config;

import feign.Client;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.okhttp.OkHttpClient;
import okhttp3.ConnectionPool;
import okhttp3.Protocol;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class GetGemsSpecificConfig {

    @Bean
    public Client feignClient() {
        okhttp3.OkHttpClient delegate = new okhttp3.OkHttpClient.Builder()
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 8000)))
                .build();
        return new OkHttpClient(delegate);
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            template.header("Authorization", "1767696328881-mainnet-10772317-r-JZxQ9TmGu7URUGscDZjznrMzVtNBcCpVlOTgsFx9t8Xv8c4o");
            template.header("Connection", "close");
            template.header("Accept", "application/json");
            template.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

            String traceId = MDC.get("traceId");
            if (traceId != null) {
                template.header("X-Trace-Id", traceId);
            }
        };
    }

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(100L, TimeUnit.SECONDS.toMillis(1L), 3);
    }
}