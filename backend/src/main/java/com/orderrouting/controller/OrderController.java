package com.orderrouting.controller;

import com.orderrouting.dto.OrderDtos.OrderDetail;
import com.orderrouting.dto.OrderDtos.OrderSummary;
import com.orderrouting.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public Page<OrderSummary> search(@RequestParam(required = false) String query,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return orderService.search(query, page, Math.min(size, 100));
    }

    @GetMapping("/{id}")
    public OrderDetail get(@PathVariable Long id) {
        return orderService.get(id);
    }
}
