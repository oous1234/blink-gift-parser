package com.ceawse.portalsparser.client;

import com.ceawse.portalsparser.config.PortalsProxyConfig;
import com.ceawse.portalsparser.dto.PortalsActionsResponseDto;
import com.ceawse.portalsparser.dto.PortalsCollectionsResponseDto;
import com.ceawse.portalsparser.dto.PortalsFiltersResponseDto;
import com.ceawse.portalsparser.dto.PortalsSearchResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "portalsClient",
        url = "${portals.api-url}",
        configuration = PortalsProxyConfig.class
)
public interface PortalsApiClient {

    @GetMapping("/market/actions/")
    PortalsActionsResponseDto getMarketActivity(
            @RequestParam("offset") int offset,
            @RequestParam("limit") int limit,
            @RequestParam("sort_by") String sortBy,
            @RequestParam("action_types") String actionTypes
    );

    @GetMapping("/collections")
    PortalsCollectionsResponseDto getCollections(@RequestParam("limit") int limit);

    @GetMapping("/collections/filters")
    PortalsFiltersResponseDto getFilters(@RequestParam("short_names") String shortNames);
}