package com.ujjwal.search_indexer_service.dto;

import com.ujjwal.search_indexer_service.document.OrderDocument;

import java.util.List;

public record OrderSearchResponse(List<OrderDocument> results, long totalHits, int page, int size) {
}
