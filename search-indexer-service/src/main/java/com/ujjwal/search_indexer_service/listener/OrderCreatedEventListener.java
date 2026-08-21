package com.ujjwal.search_indexer_service.listener;

import com.ujjwal.search_indexer_service.event.OrderCreatedEvent;
import com.ujjwal.search_indexer_service.service.OrderSearchService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedEventListener {

    private final OrderSearchService orderSearchService;

    public OrderCreatedEventListener(OrderSearchService orderSearchService) {
        this.orderSearchService = orderSearchService;
    }

    // No groupId attribute — spring.kafka.consumer.group-id in
    // application.yml already sets it for every listener in this service.
    // No idempotency-key check either, unlike every other consumer in this
    // project — see the comment on OrderSearchService.indexNewOrder for why
    // this consumer is a deliberate exception to that pattern.
    @KafkaListener(topics = "${app.kafka.topics.orders-created}")
    public void handle(OrderCreatedEvent event) {
        orderSearchService.indexNewOrder(event);
    }
}
