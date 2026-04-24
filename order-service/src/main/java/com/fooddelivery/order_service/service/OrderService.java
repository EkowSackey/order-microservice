package com.fooddelivery.order_service.service;

import com.fooddelivery.order_service.client.*;
import com.fooddelivery.order_service.dto.*;
import com.fooddelivery.order_service.exception.*;
import com.fooddelivery.order_service.model.*;
import com.fooddelivery.order_service.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final RestaurantClient restaurantClient;

    public OrderService(OrderRepository orderRepository,
                        CustomerClient customerClient,
                        RestaurantClient restaurantClient) {
        this.orderRepository = orderRepository;
        this.customerClient = customerClient;
        this.restaurantClient = restaurantClient;
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
                .restaurantId(request.getRestaurantId())
                .deliveryAddress(request.getDeliveryAddress() != null
                        ? request.getDeliveryAddress()
                        : customer.getDeliveryAddress())
                .specialInstructions(request.getSpecialInstructions())
                .estimatedDeliveryTime(
                        LocalDateTime.now().plusMinutes(restaurant.getEstimatedDeliveryMinutes()))
                // Setting initial default values that can be handled later via async
                .deliveryId(0L) // Set default, actual id will be set by delivery event later
                .items(new ArrayList<>()) // MUST INITIALIZE LIST TO AVOID NULL POINTERS
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

        // ASYNC DELIVERY:
        // Instead of calling deliveryService directly, we will later publish an OrderPlacedEvent here
        // eventPublisher.publishEvent(new OrderPlacedEvent(savedOrder.getId()));

        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return OrderResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getCustomerOrders(String username) {
        // Fetch customerId from Customer Service via Feign
        CustomerDTO customer = customerClient.getCustomerByUsername(username);
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream().map(OrderResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getRestaurantOrders(Long restaurantId) {
        return orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
                .stream().map(OrderResponse::fromEntity).toList();
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        Order.OrderStatus newStatus = Order.OrderStatus.valueOf(status.toUpperCase());
        order.setStatus(newStatus);

        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, String username) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Ensure this user owns the order
        CustomerDTO customer = customerClient.getCustomerById(order.getCustomerId());
        if (!customer.getUsername().equals(username)) {
            throw new UnauthorizedException("You can only cancel your own orders");
        }

        if (order.getStatus() != Order.OrderStatus.PLACED
                && order.getStatus() != Order.OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel order in status: " + order.getStatus());
        }

        order.setStatus(Order.OrderStatus.CANCELLED);

        // ASYNC DELIVERY CANCEL:
        // Will publish OrderCancelledEvent here later instead of synchronous call
        // if (order.getDeliveryId() != null) {
        //    eventPublisher.publishEvent(new OrderCancelledEvent(order.getId()));
        // }

        return OrderResponse.fromEntity(orderRepository.save(order));
    }
}
