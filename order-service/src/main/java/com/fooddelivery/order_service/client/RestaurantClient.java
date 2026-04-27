package com.fooddelivery.order_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// Placeholder values, the name and URL will depend on your setup (e.g., using Eureka or direct URL)
@FeignClient(name = "restaurant-service")
public interface RestaurantClient {

    @GetMapping("/api/restaurants/{id}")
    RestaurantDTO getRestaurantById(@PathVariable("id") Long id);

    @GetMapping("/api/menu-items/{id}")
    MenuItemDTO getMenuItemById(@PathVariable("id") Long id);
}
