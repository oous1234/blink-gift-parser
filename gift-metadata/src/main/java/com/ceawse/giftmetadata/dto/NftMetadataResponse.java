package com.ceawse.giftmetadata.dto;

import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonProperty;

@Builder
public record NftMetadataResponse(
        String giftSlug,
        String giftName,
        Integer giftNum,
        Integer giftMinted,
        Integer giftTotal,
        String model,
        Integer modelRare,
        String pattern,
        Integer patternRare,
        String backdrop,
        Integer backdropRare,

        Integer backdropCenterColor,
        Integer backdropEdgeColor,
        Integer backdropPatternColor,

        String modelUrl,
        String patternUrl,
        String pageUrl,
        String owner
) {}