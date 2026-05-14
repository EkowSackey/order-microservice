package com.fooddelivery.order_service.service;

import com.fooddelivery.order_service.client.*;
import com.fooddelivery.order_service.dto.*;
import com.fooddelivery.order_service.exception.*;
import com.fooddelivery.order_service.model.*;
import com.fooddelivery.order_service.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final RestaurantClient restaurantClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    public OrderService(OrderRepository orderRepository,
                        CustomerClient customerClient,
                        RestaurantClient restaurantClient,
                        ApplicationEventPublisher applicationEventPublisher) {
        this.orderRepository = orderRepository;
        this.customerClient = customerClient;
        this.restaurantClient = restaurantClient;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public OrderResponse placeOrder(String customerUsername, PlaceOrderRequest request) {
        
        // Fetch Customer details from Customer Service via Feign
        CustomerDTO customer = customerClient.getCustomerByUsername(customerUsername);
        
        // Fetch Restaurant details from Restaurant Service via Feign
        RestaurantDTO restaurant = restaurantClient.getRestaurantById(request.getRestaurantId());

        if (!restaurant.isActive()) {
            throw new IllegalStateException("Restaurant is currently not accepting orders");
        }

        // Build order
        Order order = Order.builder()
                .customerId(customer.getId())
                .customerUsername(customerUsername)
                .restaurantId(request.getRestaurantId())
                .deliveryAddress(request.getDeliveryAddress() != null
                        ? request.getDeliveryAddress()
                        : customer.getDeliveryAddress())
                .specialInstructions(request.getSpecialInstructions())
                .estimatedDeliveryTime(
                        LocalDateTime.now().plusMinutes(restaurant.getEstimatedDeliveryMinutes()))
                .items(new ArrayList<>())
                .build();

        // Fetch MenuItem details from Restaurant Service via Feign
        BigDecimal total = BigDecimal.ZERO;
        if (request.getItems() != null) {
            for (OrderItemRequest itemReq : request.getItems()) {
                MenuItemDTO menuItem = restaurantClient.getMenuItemById(itemReq.getMenuItemId());

                if (!menuItem.isAvailable()) {
                    throw new IllegalStateException("Menu item '" + menuItem.getName() + "' is not available");
                }
                if (!menuItem.getRestaurantId().equals(request.getRestaurantId())) {
                    throw new IllegalStateException("Menu item '" + menuItem.getName()
                            + "' does not belong to restaurant '" + restaurant.getName() + "'");
                }

                BigDecimal subtotal = menuItem.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));

                OrderItem orderItem = OrderItem.builder()
                        .order(order)
                        .menuItemId(itemReq.getMenuItemId())
                        .menuItemName(menuItem.getName())
                        .quantity(itemReq.getQuantity())
                        .unitPrice(menuItem.getPrice())
                        .subtotal(subtotal)
                        .specialInstructions(itemReq.getSpecialInstructions())
                        .build();

                order.getItems().add(orderItem);
                total = total.add(subtotal);
            }
        }

        order.setTotalAmount(total);
        Order savedOrder = orderRepository.save(order);

        // ASYNC DELIVERY: Publish OrderPlacedEvent
        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .orderId(savedOrder.getId())
                .customerId(savedOrder.getCustomerId())
                .restaurantId(savedOrder.getRestaurantId())
                .pickupAddress(restaurant.getAddress())
                .deliveryAddress(savedOrder.getDeliveryAddress())
                .restaurantName(restaurant.getName())
                .customerFirstName(customer.getFirstName())
                .customerLastName(customer.getLastName())
                .build();
        
        // Publishes to Spring's event bus; OrderEventPublisher.onOrderPlaced fires
        // via @TransactionalEventListener(AFTER_COMMIT) — RabbitMQ send only happens
        // after the DB transaction has fully committed.
        applicationEventPublisher.publishEvent(event);

        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional
    public void updateOrderFromDelivery(DeliveryStatusEvent event) {
        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", event.getOrderId()));
        
        // Link the delivery ID if not already linked
        order.setDeliveryId(event.getDeliveryId());

        // Map delivery status to order status
        switch (event.getStatus()) {
            case "ASSIGNED"   -> order.setStatus(Order.OrderStatus.CONFIRMED);
            case "PICKED_UP"  -> order.setStatus(Order.OrderStatus.OUT_FOR_DELIVERY);
            case "IN_TRANSIT" -> order.setStatus(Order.OrderStatus.OUT_FOR_DELIVERY);
            case "DELIVERED"  -> order.setStatus(Order.OrderStatus.DELIVERED);
            case "FAILED"     -> order.setStatus(Order.OrderStatus.CANCELLED);
            default -> log.warn("Unhandled delivery status '{}' for order #{} — no order status update applied",
                    event.getStatus(), event.getOrderId());
        }

        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, String username) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getCustomerUsername().equals(username)) {
            return OrderResponse.fromEntity(order);
        }

        CustomerDTO requestor = customerClient.getCustomerByUsername(username);
        RestaurantDTO restaurant = restaurantClient.getRestaurantById(order.getRestaurantId());
        if (restaurant.getOwnerId().equals(requestor.getId())) {
            return OrderResponse.fromEntity(order);
        }

        throw new UnauthorizedException("You do not have access to this order");
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getCustomerOrders(String username) {
        // Fetch customerId from Customer Service via Feign
        CustomerDTO customer = customerClient.getCustomerByUsername(username);
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream().map(OrderResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getRestaurantOrders(String ownerUsername, Long restaurantId) {
        CustomerDTO owner = customerClient.getCustomerByUsername(ownerUsername);
        RestaurantDTO restaurant = restaurantClient.getRestaurantById(restaurantId);
        if (!restaurant.getOwnerId().equals(owner.getId())) {
            throw new UnauthorizedException("You do not own this restaurant");
        }
        return orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
                .stream().map(OrderResponse::fromEntity).toList();
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String ownerUsername, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Only PREPARING and READY_FOR_PICKUP are restaurant-controllable.
        // Delivery-driven transitions (CONFIRMED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED)
        // are handled exclusively via updateOrderFromDelivery (RabbitMQ events).
        Order.OrderStatus newStatus;
        try {
            newStatus = Order.OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid order status: " + status);
        }
        if (newStatus != Order.OrderStatus.PREPARING && newStatus != Order.OrderStatus.READY_FOR_PICKUP) {
            throw new IllegalStateException(
                    "Restaurant owners can only set status to PREPARING or READY_FOR_PICKUP");
        }

        // Verify the authenticated user owns the restaurant for this order
        CustomerDTO owner = customerClient.getCustomerByUsername(ownerUsername);
        RestaurantDTO restaurant = restaurantClient.getRestaurantById(order.getRestaurantId());
        if (!restaurant.getOwnerId().equals(owner.getId())) {
            throw new UnauthorizedException("You do not own the restaurant for this order");
        }

        order.setStatus(newStatus);
        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, String username) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.getCustomerUsername().equals(username)) {
            throw new UnauthorizedException("You can only cancel your own orders");
        }

        if (order.getStatus() != Order.OrderStatus.PLACED
                && order.getStatus() != Order.OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel order in status: " + order.getStatus());
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        OrderResponse response = OrderResponse.fromEntity(orderRepository.save(order));

        applicationEventPublisher.publishEvent(new OrderCancelledEvent(orderId));
        return response;
    }
}
