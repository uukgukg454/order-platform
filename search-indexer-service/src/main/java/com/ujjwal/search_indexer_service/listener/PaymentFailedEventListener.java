package com.ujjwal.search_indexer_service.listener;

import com.ujjwal.search_indexer_service.event.PaymentFailedEvent;
import com.ujjwal.search_indexer_service.service.OrderSearchService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentFailedEventListener {

    private final OrderSearchService orderSearchService;

    public PaymentFailedEventListener(OrderSearchService orderSearchService) {
        this.orderSearchService = orderSearchService;
    }

    // No idempotency-key check here either — see
    // OrderSearchService.indexNewOrder's comment; a repeated status update
    // by orderId is just as naturally idempotent as the original index().
    @KafkaListener(topics = "${app.kafka.topics.payments-failed}", containerFactory = "paymentsFailedKafkaListenerContainerFactory")
    public void handle(PaymentFailedEvent event) {
        orderSearchService.updateStatus(event.orderId(), "PAYMENT_FAILED");
    }
}
