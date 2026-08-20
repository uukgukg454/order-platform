CREATE TABLE payments (
    id          UUID PRIMARY KEY,
    order_id    UUID NOT NULL,
    amount      NUMERIC(19, 2) NOT NULL,
    currency    VARCHAR(3) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

-- Every payment is looked up by the order it's for, so the column needs an
-- index or that lookup is a sequential scan.
CREATE INDEX idx_payments_order_id ON payments (order_id);
