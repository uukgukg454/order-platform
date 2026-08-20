package com.ujjwal.order_service.idempotency;

import com.ujjwal.order_service.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises IdempotencyService against a real Redis, not a mock — proving
 * the actual atomic SET ... NX round trip works end to end, which
 * IdempotencyServiceTest's mocked ValueOperations can't: a mock can be told
 * to return true then false, but that doesn't prove Redis itself enforces
 * "only the first caller for a key gets true". Postgres and Kafka come
 * along too via AbstractIntegrationTest even though this test never
 * touches either — this is a full @SpringBootTest, so the whole
 * ApplicationContext boots, and none of DataSource/Flyway/JPA/KafkaTemplate
 * autoconfig in this app is gated behind anything that would let the
 * context start without them.
 */
@SpringBootTest
class IdempotencyServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Test
    void tryMarkProcessed_returnsTrueOnce_thenFalseForTheSameKey() {
        String idempotencyKey = "order-created-" + System.nanoTime();

        boolean firstAttempt = idempotencyService.tryMarkProcessed(idempotencyKey);
        boolean secondAttempt = idempotencyService.tryMarkProcessed(idempotencyKey);

        assertThat(firstAttempt).isTrue();
        assertThat(secondAttempt).isFalse();
    }
}
