package com.fooddelivery.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryStatusEvent {
    private Long orderId;
    private Long deliveryId;
    private String status;
    private String driverName;
    private String driverPhone;
}
