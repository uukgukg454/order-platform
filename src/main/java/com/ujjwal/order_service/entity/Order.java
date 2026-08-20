package com.ujjwal.order_service.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An order placed by a customer, together with the line items it contains.
 *
 * customerId is a plain UUID field rather than a @ManyToOne to a Customer
 * entity because no Customer entity exists in this service — customer data
 * lives in a separate service, and order-service only needs to reference it
 * by id, not join across it in-process.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    // Stored as VARCHAR, not ordinal — see OrderStatus javadoc.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    // BigDecimal (not double/float) because money must never lose precision
    // to binary floating-point rounding errors.
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Order owns its items: cascade so persisting/removing an Order
    // persists/removes its items too, and orphanRemoval so removing an item
    // from this list (without deleting the Order) deletes that item's row.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // required by JPA
    }

    public Order(UUID customerId, BigDecimal totalAmount, String currency) {
        this.customerId = customerId;
        this.status = OrderStatus.CREATED;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Adds an item and keeps both sides of the bidirectional association in
     * sync. Without setting item.order here, orphanRemoval would have
     * nothing to compare against and the FK would be left null on insert.
     */
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /**
     * Removes an item via the owning side's collection so Hibernate detects
     * it as an orphan and deletes the row — simply discarding the reference
     * elsewhere would leave the row in place.
     */
    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        this.updatedAt = Instant.now();
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
