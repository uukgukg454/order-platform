package com.ujjwal.inventory_service.listener;

import com.ujjwal.inventory_service.entity.Stock;
import com.ujjwal.inventory_service.event.OrderCreatedEvent;
import com.ujjwal.inventory_service.idempotency.IdempotencyService;
import com.ujjwal.inventory_service.lock.DistributedLockService;
import com.ujjwal.inventory_service.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderCreatedEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedEventListener.class);

    private static final Duration LOCK_WAIT_TIME = Duration.ofSeconds(5);
    private static final Duration LOCK_LEASE_TIME = Duration.ofSeconds(5);

    // Deliberate DLQ test hook, not a real business rule: an order for this
    // exact sentinel customerId always fails, on every single delivery
    // attempt — a "poison pill" that simulates a permanently-broken
    // message (a bug in the producer, a payload no code path can ever
    // handle) rather than a transient one a retry could recover from. Used
    // to exercise kafkaErrorHandler's retry-then-DLT path end to end
    // without needing to actually break something for real.
    private static final UUID POISON_PILL_CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final IdempotencyService idempotencyService;
    private final DistributedLockService lockService;
    private final StockRepository stockRepository;

    public OrderCreatedEventListener(IdempotencyService idempotencyService,
                                      DistributedLockService lockService,
                                      StockRepository stockRepository) {
        this.idempotencyService = idempotencyService;
        this.lockService = lockService;
        this.stockRepository = stockRepository;
    }

    // No groupId attribute here — spring.kafka.consumer.group-id in
    // application.yml already sets it for every listener in this service,
    // so repeating it per-listener would just be a second place for it to
    // drift out of sync.
    @KafkaListener(topics = "${app.kafka.topics.orders-created}")
    public void handle(OrderCreatedEvent event) {
        // Checked before the idempotency check on purpose: if this ran
        // after it, only the FIRST delivery attempt would ever reach this
        // throw — tryMarkProcessed would already be true by the time
        // DefaultErrorHandler's retries redeliver the same record, so
        // every retry after the first would silently skip out via the
        // "duplicate delivery" branch below instead of failing again. The
        // whole point of a poison-pill test is that it fails identically
        // on every attempt.
        if (POISON_PILL_CUSTOMER_ID.equals(event.customerId())) {
            throw new RuntimeException("Simulated poison-pill failure for DLQ testing");
        }

        // Before doing anything else: Kafka only guarantees at-least-once
        // delivery, so this same event can legitimately arrive more than
        // once (consumer group rebalance, a redelivered-but-already-
        // processed offset, etc.). Prefixed with "inventory-service:" so
        // this service's idempotency keys can't collide with
        // payment-service's own "payment-service:<orderId>" keys in the
        // same shared Redis instance.
        if (!idempotencyService.tryMarkProcessed("inventory-service:" + event.orderId())) {
            log.info("Duplicate delivery of OrderCreatedEvent for orderId={} — skipping", event.orderId());
            return;
        }

        for (OrderCreatedEvent.Item item : event.items()) {
            // Locked per productId, not per order: two different orders
            // touching different products should decrement in parallel,
            // but two orders (or two redelivered attempts) touching the
            // SAME product must serialize their read-decrement-write so
            // neither one reads a quantity the other has already
            // consumed.
            lockService.executeWithLock("stock:" + item.productId(), LOCK_WAIT_TIME, LOCK_LEASE_TIME, () -> {
                decrementStock(event, item);
                return null;
            });
        }
    }

    private void decrementStock(OrderCreatedEvent event, OrderCreatedEvent.Item item) {
        Optional<Stock> stock = stockRepository.findById(item.productId());
        if (stock.isEmpty()) {
            log.warn("No stock row for productId={} (orderId={}) — nothing to decrement",
                    item.productId(), event.orderId());
            return;
        }

        Stock row = stock.get();
        if (row.getQuantity() >= item.quantity()) {
            row.setQuantity(row.getQuantity() - item.quantity());
            stockRepository.save(row);
            log.info("Decremented stock for productId={} by {} (orderId={})",
                    item.productId(), item.quantity(), event.orderId());
        } else {
            // No event published for this yet — that's a later step (e.g.
            // an OrderStockRejected event payment-service or order-service
            // would need to react to). For now this is only observable in
            // logs.
            log.warn("Insufficient stock for productId={}: have {}, need {} (orderId={})",
                    item.productId(), row.getQuantity(), item.quantity(), event.orderId());
        }
    }
}
