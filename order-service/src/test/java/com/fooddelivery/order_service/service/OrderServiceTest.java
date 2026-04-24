package com.fooddelivery.order_service.service;

import com.fooddelivery.order_service.client.*;
import com.fooddelivery.order_service.dto.*;
import com.fooddelivery.order_service.exception.*;
import com.fooddelivery.order_service.model.*;
import com.fooddelivery.order_service.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private RestaurantClient restaurantClient;

    @InjectMocks
    private OrderService orderService;

    private CustomerDTO mockCustomer;
    private RestaurantDTO mockRestaurant;
    private MenuItemDTO mockMenuItem;

    @BeforeEach
    void setUp() {
        mockCustomer = new CustomerDTO();
        mockCustomer.setId(100L);
        mockCustomer.setUsername("johndoe");
        mockCustomer.setFirstName("John");
        mockCustomer.setLastName("Doe");
        mockCustomer.setDeliveryAddress("123 Main St");

        mockRestaurant = new RestaurantDTO();
        mockRestaurant.setId(200L);
        mockRestaurant.setName("Burger Joint");
        mockRestaurant.setActive(true);
        mockRestaurant.setEstimatedDeliveryMinutes(45);

        mockMenuItem = new MenuItemDTO();
        mockMenuItem.setId(300L);
        mockMenuItem.setName("Cheeseburger");
        mockMenuItem.setPrice(new BigDecimal("15.50"));
        mockMenuItem.setAvailable(true);
        mockMenuItem.setRestaurantId(200L);
    }

    @Test
    void testPlaceOrder_Success() {
        // Arrange
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setRestaurantId(200L);
        request.setDeliveryAddress("456 Custom St");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuItemId(300L);
        itemRequest.setQuantity(2);
        request.setItems(List.of(itemRequest));

        when(customerClient.getCustomerByUsername("johndoe")).thenReturn(mockCustomer);
        when(restaurantClient.getRestaurantById(200L)).thenReturn(mockRestaurant);
        when(restaurantClient.getMenuItemById(300L)).thenReturn(mockMenuItem);

        Order savedOrder = new Order();
        savedOrder.setId(1L);
        savedOrder.setCustomerId(100L);
        savedOrder.setRestaurantId(200L);
        savedOrder.setDeliveryAddress("456 Custom St");
        savedOrder.setStatus(Order.OrderStatus.PLACED);
        savedOrder.setTotalAmount(new BigDecimal("31.00"));
        savedOrder.setItems(new ArrayList<>());
        
        OrderItem orderItem = new OrderItem();
        orderItem.setId(10L);
        orderItem.setMenuItemId(300L);
        orderItem.setMenuItemName("Cheeseburger");
        orderItem.setQuantity(2);
        orderItem.setUnitPrice(new BigDecimal("15.50"));
        orderItem.setSubtotal(new BigDecimal("31.00"));
        savedOrder.getItems().add(orderItem);

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderResponse response = orderService.placeOrder("johndoe", request);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("PLACED", response.getStatus());
        assertEquals(new BigDecimal("31.00"), response.getTotalAmount());
        assertEquals("456 Custom St", response.getDeliveryAddress());
        assertEquals(1, response.getItems().size());
        assertEquals("Cheeseburger", response.getItems().get(0).getItemName());

        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testPlaceOrder_RestaurantNotActive() {
        // Arrange
        mockRestaurant.setActive(false);
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setRestaurantId(200L);
        request.setItems(new ArrayList<>()); // Fix null pointer issue in iterator

        when(customerClient.getCustomerByUsername("johndoe")).thenReturn(mockCustomer);
        when(restaurantClient.getRestaurantById(200L)).thenReturn(mockRestaurant);

        // Act & Assert
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            orderService.placeOrder("johndoe", request);
        });

        assertEquals("Restaurant is currently not accepting orders", exception.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void testPlaceOrder_MenuItemNotAvailable() {
        // Arrange
        mockMenuItem.setAvailable(false);

        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setRestaurantId(200L);
        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuItemId(300L);
        itemRequest.setQuantity(1);
        request.setItems(List.of(itemRequest));

        when(customerClient.getCustomerByUsername("johndoe")).thenReturn(mockCustomer);
        when(restaurantClient.getRestaurantById(200L)).thenReturn(mockRestaurant);
        when(restaurantClient.getMenuItemById(300L)).thenReturn(mockMenuItem);

        // Act & Assert
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            orderService.placeOrder("johndoe", request);
        });

        assertEquals("Menu item 'Cheeseburger' is not available", exception.getMessage());
    }

    @Test
    void testPlaceOrder_MenuItemBelongsToDifferentRestaurant() {
        // Arrange
        mockMenuItem.setRestaurantId(999L); // Different ID

        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setRestaurantId(200L);
        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuItemId(300L);
        itemRequest.setQuantity(1);
        request.setItems(List.of(itemRequest));

        when(customerClient.getCustomerByUsername("johndoe")).thenReturn(mockCustomer);
        when(restaurantClient.getRestaurantById(200L)).thenReturn(mockRestaurant);
        when(restaurantClient.getMenuItemById(300L)).thenReturn(mockMenuItem);

        // Act & Assert
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            orderService.placeOrder("johndoe", request);
        });

        assertEquals("Menu item 'Cheeseburger' does not belong to restaurant 'Burger Joint'", exception.getMessage());
    }

    @Test
    void testGetOrderById_Found() {
        // Arrange
        Order order = new Order();
        order.setId(1L);
        order.setStatus(Order.OrderStatus.PLACED);
        order.setCustomerId(100L);
        order.setRestaurantId(200L);
        order.setItems(new ArrayList<>());
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        // Act
        OrderResponse response = orderService.getOrderById(1L);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("PLACED", response.getStatus());
    }

    @Test
    void testGetOrderById_NotFound() {
        // Arrange
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.getOrderById(99L);
        });
    }

    @Test
    void testUpdateOrderStatus() {
        // Arrange
        Order order = new Order();
        order.setId(1L);
        order.setStatus(Order.OrderStatus.PLACED);
        order.setItems(new ArrayList<>());
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        OrderResponse response = orderService.updateOrderStatus(1L, "PREPARING");

        // Assert
        assertEquals("PREPARING", response.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void testCancelOrder_Success() {
        // Arrange
        Order order = new Order();
        order.setId(1L);
        order.setCustomerId(100L);
        order.setStatus(Order.OrderStatus.PLACED);
        order.setItems(new ArrayList<>());
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        
        // Mock customer client
        when(customerClient.getCustomerById(100L)).thenReturn(mockCustomer);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        OrderResponse response = orderService.cancelOrder(1L, "johndoe");

        // Assert
        assertEquals("CANCELLED", response.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void testCancelOrder_Unauthorized() {
        // Arrange
        Order order = new Order();
        order.setId(1L);
        order.setCustomerId(100L);
        order.setStatus(Order.OrderStatus.PLACED);
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(customerClient.getCustomerById(100L)).thenReturn(mockCustomer); // Mock customer username is 'johndoe'

        // Act & Assert
        Exception exception = assertThrows(UnauthorizedException.class, () -> {
            orderService.cancelOrder(1L, "wronguser");
        });

        assertEquals("You can only cancel your own orders", exception.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void testCancelOrder_InvalidStatus() {
        // Arrange
        Order order = new Order();
        order.setId(1L);
        order.setCustomerId(100L);
        order.setStatus(Order.OrderStatus.OUT_FOR_DELIVERY);
        order.setItems(new ArrayList<>());
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        
        when(customerClient.getCustomerById(100L)).thenReturn(mockCustomer);

        // Act & Assert
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            orderService.cancelOrder(1L, "johndoe");
        });

        assertEquals("Cannot cancel order in status: OUT_FOR_DELIVERY", exception.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
    }
}
