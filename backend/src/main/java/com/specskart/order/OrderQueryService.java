package com.specskart.order;

import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderQueryService {

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderEventRepository events;
    private final LeadRepository leads;

    public OrderQueryService(OrderRepository orders, OrderItemRepository items, OrderEventRepository events,
                             LeadRepository leads) {
        this.orders = orders;
        this.items = items;
        this.events = events;
        this.leads = leads;
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderView byOrderNo(String orderNo) {
        return toView(orders.findByOrderNo(orderNo)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "We couldn't find that order.")));
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderView byId(UUID id) {
        return toView(orders.findById(id)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "No such order.")));
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.AdminOrderRow> adminList(String status) {
        List<Order> list = (status == null || status.isBlank())
                ? orders.findAllByOrderByCreatedAtDesc()
                : orders.findByStatusOrderByCreatedAtDesc(OrderStatus.valueOf(status));
        return list.stream().map(o -> new OrderDtos.AdminOrderRow(
                o.getId(), o.getOrderNo(), o.getStatus().name(), o.getCustomerName(),
                o.getTotalMinor(), o.getCurrency(), o.getCreatedAt(), o.getPaidAt())).toList();
    }

    private OrderDtos.OrderView toView(Order o) {
        List<OrderDtos.OrderLine> lines = items.findByOrderId(o.getId()).stream()
                .map(i -> new OrderDtos.OrderLine(i.getProductName(), i.getProductSlug(), i.getQty(),
                        i.getUnitPriceMinor(), i.lineTotalMinor())).toList();
        List<OrderDtos.StatusEvent> timeline = events.findByOrderIdOrderByCreatedAtAsc(o.getId()).stream()
                .map(e -> new OrderDtos.StatusEvent(e.getStatus().name(), e.getNote(), e.getCreatedAt())).toList();
        Lead lead = o.getLeadId() == null ? null : leads.findById(o.getLeadId()).orElse(null);
        int balance = lead == null ? 0 : lead.getPoints();
        String referralCode = lead == null ? null : lead.getReferralCode();
        return new OrderDtos.OrderView(o.getOrderNo(), o.getStatus().name(), o.getCustomerName(), o.getCustomerPhone(),
                o.getCustomerEmail(), o.getShipAddress(), o.getShipCity(),
                o.getSubtotalMinor(), o.getDiscountMinor(), o.getShippingMinor(), o.getTotalMinor(),
                o.getCurrency(), o.getPromoCode(), o.getPaidAt(), o.getCreatedAt(), lines, timeline,
                o.getPointsEarned(), o.getPointsRedeemed(), balance, referralCode);
    }
}
