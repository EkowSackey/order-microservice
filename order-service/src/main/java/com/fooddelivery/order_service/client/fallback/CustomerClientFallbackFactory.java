package com.fooddelivery.order_service.client.fallback;

import com.fooddelivery.order_service.client.CustomerClient;
import com.fooddelivery.order_service.client.CustomerDTO;
import com.fooddelivery.order_service.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class CustomerClientFallbackFactory implements FallbackFactory<CustomerClient> {

    private static final Logger log = LoggerFactory.getLogger(CustomerClientFallbackFactory.class);

    @Override
    public CustomerClient create(Throwable cause) {
        log.error("Customer service circuit breaker activated: {}", cause.getMessage());
        return new CustomerClient() {
            @Override
            public CustomerDTO getCustomerByUsername(String username) {
                throw new ServiceUnavailableException(
                        "Customer service is currently unavailable. Please try again later.");
            }

            @Override
            public CustomerDTO getCustomerById(Long id) {
                throw new ServiceUnavailableException(
                        "Customer service is currently unavailable. Please try again later.");
            }
        };
    }
}
