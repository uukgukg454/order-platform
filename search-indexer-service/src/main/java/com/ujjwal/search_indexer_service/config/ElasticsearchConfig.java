package com.ujjwal.search_indexer_service.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ujjwal.search_indexer_service.document.OrderDocument;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the low-level ElasticsearchClient by hand rather than pulling in
 * spring-boot-starter-data-elasticsearch's auto-configuration.
 *
 * Checked, not assumed: Spring Boot 4.1.0's own BOM pins
 * co.elastic.clients:elasticsearch-java (the client Spring Data
 * Elasticsearch 6.1.0 — what the starter would bring in — is built against)
 * to 9.4.2, and Spring Data Elasticsearch's own compatibility matrix
 * confirms 6.1.x targets Elasticsearch server 9.4.2. That's a major version
 * ahead of the 8.15 Elasticsearch server this project actually runs
 * locally. Elastic's clients only guarantee compatibility with a server on
 * the SAME major version, so taking the starter as-is would mean a 9.x
 * client talking to an 8.15 server — unsupported, not just untested. This
 * bean instead depends directly on co.elastic.clients:elasticsearch-java,
 * pinned explicitly in build.gradle to 8.15.5 (matching this project's
 * actual server) rather than Spring Boot's BOM-managed 9.4.2, and is wired
 * up by hand the same way RedissonConfig hand-builds a RedissonClient
 * elsewhere in this project for an analogous BOM/reality mismatch.
 */
@Configuration
public class ElasticsearchConfig {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfig.class);

    @Bean
    public ElasticsearchClient elasticsearchClient(@Value("${app.elasticsearch.host}") String host,
                                                     @Value("${app.elasticsearch.port}") int port) {
        RestClient restClient = RestClient.builder(new HttpHost(host, port, "http")).build();

        // Same JavaTimeModule + WRITE_DATES_AS_TIMESTAMPS-disabled setup
        // order-service's RedisConfig uses for its own ObjectMapper —
        // OrderDocument.createdAt is a java.time.Instant, and this keeps it
        // serialized as a readable ISO-8601 string rather than a numeric
        // epoch array, which also happens to be the format Elasticsearch's
        // dynamic date-detection recognizes automatically for the fields
        // this service still leaves to dynamic mapping (see
        // ensureOrdersIndexExists below for the fields that aren't).
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(mapper));
        return new ElasticsearchClient(transport);
    }

    /**
     * Creates the "orders" index with an explicit mapping on startup, if it
     * doesn't already exist yet.
     *
     * Fixes a real bug from relying entirely on Elasticsearch's dynamic
     * mapping: a plain string field like orderId or customerId gets
     * auto-mapped with an analyzed "text" type (tokenized — a UUID gets
     * split on its hyphens), not "keyword" (exact match, unanalyzed) —
     * wrong for fields this service only ever needs to filter on exactly,
     * never search fuzzily. orderId, customerId, status, and items.productId
     * are pinned to "keyword" explicitly here; totalAmount/currency/
     * createdAt are left to dynamic inference, since nothing in this
     * service queries them and Elasticsearch's defaults (float, text+
     * keyword, date) are fine for fields that are only ever read back
     * whole, never filtered on.
     *
     * items stays "object" (Elasticsearch's default for a JSON array of
     * objects), not "nested": nested would be the more correct type for
     * querying INTO individual items without cross-item field bleed across
     * a multi-item order, but nothing in SearchController queries items at
     * all today, so that correctness doesn't buy anything yet — worth
     * revisiting if item-level search is ever added.
     *
     * Deliberately does nothing if the index already exists, rather than
     * trying to update its mapping — Elasticsearch field mappings are
     * immutable once set (you can add new fields, but never change an
     * existing field's type), so fixing a wrong mapping always means
     * delete-and-recreate, not an in-place update. That's an operational
     * step (see the conversation this shipped in), not something this
     * runner attempts automatically.
     */
    @Bean
    public ApplicationRunner ensureOrdersIndexExists(ElasticsearchClient client) {
        return args -> {
            boolean exists = client.indices().exists(e -> e.index(OrderDocument.INDEX_NAME)).value();
            if (exists) {
                log.info("Elasticsearch index '{}' already exists — leaving its mapping as-is.", OrderDocument.INDEX_NAME);
                return;
            }

            client.indices().create(c -> c
                    .index(OrderDocument.INDEX_NAME)
                    .mappings(m -> m
                            .properties("orderId", p -> p.keyword(k -> k))
                            .properties("customerId", p -> p.keyword(k -> k))
                            .properties("status", p -> p.keyword(k -> k))
                            .properties("items", p -> p.object(o -> o
                                    .properties("productId", ip -> ip.keyword(k -> k))
                                    .properties("quantity", ip -> ip.integer(i -> i))
                            ))
                    ));
            log.info("Created Elasticsearch index '{}' with explicit keyword mappings for orderId/customerId/status/items.productId.",
                    OrderDocument.INDEX_NAME);
        };
    }
}
