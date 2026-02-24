package com.ceawse.portalsparser.mapper;

import com.ceawse.portalsparser.client.DiscoveryInternalClient;
import com.ceawse.portalsparser.domain.PortalsGiftHistoryDocument;
import com.ceawse.portalsparser.dto.PortalsActionsResponseDto;
import com.ceawse.portalsparser.dto.PortalsNftDto;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class PortalsMapper {
    private static final String MARKETPLACE_NAME = "portals";
    private static final String CURRENCY_TON = "TON";
    private static final BigDecimal NANO_MULTIPLIER = BigDecimal.valueOf(1_000_000_000);

    public PortalsGiftHistoryDocument mapActionToHistory(PortalsActionsResponseDto.ActionDto action, long timestamp) {
        PortalsGiftHistoryDocument doc = new PortalsGiftHistoryDocument();
        doc.setMarketplace(MARKETPLACE_NAME);
        doc.setTimestamp(timestamp);
        doc.setIsOffchain(true);

        if (action.getNft() != null) {
            doc.setAddress(action.getNft().getId());
            doc.setCollectionAddress(action.getNft().getCollectionId());
            doc.setName(formatName(action.getNft().getName(), action.getNft().getExternalCollectionNumber()));
        }

        doc.setEventType(mapEventType(action.getType()));

        if (StringUtils.hasText(action.getAmount())) {
            doc.setPrice(action.getAmount());
            doc.setPriceNano(toNano(action.getAmount()));
            doc.setCurrency(CURRENCY_TON);
        }

        String uniqueHash = (action.getOfferId() != null ? action.getOfferId() : "no_id")
                + "_" + action.getType() + "_" + timestamp;
        doc.setHash(uniqueHash);

        return doc;
    }

    public void enrichHistory(PortalsGiftHistoryDocument doc, DiscoveryInternalClient.MetadataResponse meta) {
        if (meta != null && meta.getModel() != null) {
            doc.setModel(meta.getModel());
            doc.setModelRare(meta.getModelRare());
            doc.setBackdrop(meta.getBackdrop());
            doc.setBackdropRare(meta.getBackdropRare());
            doc.setSymbol(meta.getSymbol());
            doc.setSymbolRare(meta.getSymbolRare());
            doc.setGiftNum(meta.getGiftNum());
            doc.setGiftTotal(meta.getGiftTotal());
        }
    }

    public String createSlug(PortalsNftDto nft) {
        if (nft == null || nft.getName() == null || nft.getExternalCollectionNumber() == null) return null;
        // Portals: "Winter Wreath" + 19852 -> "WinterWreath-19852"
        String base = nft.getName().replaceAll("\\s+", "");
        return base + "-" + nft.getExternalCollectionNumber();
    }

    private String formatName(String rawName, Long number) {
        return number != null ? rawName + " #" + number : rawName;
    }

    private String mapEventType(String rawType) {
        if (rawType == null) return "UNKNOWN";
        return switch (rawType.toLowerCase()) {
            case "listing", "price_update" -> "PUTUPFORSALE";
            case "buy" -> "SOLD";
            default -> rawType.toUpperCase();
        };
    }

    private String toNano(String price) {
        try {
            return new BigDecimal(price).multiply(NANO_MULTIPLIER).toBigInteger().toString();
        } catch (Exception e) {
            return "0";
        }
    }
}