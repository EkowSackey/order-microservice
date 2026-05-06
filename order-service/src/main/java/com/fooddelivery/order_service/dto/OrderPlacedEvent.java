package com.fooddelivery.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPlacedEvent {
    private Long orderId;
    private Long customerId;
    private Long restaurantId;
    private String pickupAddress;
    private String deliveryAddress;
    private String restaurantName;
    private String customerFirstName;
    private String customerLastName;
}
