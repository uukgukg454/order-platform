CREATE TABLE stock (
    product_id  UUID PRIMARY KEY,
    quantity    INTEGER NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

-- Seed data for local/dev use and future end-to-end tests spanning
-- order-service -> Kafka -> inventory-service. 22222222-2222-2222-2222-
-- 222222222222 is reserved as the product id a future test would use when
-- placing an order it expects this service to already have matching stock
-- for, so that id specifically needs a comfortably large starting quantity.
INSERT INTO stock (product_id, quantity, updated_at) VALUES
    ('22222222-2222-2222-2222-222222222222', 500, now()),
    ('33333333-3333-3333-3333-333333333333', 250, now()),
    ('44444444-4444-4444-4444-444444444444', 100, now());
