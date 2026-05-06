package com.fooddelivery.order_service.consumer;

import com.fooddelivery.order_service.config.RabbitMQConfig;
import com.fooddelivery.order_service.dto.DeliveryStatusEvent;
import com.fooddelivery.order_service.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class DeliveryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEventConsumer.class);
    private final OrderService orderService;

    public DeliveryEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    public void consumeDeliveryStatusEvent(DeliveryStatusEvent event) {
        log.info("Received DeliveryStatusEvent for order id: {} with status: {}", 
                 event.getOrderId(), event.getStatus());
        
        try {
            orderService.updateOrderFromDelivery(event);
        } catch (Exception e) {
            log.error("Error updating order from delivery event: {}", e.getMessage());
        }
    }
}
