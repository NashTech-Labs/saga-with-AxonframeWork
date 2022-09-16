package com.knoldus.orderservice.command.saga;


import com.knoldus.commonservice.commands.*;
import com.knoldus.commonservice.events.*;
import com.knoldus.commonservice.model.CardDetails;
import com.knoldus.commonservice.model.User;
import com.knoldus.commonservice.queries.GetUserPaymentDetailsQuery;
import com.knoldus.orderservice.command.events.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Saga
@Slf4j
public class OrderProcessingSaga {

    @Autowired
    private transient CommandGateway commandGateway;

    public OrderProcessingSaga() {
    }

    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    private void handle(OrderCreatedEvent event) {
        log.info("OrderCreatedEvent in Saga for Order Id : {}",
                event.getOrderId());
// we can use QueryGateway to get the details from other service.
// Added the detail we can integrate other service for fetching the detail
        CardDetails cardDetails
                = CardDetails.builder()
                .name("Anshuman")
                .validUntilYear(2022)
                .validUntilMonth(01)
                .cardNumber("123456789")
                .cvv(111)
                .build();
        User user = User.builder().cardDetails(cardDetails).build();

        ValidatePaymentCommand validatePaymentCommand
                = ValidatePaymentCommand
                .builder()
                .cardDetails(user.getCardDetails())
                .orderId(event.getOrderId())
                .paymentId(UUID.randomUUID().toString())
                .build();

        commandGateway.sendAndWait(validatePaymentCommand);
    }

    private void cancelOrderCommand(String orderId) {
        CancelOrderCommand cancelOrderCommand
                = new CancelOrderCommand(orderId);
        commandGateway.send(cancelOrderCommand);
    }

    @SagaEventHandler(associationProperty = "orderId")
    private void handle(PaymentProcessedEvent event) {
        log.info("PaymentProcessedEvent in Saga for Order Id : {}",
                event.getOrderId());
        try {
//uncomment this to see the the flow of cancelled order
            /*if(true)
                throw new Exception();
*/
            ShipOrderCommand shipOrderCommand
                    = ShipOrderCommand
                    .builder()
                    .shipmentId(UUID.randomUUID().toString())
                    .orderId(event.getOrderId())
                    .build();
            commandGateway.send(shipOrderCommand);
        } catch (Exception e) {
            log.error(e.getMessage());
            // Start the compensating transaction
            cancelPaymentCommand(event);
        }
    }

    private void cancelPaymentCommand(PaymentProcessedEvent event) {
        CancelPaymentCommand cancelPaymentCommand
                = new CancelPaymentCommand(
                event.getPaymentId(), event.getOrderId()
        );

        commandGateway.send(cancelPaymentCommand);
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderShippedEvent event) {

        log.info("OrderShippedEvent in Saga for Order Id : {}",
                event.getOrderId());

        CompleteOrderCommand completeOrderCommand
                = CompleteOrderCommand.builder()
                .orderId(event.getOrderId())
                .orderStatus("APPROVED")
                .build();

        commandGateway.send(completeOrderCommand);
    }

    @SagaEventHandler(associationProperty = "orderId")
    @EndSaga
    public void handle(OrderCompletedEvent event) {
        log.info("OrderCompletedEvent in Saga for Order Id : {}",
                event.getOrderId());
    }

    @SagaEventHandler(associationProperty = "orderId")
    @EndSaga
    public void handle(OrderCancelledEvent event) {
        log.info("OrderCancelledEvent in Saga for Order Id : {}",
                event.getOrderId());
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(PaymentCancelledEvent event) {
        log.info("PaymentCancelledEvent in Saga for Order Id : {}",
                event.getOrderId());
        cancelOrderCommand(event.getOrderId());
    }
}
