package com.fooddelivery.order_service.publisher;

import com.fooddelivery.order_service.config.RabbitMQConfig;
import com.fooddelivery.order_service.dto.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishOrderPlacedEvent(OrderPlacedEvent event) {
        log.info("Publishing OrderPlacedEvent for order id: {}", event.getOrderId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "order.placed", event);
    }

    public void publishOrderCancelledEvent(Long orderId) {
        log.info("Publishing OrderCancelledEvent for order id: {}", orderId);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "order.cancelled", orderId);
    }
}
