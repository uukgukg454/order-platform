package com.ujjwal.inventory_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Current stock level for one product.
 *
 * productId is the primary key rather than a separate generated id: this
 * table only ever holds one row per product (the current level, not a
 * history of changes), so there's nothing else meaningful to key it by.
 */
@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Stock() {
        // required by JPA
    }

    public Stock(UUID productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
        this.updatedAt = Instant.now();
    }

    public UUID getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
