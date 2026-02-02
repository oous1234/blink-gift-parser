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
import org.springframework.cache.annotation.Cacheable;
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

    private static final Pattern RARE_EXTRACTOR = Pattern.compile("(\\d+(\\.\\d+)?)%");
    private static final Pattern QUANTITY_EXTRACTOR = Pattern.compile("(\\d[\\d\\s]*)/(\\d[\\d\\s]*)");
    private static final Pattern CLEAN_NAME_PATTERN = Pattern.compile("^(.*?)\\s*\\d+(\\.\\d+)?%$");

    @Override
    @Cacheable(value = "gift_metadata", key = "#slug + '-' + #id")
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
        String giftName = "Unknown Gift";
        Element metaTitle = doc.selectFirst("meta[property=og:title]");
        if (metaTitle != null) {
            String title = metaTitle.attr("content");
            giftName = title.split("–")[0].split("#")[0].trim();
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

        String rawModel = attrs.get("model");
        String rawBackdrop = attrs.get("backdrop");
        String rawPattern = attrs.get("symbol");

        String cleanModel = cleanTraitName(rawModel);
        String cleanBackdrop = cleanTraitName(rawBackdrop);
        String cleanPattern = cleanTraitName(rawPattern);

        int[] qty = parseQuantity(attrs.getOrDefault("quantity", "0/0"));

        Integer colorCenter = null;
        Integer colorEdge = null;
        Integer colorPattern = null;
        Element svg = doc.selectFirst("div.tgme_gift_preview > svg");
        if (svg != null) {
            Elements stops = svg.select("stop");
            if (stops.size() >= 2) {
                colorCenter = hexToDecimal(stops.get(0).attr("stop-color"));
                colorEdge = hexToDecimal(stops.get(1).attr("stop-color"));
            }
            Element floodTag = svg.selectFirst("[id~=gift.*PatternColor]");
            if (floodTag != null) {
                colorPattern = hexToDecimal(floodTag.attr("flood-color"));
            }
        }

        String modelUrl = String.format("https://api.changes.tg/model/%s/%s.json",
                slug, cleanModel.replace(" ", "%20"));

        String patternUrl = cleanPattern != null ?
                String.format("https://api.changes.tg/symbol/%s/%s.png", slug, cleanPattern.replace(" ", "%20")) : null;

        return NftMetadataResponse.builder()
                .giftSlug(slug + "-" + id)
                .giftName(giftName)
                .giftNum(id)
                .giftMinted(qty[0])
                .giftTotal(qty[1])
                .model(cleanModel)
                .modelRare(extractRareMultiplied(rawModel))
                .backdrop(cleanBackdrop)
                .backdropRare(extractRareMultiplied(rawBackdrop))
                .pattern(cleanPattern)
                .patternRare(extractRareMultiplied(rawPattern))
                .backdropCenterColor(colorCenter)
                .backdropEdgeColor(colorEdge)
                .backdropPatternColor(colorPattern)
                .modelUrl(modelUrl)
                .patternUrl(patternUrl)
                .pageUrl("https://t.me/nft/" + slug + "-" + id)
                .owner(attrs.getOrDefault("owner", "Unknown"))
                .build();
    }

    private String cleanTraitName(String raw) {
        if (raw == null) return "Original";
        Matcher m = CLEAN_NAME_PATTERN.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        return raw.trim();
    }

    private Integer hexToDecimal(String hex) {
        if (hex == null || !hex.startsWith("#")) return null;
        try {
            return Integer.parseInt(hex.substring(1), 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer extractRareMultiplied(String text) {
        if (text == null) return null;
        Matcher m = RARE_EXTRACTOR.matcher(text);
        if (m.find()) {
            try {
                double val = Double.parseDouble(m.group(1));
                return (int) Math.round(val * 10);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private int[] parseQuantity(String text) {
        Matcher m = QUANTITY_EXTRACTOR.matcher(text);
        if (m.find()) {
            try {
                int minted = Integer.parseInt(m.group(1).replaceAll("\\s", ""));
                int total = Integer.parseInt(m.group(2).replaceAll("\\s", ""));
                return new int[]{minted, total};
            } catch (Exception e) { return new int[]{0, 0}; }
        }
        return new int[]{0, 0};
    }
}