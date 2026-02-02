package com.ceawse.giftdiscovery.worker;

import com.ceawse.giftdiscovery.service.MarketDataService;
import com.ceawse.giftdiscovery.service.PriceEstimationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnrichmentScheduler {

    private final MarketDataService marketDataService;
    private final PriceEstimationService priceEstimationService;

    @Scheduled(fixedDelay = 60000)
    public void refreshCache() {
        marketDataService.refreshCache();
    }

    @Scheduled(fixedDelay = 2000)
    public void estimatePrices() {
        priceEstimationService.estimateGiftPrices();
    }

}