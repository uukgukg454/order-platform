package com.ujjwal.search_indexer_service.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Denormalized, search-friendly view of an order, indexed into Elasticsearch's
 * "orders" index (INDEX_NAME below) under an id equal to orderId.toString()
 * — see OrderSearchService for why that specific choice of document id
 * matters.
 *
 * No @Document / @Field(type = FieldType.Keyword) here: those are Spring
 * Data Elasticsearch annotations, and this module deliberately doesn't
 * depend on spring-data-elasticsearch at all — see ElasticsearchConfig's own
 * comment for why (its 6.1.0 has a mandatory, non-optional dependency on
 * elasticsearch-java 9.4.2, which would fight the 8.15.5 client this module
 * is pinned to on the classpath). The orderId/customerId/status/
 * items.productId fields still end up mapped as Elasticsearch "keyword"
 * type — enforced by the explicit mapping ElasticsearchConfig's
 * ensureOrdersIndexExists bean sends to Elasticsearch directly, not by an
 * annotation on this class. Nothing here reads annotations on OrderDocument
 * at runtime anyway: indexing and querying both go through
 * ElasticsearchClient directly (see OrderSearchService), not through
 * ElasticsearchOperations/a repository, which is the only thing that would
 * ever interpret such annotations in the first place.
 *
 * status mirrors order-service's own OrderStatus vocabulary (CREATED, PAID,
 * PAYMENT_FAILED, ...) rather than inventing a parallel one — kept as a
 * plain String here, not that enum, since taking a compile-time dependency
 * on order-service's entity package would break this project's no-shared-
 * schema-module convention.
 *
 * totalAmount is a BigDecimal for consistency with order-service's own Order
 * entity, but this index is a read model for search, not the ledger of
 * record — Postgres (via order-service/payment-service) stays authoritative
 * for the actual charged amount; this copy exists to be queried, not to be
 * trusted for money math.
 */
public record OrderDocument(
        UUID orderId,
        UUID customerId,
        String status,
        BigDecimal totalAmount,
        String currency,
        List<Item> items,
        Instant createdAt
) {
    // Centralized here, not duplicated in OrderSearchService/ElasticsearchConfig
    // as a private constant in each — every place that needs the index name
    // (the search service's read/write calls, the startup mapping bootstrap)
    // now references this one definition.
    public static final String INDEX_NAME = "orders";

    public record Item(UUID productId, int quantity) {
    }
}
