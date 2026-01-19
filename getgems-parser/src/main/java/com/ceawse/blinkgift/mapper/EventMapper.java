package com.ceawse.blinkgift.mapper;

import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import com.ceawse.blinkgift.domain.UniqueGiftDocument;
import com.ceawse.blinkgift.dto.GetGemsItemDto;
import com.ceawse.blinkgift.dto.GetGemsSaleItemDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EventMapper {

    private static final String MARKETPLACE_GETGEMS = "getgems";
    private static final Pattern GIFT_NAME_PATTERN = Pattern.compile("#(\\d+)(?:\\s+of\\s+|/)(\\d+)");

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

    public GiftHistoryDocument toSnapshotEntity(GetGemsSaleItemDto item, String snapshotId) {
        GiftHistoryDocument doc = new GiftHistoryDocument();
        doc.setMarketplace(MARKETPLACE_GETGEMS);
        doc.setEventType("SNAPSHOT_LIST");
        doc.setSnapshotId(snapshotId);
        doc.setTimestamp(System.currentTimeMillis());
        doc.setAddress(item.getAddress());
        doc.setCollectionAddress(item.getCollectionAddress());
        doc.setName(item.getName());
        doc.setIsOffchain(item.isOffchain());
        doc.setHash(snapshotId + "_" + item.getAddress());

        if (item.getSale() != null) {
            String nano = item.getSale().getFullPrice();
            doc.setPriceNano(nano);
            doc.setPrice(fromNano(nano));
            doc.setCurrency(item.getSale().getCurrency());
            doc.setOldOwner(item.getOwnerAddress());
        }
        return doc;
    }

    public UniqueGiftDocument toUniqueGiftEntity(GetGemsSaleItemDto item) {
        UniqueGiftDocument.GiftAttributes.GiftAttributesBuilder attrsBuilder = UniqueGiftDocument.GiftAttributes.builder();
        attrsBuilder.updatedAt(Instant.now());

        if (item.getAttributes() != null) {
            for (var attr : item.getAttributes()) {
                if ("Model".equalsIgnoreCase(attr.getTraitType())) attrsBuilder.model(attr.getValue());
                if ("Backdrop".equalsIgnoreCase(attr.getTraitType())) attrsBuilder.backdrop(attr.getValue());
                if ("Symbol".equalsIgnoreCase(attr.getTraitType())) attrsBuilder.symbol(attr.getValue());
            }
        }

        return UniqueGiftDocument.builder()
                .id(item.getAddress())
                .name(item.getName())
                .collectionAddress(item.getCollectionAddress())
                .isOffchain(item.isOffchain())
                .attributes(attrsBuilder.build())
                .lastSeenAt(Instant.now())
                .build();
    }

    public GiftNumbers parseNumbers(String name) {
        if (name == null) return new GiftNumbers(null, null);
        Matcher matcher = GIFT_NAME_PATTERN.matcher(name);
        if (matcher.find()) {
            try {
                return new GiftNumbers(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2))
                );
            } catch (Exception e) {
                return new GiftNumbers(null, null);
            }
        }
        return new GiftNumbers(null, null);
    }

    private String normalizeEventType(String rawType) {
        return rawType == null ? "UNKNOWN" : rawType.toLowerCase();
    }

    private String fromNano(String nano) {
        if (nano == null) return "0";
        try {
            return new BigDecimal(nano).divide(BigDecimal.valueOf(1_000_000_000)).toPlainString();
        } catch (Exception e) {
            return "0";
        }
    }

    public record GiftNumbers(Integer serialNumber, Integer totalLimit) {}
}