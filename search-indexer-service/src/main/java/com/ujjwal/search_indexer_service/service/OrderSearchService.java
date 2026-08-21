package com.ujjwal.search_indexer_service.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.ujjwal.search_indexer_service.document.OrderDocument;
import com.ujjwal.search_indexer_service.dto.OrderSearchResponse;
import com.ujjwal.search_indexer_service.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns every read/write against the "orders" Elasticsearch index — the
 * three Kafka listeners and SearchController all go through this rather
 * than touching ElasticsearchClient directly.
 */
@Service
public class OrderSearchService {

    private static final Logger log = LoggerFactory.getLogger(OrderSearchService.class);

    private final ElasticsearchClient client;

    public OrderSearchService(ElasticsearchClient client) {
        this.client = client;
    }

    public void indexNewOrder(OrderCreatedEvent event) {
        OrderDocument document = new OrderDocument(
                event.orderId(),
                event.customerId(),
                "CREATED",
                event.totalAmount(),
                event.currency(),
                event.items().stream()
                        .map(item -> new OrderDocument.Item(item.productId(), item.quantity()))
                        .toList(),
                // OrderCreatedEvent carries no timestamp of its own (see that
                // record's own comment in event/OrderCreatedEvent.java) — this
                // is the instant search-indexer-service processed the event,
                // not order-service's actual Order.createdAt column. Good
                // enough for a search-friendly read model; worth knowing this
                // isn't the authoritative creation time if it's ever compared
                // against order-service's own timestamp.
                Instant.now()
        );

        try {
            // Document id = orderId.toString(), not an Elasticsearch-generated
            // id: indexing by a fixed, deterministic id makes a redelivered
            // OrderCreatedEvent (Kafka only guarantees at-least-once) a
            // natural no-op upsert — the second index() call just overwrites
            // the same document with identical content — rather than creating
            // a duplicate the way an auto-generated id would.
            //
            // This is also exactly why this listener, unlike every other
            // consumer in this project, does NOT check a Redis idempotency
            // key before processing: that check exists to stop a *side
            // effect* (decrementing stock, charging a payment) from running
            // twice. A repeated PUT-by-id to Elasticsearch has no such side
            // effect to guard against — it's already idempotent by
            // construction, so adding the same guard here would just be
            // redundant Redis traffic protecting against a problem that
            // can't happen on this path.
            client.index(i -> i.index(OrderDocument.INDEX_NAME).id(document.orderId().toString()).document(document));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to index order " + document.orderId(), e);
        }
    }

    public void updateStatus(UUID orderId, String status) {
        try {
            client.update(u -> u.index(OrderDocument.INDEX_NAME).id(orderId.toString()).doc(new StatusUpdate(status)), OrderDocument.class);
        } catch (ElasticsearchException e) {
            if (e.status() == 404) {
                // payments.completed/payments.failed and orders.created arrive
                // on different topics with no cross-topic ordering guarantee —
                // Kafka only orders records within a single topic-partition,
                // never across topics. If this event is processed before
                // OrderCreatedEventListener has indexed the order it refers
                // to, there's nothing yet to update. Logging and returning
                // (rather than upserting a partial document containing only
                // orderId + status) mirrors how inventory-service's own
                // decrementStock/releaseStock handle a missing referenced
                // row: warn and move on, don't fabricate incomplete data.
                log.warn("No indexed document for orderId={} yet — skipping status update to {}", orderId, status);
                return;
            }
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to update status for order " + orderId, e);
        }
    }

    public OrderSearchResponse search(UUID orderId, UUID customerId, String status, String queryText, int page, int size) {
        boolean hasOrderIdFilter = orderId != null;
        boolean hasCustomerFilter = customerId != null;
        boolean hasStatusFilter = status != null && !status.isBlank();
        boolean hasFreeText = queryText != null && !queryText.isBlank();

        try {
            SearchResponse<OrderDocument> response = client.search(s -> {
                s.index(OrderDocument.INDEX_NAME).from(page * size).size(size);
                if (hasOrderIdFilter || hasCustomerFilter || hasStatusFilter || hasFreeText) {
                    s.query(q -> q.bool(b -> {
                        // orderId/customerId/status are now mapped as plain
                        // Elasticsearch "keyword" fields (see
                        // ElasticsearchConfig.ensureOrdersIndexExists), not
                        // dynamically-mapped text-with-a-".keyword"-sub-field —
                        // so the term query targets the bare field name
                        // directly. Filtering by an exact value belongs in
                        // bool.filter, not bool.must: filter clauses don't
                        // affect relevance scoring and are cacheable by
                        // Elasticsearch, which is exactly right for an
                        // exact-match lookup that isn't "searching" for
                        // anything.
                        if (hasOrderIdFilter) {
                            b.filter(f -> f.term(t -> t.field("orderId").value(orderId.toString())));
                        }
                        if (hasCustomerFilter) {
                            b.filter(f -> f.term(t -> t.field("customerId").value(customerId.toString())));
                        }
                        if (hasStatusFilter) {
                            b.filter(f -> f.term(t -> t.field("status").value(status)));
                        }
                        if (hasFreeText) {
                            // The only field left in this multiMatch: currency
                            // is the one string field still on dynamic mapping
                            // (still gets an analyzed "text" side automatically),
                            // now that status moved to its own exact-match
                            // filter above instead of being free-text target.
                            b.must(m -> m.multiMatch(mm -> mm.query(queryText).fields("currency")));
                        }
                        return b;
                    }));
                }
                return s;
            }, OrderDocument.class);

            List<OrderDocument> results = response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();
            long totalHits = response.hits().total() != null
                    ? response.hits().total().value()
                    : results.size();

            return new OrderSearchResponse(results, totalHits, page, size);
        } catch (IOException e) {
            throw new UncheckedIOException("Search failed", e);
        }
    }

    // Partial-update payload for updateStatus — deliberately its own tiny
    // type rather than reusing OrderDocument with null fields: Elasticsearch's
    // partial _update only touches the fields present in the submitted JSON,
    // so serializing a full OrderDocument with nulls elsewhere would risk
    // Jackson including those nulls explicitly and clobbering fields this
    // call was never meant to touch.
    private record StatusUpdate(String status) {
    }
}
