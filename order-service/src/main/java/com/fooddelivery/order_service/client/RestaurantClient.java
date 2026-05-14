package com.fooddelivery.order_service.client;

import com.fooddelivery.order_service.client.fallback.RestaurantClientFallbackFactory;
import com.fooddelivery.order_service.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "restaurant-service", configuration = FeignConfig.class, fallbackFactory = RestaurantClientFallbackFactory.class)
public interface RestaurantClient {

    @GetMapping("/api/restaurants/{id}")
    RestaurantDTO getRestaurantById(@PathVariable("id") Long id);

    @GetMapping("/api/menu-items/{id}")
    MenuItemDTO getMenuItemById(@PathVariable("id") Long id);
}
