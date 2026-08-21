package com.ujjwal.search_indexer_service.config;

import com.ujjwal.search_indexer_service.event.PaymentCompletedEvent;
import com.ujjwal.search_indexer_service.event.PaymentFailedEvent;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

/**
 * Extra listener container factories application.yml alone can't express.
 *
 * spring.kafka.consumer.properties in application.yml can only set ONE
 * global JsonDeserializer default type, and it's already pointed at
 * OrderCreatedEvent for orders.created (the DEFAULT factory Boot
 * autoconfigures from that YAML). This service also consumes
 * payments.completed and payments.failed, each a differently-shaped event,
 * so two more factories are built here in code — same pattern as
 * inventory-service's paymentFailedKafkaListenerContainerFactory: reuse
 * every property already set in YAML (bootstrap-servers, group-id,
 * auto-offset-reset, trusted-packages, ...) via KafkaProperties, overriding
 * only the one property that needs to differ per factory.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent> paymentsCompletedKafkaListenerContainerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> consumerProps = kafkaProperties.buildConsumerProperties();
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentCompletedEvent.class.getName());

        ConsumerFactory<String, PaymentCompletedEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> paymentsFailedKafkaListenerContainerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> consumerProps = kafkaProperties.buildConsumerProperties();
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentFailedEvent.class.getName());

        ConsumerFactory<String, PaymentFailedEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
