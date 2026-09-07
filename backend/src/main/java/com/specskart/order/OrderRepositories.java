package com.specskart.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByToken(String token);

    List<Cart> findByOrderedAtIsNullAndNudgedAtIsNullAndLeadIdIsNotNullAndUpdatedAtBetween(Instant from, Instant to);
}

interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    List<CartItem> findByCartId(UUID cartId);

    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

    void deleteByCartId(UUID cartId);
}

interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByOrderNo(String orderNo);

    Optional<Order> findByPaymentRef(String paymentRef);

    boolean existsByOrderNo(String orderNo);

    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    List<Order> findByLeadIdOrderByCreatedAtDesc(UUID leadId);

    List<Order> findAllByOrderByCreatedAtDesc();
}

interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderId(UUID orderId);
}

interface OrderEventRepository extends JpaRepository<OrderEvent, UUID> {
    List<OrderEvent> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
