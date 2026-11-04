CREATE TABLE reservations (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     TEXT        NOT NULL REFERENCES users (id),
    workshop_id TEXT        NOT NULL REFERENCES workshops (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Un utilisateur ne réserve qu'une fois le même atelier.
    -- L'index de la contrainte sert aussi les recherches par user_id.
    CONSTRAINT reservations_user_workshop_unique UNIQUE (user_id, workshop_id)
);

-- Index pour les recherches par atelier, que la contrainte d'unicité ne couvre pas.
CREATE INDEX reservations_workshop_id_idx ON reservations (workshop_id);
