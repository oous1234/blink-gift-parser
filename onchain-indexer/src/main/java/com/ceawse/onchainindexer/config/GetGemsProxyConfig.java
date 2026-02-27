package com.ceawse.onchainindexer.config;

import feign.Client;
import feign.RequestInterceptor;
import feign.okhttp.OkHttpClient;
import okhttp3.ConnectionPool;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

public class GetGemsProxyConfig {

    private static final String API_KEY = "1767696328881-mainnet-10772317-r-JZxQ9TmGu7URUGscDZjznrMzVtNBcCpVlOTgsFx9t8Xv8c4o";

    @Bean
    public Client feignClient() {
        okhttp3.OkHttpClient delegate = new okhttp3.OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.of44.fun", 8888)))

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
            template.header("Authorization", API_KEY);
            template.header("Accept", "application/json");
            template.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

            template.header("Connection", "close");

            String traceId = MDC.get("traceId");
            if (traceId != null) {
                template.header("X-Trace-Id", traceId);
            }
        };
    }
}