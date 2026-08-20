package com.ujjwal.order_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// A throwaway Testcontainers Postgres, not the dev database on localhost:5432
// — this test just needs *a* database Flyway/Hibernate can validate against
// to load the context, not the specific one a developer happens to have
// running. See OrderControllerIntegrationTest for the full rationale.
@Testcontainers
@SpringBootTest
class OrderServiceApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

	@Test
	void contextLoads() {
	}

}
