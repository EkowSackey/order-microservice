package com.fooddelivery.order_service.client;

import lombok.Data;

@Data
public class CustomerDTO {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String deliveryAddress;
}
