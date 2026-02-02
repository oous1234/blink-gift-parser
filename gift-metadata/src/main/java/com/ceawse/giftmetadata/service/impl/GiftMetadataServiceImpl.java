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

    private static final Pattern RARE_EXTRACTOR = Pattern.compile("(\\d+(\\.\\d+)?)%");
    private static final Pattern QUANTITY_EXTRACTOR = Pattern.compile("(\\d[\\d\\s]*)/(\\d[\\d\\s]*)");

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
        // 1. Имя и Слюг
        String fullName = "";
        Element metaTitle = doc.selectFirst("meta[property=og:title]");
        if (metaTitle != null) {
            fullName = metaTitle.attr("content").split("–")[0].trim();
        }
        String giftName = fullName.contains("#") ? fullName.split("#")[0].trim() : fullName;

        // 2. Парсинг таблицы атрибутов (Model, Backdrop, Symbol, Quantity)
        Map<String, String> attrs = new HashMap<>();
        Elements rows = doc.select("table.tgme_gift_table tr");
        for (Element row : rows) {
            Element th = row.selectFirst("th");
            Element td = row.selectFirst("td");
            if (th != null && td != null) {
                attrs.put(th.text().toLowerCase().trim(), td.text().trim());
            }
        }

        // 3. Извлечение редкости (число без %)
        Integer modelRare = extractRare(attrs.get("model"));
        Integer backdropRare = extractRare(attrs.get("backdrop"));
        Integer patternRare = extractRare(attrs.get("symbol")); // В Telegram Symbol часто выступает как Pattern

        // 4. Парсинг тиража (Minted / Total)
        String quantityStr = attrs.getOrDefault("quantity", "");
        int[] quantity = parseQuantity(quantityStr);

        // 5. Работа с цветами (Конвертация HEX в Decimal)
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
            // Поиск цвета паттерна
            Element floodTag = svg.selectFirst("[id~=gift.*PatternColor]");
            if (floodTag != null) {
                colorPattern = hexToDecimal(floodTag.attr("flood-color"));
            }
        }

        // 6. Ссылки на ресурсы (Анимация и Паттерн)
        Element tgsSource = doc.selectFirst("source[type=application/x-tgsticker]");
        String modelUrl = (tgsSource != null) ? tgsSource.attr("srcset") : null;

        String patternUrl = null;
        if (svg != null) {
            Element imageTag = svg.selectFirst("image");
            if (imageTag != null) {
                patternUrl = imageTag.hasAttr("xlink:href") ? imageTag.attr("xlink:href") : imageTag.attr("href");
            }
        }

        return NftMetadataResponse.builder()
                .giftSlug(slug + "-" + id)
                .giftName(giftName)
                .giftNum(id)
                .giftMinted(quantity[0])
                .giftTotal(quantity[1])
                .model(cleanName(attrs.get("model")))
                .modelRare(modelRare)
                .backdrop(cleanName(attrs.get("backdrop")))
                .backdropRare(backdropRare)
                .pattern(cleanName(attrs.get("symbol")))
                .patternRare(patternRare)
                .backdropCenterColor(colorCenter)
                .backdropEdgeColor(colorEdge)
                .backdropPatternColor(colorPattern)
                .modelUrl(normalizeUrl(modelUrl))
                .patternUrl(normalizeUrl(patternUrl))
                .pageUrl("https://t.me/nft/" + slug + "-" + id)
                .owner(attrs.getOrDefault("owner", "Unknown"))
                .build();
    }

    private Integer hexToDecimal(String hex) {
        if (hex == null || !hex.startsWith("#")) return null;
        try {
            return Integer.parseInt(hex.substring(1), 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer extractRare(String text) {
        if (text == null) return null;
        Matcher m = RARE_EXTRACTOR.matcher(text);
        if (m.find()) {
            // Превращаем "1.5%" в 1 (целое число) или можно оставить Double,
            // но вы просили целое для бекенда
            return (int) Double.parseDouble(m.group(1));
        }
        return null;
    }

    private String cleanName(String text) {
        if (text == null) return null;
        return text.split("\\d+(\\.\\d+)?%")[0].trim();
    }

    private int[] parseQuantity(String text) {
        Matcher m = QUANTITY_EXTRACTOR.matcher(text);
        if (m.find()) {
            int minted = Integer.parseInt(m.group(1).replaceAll("\\s", ""));
            int total = Integer.parseInt(m.group(2).replaceAll("\\s", ""));
            return new int[]{minted, total};
        }
        return new int[]{0, 0};
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/file/")) return "https://cdn4.cdn-telegram.org" + url;
        return url;
    }
}