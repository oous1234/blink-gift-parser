package com.ceawse.blinkgift.mapper;

import com.ceawse.blinkgift.client.DiscoveryInternalClient;
import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import com.ceawse.blinkgift.dto.GetGemsItemDto;
import com.ceawse.blinkgift.dto.GetGemsSaleItemDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Pattern;

@Component
public class EventMapper {
    private static final String MARKETPLACE_GETGEMS = "getgems";

    public GiftHistoryDocument toHistoryEntity(GetGemsItemDto dto) {
        GiftHistoryDocument doc = new GiftHistoryDocument();
        doc.setMarketplace(MARKETPLACE_GETGEMS);
        doc.setCollectionAddress(dto.getCollectionAddress());
        doc.setAddress(dto.getAddress());
        doc.setName(dto.getName());
        doc.setTimestamp(dto.getTimestamp());
        doc.setHash(dto.getHash());
        doc.setLt(dto.getLt());
        doc.setIsOffchain(dto.isOffchain());

        if (dto.getTypeData() != null) {
            var typeData = dto.getTypeData();
            doc.setEventType(normalizeEventType(typeData.getType()));
            doc.setPrice(typeData.getPrice());
            doc.setPriceNano(typeData.getPriceNano());
            doc.setCurrency(typeData.getCurrency());
            doc.setOldOwner(typeData.getOldOwner());
            doc.setNewOwner(typeData.getNewOwner());
        }
        return doc;
    }

    public void enrichHistory(GiftHistoryDocument doc, DiscoveryInternalClient.MetadataResponse meta) {
        if (meta == null) return;
        doc.setModel(meta.getModel());
        doc.setModelRare(meta.getModelRare());
        doc.setBackdrop(meta.getBackdrop());
        doc.setBackdropRare(meta.getBackdropRare());
        doc.setSymbol(meta.getSymbol());
        doc.setSymbolRare(meta.getSymbolRare());
        doc.setGiftNum(meta.getGiftNum());
        doc.setGiftTotal(meta.getGiftTotal());
    }

    public String createSlug(String name) {
        if (name == null) return null;
        try {
            String cleanName = name.split("#")[0].replaceAll("\\s+", "");
            String number = name.split("#")[1].split(" ")[0].trim();
            return cleanName + "-" + number;
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeEventType(String rawType) {
        if (rawType == null) return "UNKNOWN";
        return switch (rawType.toLowerCase()) {
            case "putupforsale", "put_up_for_sale" -> "PUTUPFORSALE";
            case "sold" -> "SOLD";
            case "cancelsale", "cancel_sale" -> "CANCELSALE";
            default -> rawType.toUpperCase();
        };
    }
}