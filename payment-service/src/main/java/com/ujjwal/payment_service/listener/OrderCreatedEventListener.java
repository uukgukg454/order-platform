package com.ujjwal.payment_service.listener;

import com.ujjwal.payment_service.entity.Payment;
import com.ujjwal.payment_service.entity.PaymentStatus;
import com.ujjwal.payment_service.event.OrderCreatedEvent;
import com.ujjwal.payment_service.event.PaymentCompletedEvent;
import com.ujjwal.payment_service.event.PaymentFailedEvent;
import com.ujjwal.payment_service.idempotency.IdempotencyService;
import com.ujjwal.payment_service.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class OrderCreatedEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedEventListener.class);

    // Deterministic stand-in for a real payment gateway: anything over this
    // is declined, everything else approved. No actual charge logic,
    // gateway integration, or retries yet — just enough to drive the
    // COMPLETED/FAILED branches of the saga.
    private static final BigDecimal DECLINE_THRESHOLD = new BigDecimal("500");

    private final IdempotencyService idempotencyService;
    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String paymentsCompletedTopic;
    private final String paymentsFailedTopic;

    public OrderCreatedEventListener(IdempotencyService idempotencyService,
                                      PaymentRepository paymentRepository,
                                      KafkaTemplate<String, Object> kafkaTemplate,
                                      @Value("${app.kafka.topics.payments-completed}") String paymentsCompletedTopic,
                                      @Value("${app.kafka.topics.payments-failed}") String paymentsFailedTopic) {
        this.idempotencyService = idempotencyService;
        this.paymentRepository = paymentRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.paymentsCompletedTopic = paymentsCompletedTopic;
        this.paymentsFailedTopic = paymentsFailedTopic;
    }

    // No groupId attribute here — spring.kafka.consumer.group-id in
    // application.yml already sets it for every listener in this service,
    // so repeating it per-listener would just be a second place for it to
    // drift out of sync.
    @KafkaListener(topics = "${app.kafka.topics.orders-created}")
    public void handle(OrderCreatedEvent event) {
        // Before doing anything else: Kafka only guarantees at-least-once
        // delivery, so this same event can legitimately arrive more than
        // once. Prefixed with "payment-service:" so this service's
        // idempotency keys can't collide with inventory-service's own
        // "inventory-service:<orderId>" keys in the same shared Redis
        // instance. No distributed lock needed here, unlike
        // inventory-service's stock decrement — creating a new Payment row
        // isn't a shared-mutable-resource read-modify-write, so there's
        // nothing for a lock to protect.
        if (!idempotencyService.tryMarkProcessed("payment-service:" + event.orderId())) {
            log.info("Duplicate delivery of OrderCreatedEvent for orderId={} — skipping", event.orderId());
            return;
        }

        boolean approved = event.totalAmount().compareTo(DECLINE_THRESHOLD) <= 0;

        Payment payment = new Payment(event.orderId(), event.totalAmount(), event.currency());
        payment.setStatus(approved ? PaymentStatus.COMPLETED : PaymentStatus.FAILED);
        Payment saved = paymentRepository.save(payment);

        if (approved) {
            publishPaymentCompleted(saved);
        } else {
            publishPaymentFailed(event);
        }
    }

    private void publishPaymentCompleted(Payment payment) {
        PaymentCompletedEvent completedEvent = new PaymentCompletedEvent(
                payment.getOrderId(), payment.getId(), payment.getAmount(), payment.getCurrency());
        // Keyed by orderId, not paymentId — same partitioning rationale as
        // order-service's producer: per-order processing doesn't need
        // cross-order ordering, so keying by the id every consumer will
        // actually filter/join on is both finer-grained and sufficient,
        // without concentrating one busy actor's traffic on one partition.
        kafkaTemplate.send(paymentsCompletedTopic, payment.getOrderId().toString(), completedEvent);
        log.info("Payment completed for orderId={} amount={} {}",
                payment.getOrderId(), payment.getAmount(), payment.getCurrency());
    }

    private void publishPaymentFailed(OrderCreatedEvent event) {
        List<PaymentFailedEvent.Item> items = event.items().stream()
                .map(item -> new PaymentFailedEvent.Item(item.productId(), item.quantity()))
                .toList();
        PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                event.orderId(), items, "Payment declined: amount exceeds " + DECLINE_THRESHOLD);
        // Same orderId-keyed partitioning as the completed path above.
        kafkaTemplate.send(paymentsFailedTopic, event.orderId().toString(), failedEvent);
        log.warn("Payment declined for orderId={} amount={} — publishing compensation event",
                event.orderId(), event.totalAmount());
    }
}
