package com.ujjwal.search_indexer_service.controller;

import com.ujjwal.search_indexer_service.dto.OrderSearchResponse;
import com.ujjwal.search_indexer_service.service.OrderSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final OrderSearchService orderSearchService;

    public SearchController(OrderSearchService orderSearchService) {
        this.orderSearchService = orderSearchService;
    }

    // orderId/customerId/status/q are all optional and independent — any
    // combination, or none, can be supplied. OrderSearchService.search falls
    // back to match-all (paginated) when nothing is present. orderId/
    // customerId/status are exact-match filters against their now-keyword-
    // mapped fields; q is a lighter free-text match against currency, the
    // one remaining dynamically-mapped string field — see
    // OrderSearchService.search's own comment for why status moved out of
    // free-text matching.
    @GetMapping("/orders")
    public OrderSearchResponse searchOrders(@RequestParam(required = false) UUID orderId,
                                             @RequestParam(required = false) UUID customerId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return orderSearchService.search(orderId, customerId, status, q, page, size);
    }
}
