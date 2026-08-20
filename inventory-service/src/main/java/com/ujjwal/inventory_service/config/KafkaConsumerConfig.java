package com.ujjwal.inventory_service.config;

import com.ujjwal.inventory_service.event.PaymentFailedEvent;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

/**
 * Extra Kafka consumer wiring that application.yml alone can't express:
 * a second listener container factory for payments.failed, and the
 * DLQ error handler for orders.created.
 */
@Configuration
public class KafkaConsumerConfig {

    /**
     * Extra listener container factory, for payments.failed only.
     *
     * application.yml's spring.kafka.consumer.properties can only express
     * ONE global JsonDeserializer default type, and it's already pointed at
     * OrderCreatedEvent for orders.created (OrderCreatedEventListener uses
     * the default "kafkaListenerContainerFactory" Boot autoconfigures from
     * that YAML, unchanged). This service now consumes a second,
     * differently-shaped event too, so a second factory is built here in
     * code — reusing every property already set in YAML (bootstrap-servers,
     * group-id, auto-offset-reset, trusted-packages, the
     * ErrorHandlingDeserializer wrapping, ...) via KafkaProperties, and
     * overriding only the one property that actually needs to differ: which
     * class the delegate JsonDeserializer defaults to.
     *
     * Note this factory does NOT get kafkaErrorHandler below automatically
     * — that only happens for factories Boot itself builds via
     * ConcurrentKafkaListenerContainerFactoryConfigurer (see
     * kafkaErrorHandler's own comment). PaymentFailedEventListener
     * currently runs with Spring Kafka's own built-in default error
     * handling instead of the 1s/3-retry/DLT behavior configured below —
     * worth wiring explicitly (factory.setCommonErrorHandler(...)) if this
     * listener needs the same DLQ treatment later.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> paymentFailedKafkaListenerContainerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> consumerProps = kafkaProperties.buildConsumerProperties();
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentFailedEvent.class.getName());

        ConsumerFactory<String, PaymentFailedEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }

    /**
     * Dead-letter handling for the DEFAULT listener container factory
     * (the one Boot autoconfigures from spring.kafka.consumer.* in
     * application.yml, used by OrderCreatedEventListener).
     *
     * Verified rather than assumed: Boot's KafkaAnnotationDrivenConfiguration
     * takes an ObjectProvider&lt;CommonErrorHandler&gt; in its constructor and
     * calls getIfUnique() on it, feeding whatever it finds into
     * ConcurrentKafkaListenerContainerFactoryConfigurer.setCommonErrorHandler(...)
     * before that configurer builds the default factory bean — confirmed by
     * decompiling this exact version's spring-boot-kafka-4.1.0.jar rather
     * than assuming Boot 3.x's equivalent behavior still holds. So a single
     * DefaultErrorHandler bean like this one is picked up automatically; no
     * explicit factory.setCommonErrorHandler(...) call is needed here.
     *
     * FixedBackOff(1000, 3): retries the listener 3 times, 1 second apart,
     * before DeadLetterPublishingRecoverer takes over. DeadLetterPublishing
     * Recoverer's single-KafkaOperations constructor uses its default
     * destination-naming behavior — original topic name + ".DLT" — so
     * failures on "orders.created" (this app's app.kafka.topics.orders-
     * created) land on "orders.created.DLT". That topic needs to already
     * exist (auto-create is off), created manually via kafka-topics.sh.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
