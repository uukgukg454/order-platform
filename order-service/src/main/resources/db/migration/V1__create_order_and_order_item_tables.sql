-- "orders" not "order" — order is a reserved word in Postgres.
CREATE TABLE orders (
    id            UUID PRIMARY KEY,
    customer_id   UUID NOT NULL,
    status        VARCHAR(20) NOT NULL,
    total_amount  NUMERIC(19, 2) NOT NULL,
    currency      VARCHAR(3) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE order_items (
    id            UUID PRIMARY KEY,
    order_id      UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id    UUID NOT NULL,
    product_name  VARCHAR(255) NOT NULL,
    unit_price    NUMERIC(19, 2) NOT NULL,
    quantity      INTEGER NOT NULL
);

-- Every order_items row is looked up by its parent order (Order.items),
-- so the FK column needs an index or that lookup is a sequential scan.
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
