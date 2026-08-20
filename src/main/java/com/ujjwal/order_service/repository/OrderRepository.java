package com.ujjwal.order_service.repository;

import com.ujjwal.order_service.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * There is deliberately no OrderItemRepository. OrderItem is not an
 * aggregate root — it has no meaning or lifecycle outside its parent Order
 * (see the cascade/orphanRemoval on Order.items), so it should only ever be
 * loaded and modified through this repository, via the owning Order.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Fetches one Order with its items initialized in the same query.
     *
     * items is LAZY on Order (see Order.items), so a plain findById here
     * would leave items unfetched; accessing it later (e.g. while
     * serializing the response to JSON, outside the transaction) would
     * throw LazyInitializationException. JOIN FETCH pulls both in one
     * round trip instead. LEFT JOIN FETCH (not an inner join) so an order
     * with zero items is still returned rather than silently dropped.
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") UUID id);

    /**
     * Lists an Order summary page by customer, without fetching items.
     *
     * A list view only needs order-level fields (status, total, dates) to
     * render a summary — fetching every item of every order on the page
     * would multiply the row count for no benefit and isn't needed here.
     * items stays LAZY and simply isn't touched by callers of this method.
     */
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);
}
