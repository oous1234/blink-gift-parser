package com.ceawse.coreprocessor.service;

import com.ceawse.coreprocessor.model.CurrentSaleDocument;
import com.ceawse.coreprocessor.model.GiftHistoryDocument;
import com.ceawse.coreprocessor.model.SoldGiftDocument;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class MarketEventMapper {

    public CurrentSaleDocument toCurrentSale(GiftHistoryDocument event) {
        return CurrentSaleDocument.builder()
                .address(event.getAddress())
                .collectionAddress(event.getCollectionAddress())
                .name(event.getName())
                .price(event.getPrice())
                .priceNano(parseNano(event.getPriceNano()))
                .currency(event.getCurrency())
                .seller(event.getOldOwner())
                .marketplace(event.getMarketplace())
                .isOffchain(Boolean.TRUE.equals(event.getIsOffchain()))
                .listedAt(Instant.ofEpochMilli(event.getTimestamp()))
                .updatedAt(Instant.now())
                .model(event.getModel())
                .modelRare(event.getModelRare())
                .backdrop(event.getBackdrop())
                .backdropRare(event.getBackdropRare())
                .symbol(event.getSymbol())
                .symbolRare(event.getSymbolRare())
                .build();
    }

    public void updateCurrentSale(CurrentSaleDocument sale, GiftHistoryDocument event) {
        sale.setName(event.getName());
        sale.setPrice(event.getPrice());
        sale.setPriceNano(parseNano(event.getPriceNano()));
        sale.setCurrency(event.getCurrency());
        sale.setSeller(event.getOldOwner());
        sale.setMarketplace(event.getMarketplace());
        sale.setOffchain(Boolean.TRUE.equals(event.getIsOffchain()));
        sale.setUpdatedAt(Instant.now());

        if (event.getModel() != null) sale.setModel(event.getModel());
        if (event.getBackdrop() != null) sale.setBackdrop(event.getBackdrop());
        if (event.getSymbol() != null) sale.setSymbol(event.getSymbol());
    }

    public SoldGiftDocument toSoldGift(GiftHistoryDocument event) {
        return SoldGiftDocument.builder()
                .id(event.getHash())
                .address(event.getAddress())
                .collectionAddress(event.getCollectionAddress())
                .name(event.getName())
                .price(event.getPrice())
                .priceNano(parseNano(event.getPriceNano()))
                .currency(event.getCurrency())
                .seller(event.getOldOwner())
                .buyer(event.getNewOwner())
                .soldAt(Instant.ofEpochMilli(event.getTimestamp()))
                .isOffchain(Boolean.TRUE.equals(event.getIsOffchain()))
                .marketplace(event.getMarketplace())
                .model(event.getModel())
                .modelRare(event.getModelRare())
                .backdrop(event.getBackdrop())
                .backdropRare(event.getBackdropRare())
                .symbol(event.getSymbol())
                .symbolRare(event.getSymbolRare())
                .build();
    }

    private Long parseNano(String nanoStr) {
        if (nanoStr == null) return 0L;
        try {
            return Long.parseLong(nanoStr);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}