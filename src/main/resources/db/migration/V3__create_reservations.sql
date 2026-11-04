CREATE TABLE reservations (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     TEXT        NOT NULL REFERENCES users (id),
    workshop_id TEXT        NOT NULL REFERENCES workshops (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A user can book the same workshop only once.
    -- The constraint's index also serves lookups by user_id.
    CONSTRAINT reservations_user_workshop_unique UNIQUE (user_id, workshop_id)
);

-- Index for lookups by workshop, which the unique constraint does not cover.
CREATE INDEX reservations_workshop_id_idx ON reservations (workshop_id);
