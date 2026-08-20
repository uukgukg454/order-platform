package com.ujjwal.order_service;

import com.ujjwal.order_service.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Context-load smoke test. Postgres, Redis, and Kafka all come from
 * AbstractIntegrationTest — every one of them is genuinely required just
 * for this context to start: JPA/Flyway need Postgres, getOrder's
 * @Cacheable("orders") needs Redis, and OrderService's KafkaTemplate
 * constructor dependency needs Kafka. None of it is optional to this
 * specific test, even though the test itself never calls any of them
 * directly.
 */
class OrderServiceApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }

}
