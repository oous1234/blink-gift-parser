package com.ceawse.giftmetadata.dto;

import lombok.Builder;

@Builder
public record NftMetadataResponse(
        Integer collectibleId,
        String nftName,
        String owner,
        String model,
        String backdrop,
        String symbol,
        String quantity,
        String gradientFrom,
        String gradientTo,
        String patternPngUrl,
        String patternTint,
        String tgsUrl,
        String pageUrl
) {}