package com.ceawse.giftdiscovery.dto;

import lombok.Builder;
import java.util.Map;

@Builder
public record MetadataResponseDto(
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