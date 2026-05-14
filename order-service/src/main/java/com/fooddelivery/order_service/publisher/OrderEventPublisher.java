package com.fooddelivery.order_service.publisher;

import com.fooddelivery.order_service.config.RabbitMQConfig;
import com.fooddelivery.order_service.dto.OrderCancelledEvent;
import com.fooddelivery.order_service.dto.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // Fires only after the DB transaction commits — guarantees the order row exists
    // before delivery-service receives the event.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("Publishing OrderPlacedEvent for order id: {}", event.getOrderId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "order.placed", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Publishing OrderCancelledEvent for order id: {}", event.getOrderId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "order.cancelled", event);
    }
}
