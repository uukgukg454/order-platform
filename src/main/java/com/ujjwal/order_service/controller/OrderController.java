package com.ujjwal.order_service.controller;

import com.ujjwal.order_service.dto.request.CreateOrderRequest;
import com.ujjwal.order_service.dto.request.UpdateOrderStatusRequest;
import com.ujjwal.order_service.dto.response.OrderResponse;
import com.ujjwal.order_service.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    // Hard ceiling on page size so a client can't pass e.g. size=1000000 and
    // force the service to load an unbounded number of rows in one query.
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request,
                                                       UriComponentsBuilder uriBuilder) {
        OrderResponse response = orderService.createOrder(request);
        // 201 Created + Location header: the standard shape for "a new
        // resource now exists at this URI" — lets the client GET it back
        // without having to construct the path itself.
        URI location = uriBuilder.path("/orders/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        // 200: plain successful retrieval. The not-found case never reaches
        // this method body — OrderService throws OrderNotFoundException,
        // which GlobalExceptionHandler translates into a 404 response.
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> listOrders(@RequestParam UUID customerId,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, cappedSize);
        return ResponseEntity.ok(orderService.listOrdersByCustomer(customerId, pageable));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(@PathVariable UUID id,
                                                             @Valid @RequestBody UpdateOrderStatusRequest request) {
        // Not-found and validation failures both fall through to
        // GlobalExceptionHandler exactly as they do for the other endpoints
        // above — nothing endpoint-specific needed here.
        return ResponseEntity.ok(orderService.updateOrderStatus(id, request.status()));
    }
}
