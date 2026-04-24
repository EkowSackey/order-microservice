package com.fooddelivery.order_service.client;

import lombok.Data;

@Data
public class RestaurantDTO {
    private Long id;
    private String name;
    private boolean active;
    private int estimatedDeliveryMinutes;
}
