package com.ceawse.giftdiscovery.service;

import com.ceawse.giftdiscovery.model.UniqueGiftDocument;

public interface DiscoveryService {

    UniqueGiftDocument getOrPopulateMetadata(String slug);

    void processRegistryStream();
    void processHistoryStream();
}