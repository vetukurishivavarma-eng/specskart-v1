package com.specskart.order;

import com.specskart.shared.ApiException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderQueryService query;
    private final CheckoutService checkout;

    public AdminOrderController(OrderQueryService query, CheckoutService checkout) {
        this.query = query;
        this.checkout = checkout;
    }

    @GetMapping
    public List<OrderDtos.AdminOrderRow> list(@RequestParam(required = false) String status) {
        return query.adminList(status);
    }

    @GetMapping("/{id}")
    public OrderDtos.OrderView detail(@PathVariable UUID id) {
        return query.byId(id);
    }

    @PatchMapping("/{id}/status")
    public OrderDtos.OrderView updateStatus(@PathVariable UUID id, @RequestBody OrderDtos.StatusUpdate body) {
        OrderStatus target;
        try {
            target = OrderStatus.valueOf(body.status());
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_STATUS", "Unknown order status: " + body.status());
        }
        checkout.updateStatus(id, target, body.note());
        return query.byId(id);
    }
}
