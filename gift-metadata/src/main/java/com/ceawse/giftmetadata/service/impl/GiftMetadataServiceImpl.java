package com.ceawse.giftmetadata.service.impl;

import com.ceawse.giftmetadata.client.TelegramNftClient;
import com.ceawse.giftmetadata.dto.NftMetadataResponse;
import com.ceawse.giftmetadata.exception.InformationException;
import com.ceawse.giftmetadata.service.GiftMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GiftMetadataServiceImpl implements GiftMetadataService {

    private final TelegramNftClient nftClient;

    @Override
    public NftMetadataResponse getMetadata(String slug, Integer id) {
        String html;
        try {
            html = nftClient.getNftHtml(slug, id);
        } catch (Exception e) {
            log.error("Error fetching NFT data for {}-{}", slug, id, e);
            throw InformationException.builder("NFT not found or Telegram error").build();
        }

        Document doc = Jsoup.parse(html);

        if (doc.selectFirst("div.tgme_page_error_title") != null) {
            throw InformationException.builder("NFT collectible not found on Telegram").build();
        }

        return parseDocument(doc, slug, id);
    }

    private NftMetadataResponse parseDocument(Document doc, String slug, Integer id) {
        String nftName = "";
        Element metaTitle = doc.selectFirst("meta[property=og:title]");
        if (metaTitle != null) {
            nftName = metaTitle.attr("content").split("–")[0].trim();
        }

        Map<String, String> attrs = new HashMap<>();
        Elements rows = doc.select("table.tgme_gift_table tr");
        for (Element row : rows) {
            Element th = row.selectFirst("th");
            Element td = row.selectFirst("td");
            if (th != null && td != null) {
                attrs.put(th.text().toLowerCase().trim(), td.text().trim());
            }
        }

        String gradFrom = "";
        String gradTo = "";
        String patternUrl = "";
        String patternTint = "";

        Element svg = doc.selectFirst("div.tgme_gift_preview > svg");
        if (svg != null) {
            Elements stops = svg.select("stop");
            if (stops.size() >= 2) {
                gradFrom = stops.get(0).attr("stop-color");
                gradTo = stops.get(1).attr("stop-color");
            }

            Element imageTag = svg.selectFirst("image");
            if (imageTag != null) {
                patternUrl = imageTag.hasAttr("xlink:href") ? imageTag.attr("xlink:href") : imageTag.attr("href");
            }

            Element floodTag = svg.selectFirst("[id~=gift.*PatternColor]");
            if (floodTag != null) {
                patternTint = floodTag.attr("flood-color");
            }
        }

        Element tgsSource = doc.selectFirst("source[type=application/x-tgsticker]");
        String tgsUrl = (tgsSource != null) ? tgsSource.attr("srcset") : null;

        return NftMetadataResponse.builder()
                .collectibleId(id)
                .nftName(nftName)
                .owner(attrs.getOrDefault("owner", ""))
                .model(attrs.getOrDefault("model", ""))
                .backdrop(attrs.getOrDefault("backdrop", ""))
                .symbol(attrs.getOrDefault("symbol", ""))
                .quantity(attrs.getOrDefault("quantity", ""))
                .gradientFrom(gradFrom)
                .gradientTo(gradTo)
                .patternPngUrl(normalizeUrl(patternUrl))
                .patternTint(patternTint)
                .tgsUrl(normalizeUrl(tgsUrl))
                .pageUrl(String.format("https://t.me/nft/%s-%d", slug, id))
                .build();
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/file/")) return "https://cdn4.cdn-telegram.org" + url;
        return url;
    }
}