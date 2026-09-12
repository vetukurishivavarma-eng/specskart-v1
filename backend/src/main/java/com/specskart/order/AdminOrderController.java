package com.specskart.order;

import com.specskart.shared.ApiException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderQueryService query;
    private final CheckoutService checkout;
    private final OrderRepository orders;
    private final PrescriptionFileRepository prescriptionFiles;

    public AdminOrderController(OrderQueryService query, CheckoutService checkout,
                                OrderRepository orders, PrescriptionFileRepository prescriptionFiles) {
        this.query = query;
        this.checkout = checkout;
        this.orders = orders;
        this.prescriptionFiles = prescriptionFiles;
    }

    /** The customer's uploaded prescription photo/PDF for this order. Auth-gated — this is
     *  personal medical-ish data, never served on the public API. */
    @GetMapping("/{id}/prescription")
    public ResponseEntity<byte[]> prescription(@PathVariable UUID id) {
        Order o = orders.findById(id).orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "No such order."));
        if (o.getPrescriptionFileId() == null) {
            throw ApiException.notFound("NO_PRESCRIPTION", "No prescription was uploaded for this order.");
        }
        PrescriptionFile f = prescriptionFiles.findById(o.getPrescriptionFileId())
                .orElseThrow(() -> ApiException.notFound("NO_PRESCRIPTION", "That file is no longer available."));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.getContentType())).body(f.getBytes());
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

    /** Confirm cash was collected for a pay-on-delivery order (settles loyalty / conversion). */
    @PostMapping("/{id}/cash-received")
    public OrderDtos.OrderView cashReceived(@PathVariable UUID id) {
        checkout.markCashReceived(id);
        return query.byId(id);
    }
}
