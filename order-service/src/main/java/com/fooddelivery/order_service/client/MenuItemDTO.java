package com.fooddelivery.order_service.client;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class MenuItemDTO {
    private Long id;
    private String name;
    private BigDecimal price;
    private boolean available;
    private Long restaurantId;
}
