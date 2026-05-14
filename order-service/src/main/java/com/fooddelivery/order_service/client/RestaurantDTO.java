package com.fooddelivery.order_service.client;

import lombok.Data;

@Data
public class RestaurantDTO {
    private Long id;
    private String name;
    private String address;
    private boolean active;
    private int estimatedDeliveryMinutes;
    private Long ownerId;
}
