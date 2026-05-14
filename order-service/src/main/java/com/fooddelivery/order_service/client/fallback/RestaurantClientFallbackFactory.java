package com.fooddelivery.order_service.client.fallback;

import com.fooddelivery.order_service.client.MenuItemDTO;
import com.fooddelivery.order_service.client.RestaurantClient;
import com.fooddelivery.order_service.client.RestaurantDTO;
import com.fooddelivery.order_service.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class RestaurantClientFallbackFactory implements FallbackFactory<RestaurantClient> {

    private static final Logger log = LoggerFactory.getLogger(RestaurantClientFallbackFactory.class);

    @Override
    public RestaurantClient create(Throwable cause) {
        log.error("Restaurant service circuit breaker activated: {}", cause.getMessage());
        return new RestaurantClient() {
            @Override
            public RestaurantDTO getRestaurantById(Long id) {
                throw new ServiceUnavailableException(
                        "Restaurant service is currently unavailable. Please try again later.");
            }

            @Override
            public MenuItemDTO getMenuItemById(Long id) {
                throw new ServiceUnavailableException(
                        "Restaurant service is currently unavailable. Please try again later.");
            }
        };
    }
}
