package com.ceawse.giftmetadata.service;

import com.ceawse.giftmetadata.dto.NftMetadataResponse;

public interface GiftMetadataService {
    NftMetadataResponse getMetadata(String slug, Integer id);
}