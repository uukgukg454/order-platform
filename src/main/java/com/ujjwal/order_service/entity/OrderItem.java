package com.ujjwal.order_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single line item within an {@link Order}.
 *
 * productId is a plain UUID field rather than a @ManyToOne to a Product
 * entity because no Product entity exists in this service — product catalog
 * data lives elsewhere, and order-service only needs the id.
 *
 * productName and unitPrice are snapshots of the product's state at the
 * moment the order was placed, not a live lookup: if the catalog service
 * later renames the product or changes its price, this order's history must
 * still reflect what the customer actually saw and paid.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    // Snapshot at order time — see class-level javadoc.
    @Column(name = "product_name", nullable = false)
    private String productName;

    // Snapshot at order time, and BigDecimal (not double/float) for the same
    // reason as Order.totalAmount: money must not lose precision to
    // binary floating-point rounding.
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    protected OrderItem() {
        // required by JPA
    }

    public OrderItem(UUID productId, String productName, BigDecimal unitPrice, Integer quantity) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    void setOrder(Order order) {
        this.order = order;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }
}
