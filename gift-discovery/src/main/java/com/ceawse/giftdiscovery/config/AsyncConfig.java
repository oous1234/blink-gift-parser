package com.ceawse.giftdiscovery.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "virtualThreadExecutor")
    public ExecutorService virtualThreadExecutor() {
        // Мы оборачиваем стандартный VirtualThreadExecutor, чтобы он пробрасывал MDC
        ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();

        return new ExecutorServiceForwardingMdc(delegate);
    }

    @RequiredArgsConstructor
    private static class ExecutorServiceForwardingMdc extends java.util.concurrent.AbstractExecutorService {
        private final ExecutorService delegate;

        @Override
        public void execute(Runnable command) {
            Map<String, String> context = MDC.getCopyOfContextMap();
            delegate.execute(() -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    command.run();
                } finally {
                    MDC.clear();
                }
            });
        }

        @Override public void shutdown() { delegate.shutdown(); }
        @Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
        @Override public boolean isShutdown() { return delegate.isShutdown(); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
    }
}