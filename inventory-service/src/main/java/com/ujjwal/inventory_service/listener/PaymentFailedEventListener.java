package com.ujjwal.inventory_service.listener;

import com.ujjwal.inventory_service.entity.Stock;
import com.ujjwal.inventory_service.event.PaymentFailedEvent;
import com.ujjwal.inventory_service.idempotency.IdempotencyService;
import com.ujjwal.inventory_service.lock.DistributedLockService;
import com.ujjwal.inventory_service.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Compensating side of the saga: releases stock this service already
 * decremented for an order whose payment then failed.
 */
@Component
public class PaymentFailedEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentFailedEventListener.class);

    private static final Duration LOCK_WAIT_TIME = Duration.ofSeconds(5);
    private static final Duration LOCK_LEASE_TIME = Duration.ofSeconds(5);

    private final IdempotencyService idempotencyService;
    private final DistributedLockService lockService;
    private final StockRepository stockRepository;

    public PaymentFailedEventListener(IdempotencyService idempotencyService,
                                       DistributedLockService lockService,
                                       StockRepository stockRepository) {
        this.idempotencyService = idempotencyService;
        this.lockService = lockService;
        this.stockRepository = stockRepository;
    }

    @KafkaListener(topics = "${app.kafka.topics.payments-failed}", containerFactory = "paymentFailedKafkaListenerContainerFactory")
    public void handle(PaymentFailedEvent event) {
        // Explicitly NOT "inventory-service:" + orderId — OrderCreatedEvent
        // Listener already marked that key true when it did the original
        // decrement for this order, so reusing it here would make this
        // compensating release look like a duplicate and get skipped
        // before it ever ran. A distinct "compensate:" namespace keeps "did
        // we decrement for this order" and "did we release for this order"
        // as two separate questions instead of one that can only be
        // answered once.
        if (!idempotencyService.tryMarkProcessed("inventory-service:compensate:" + event.orderId())) {
            log.info("Duplicate delivery of PaymentFailedEvent for orderId={} — skipping", event.orderId());
            return;
        }

        for (PaymentFailedEvent.Item item : event.items()) {
            // Same "stock:" + productId lock key as the original decrement
            // in OrderCreatedEventListener — both paths read-modify-write
            // the same Stock row, so a release running concurrently with a
            // decrement for a different order on the same product must
            // serialize against it too, not just against other releases.
            lockService.executeWithLock("stock:" + item.productId(), LOCK_WAIT_TIME, LOCK_LEASE_TIME, () -> {
                releaseStock(event, item);
                return null;
            });
        }
    }

    private void releaseStock(PaymentFailedEvent event, PaymentFailedEvent.Item item) {
        Optional<Stock> stock = stockRepository.findById(item.productId());
        if (stock.isEmpty()) {
            log.warn("No stock row for productId={} (orderId={}) — nothing to release",
                    item.productId(), event.orderId());
            return;
        }

        Stock row = stock.get();
        row.setQuantity(row.getQuantity() + item.quantity());
        stockRepository.save(row);
        log.info("Released {} units of stock for productId={} after failed payment (orderId={})",
                item.quantity(), item.productId(), event.orderId());
    }
}
