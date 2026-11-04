-- Données de démo, chargées uniquement avec le profil dev (voir application-dev.yaml).
-- Migration répétable : Flyway la rejoue quand ce fichier change, d'où les ON CONFLICT.

INSERT INTO users (id, name) VALUES
    ('u1', 'Alice'),
    ('u2', 'Bob'),
    ('u3', 'Chloé'),
    ('u4', 'David')
ON CONFLICT (id) DO NOTHING;

INSERT INTO workshops (id, title, seats_left) VALUES
    ('w1', 'Java 25 : virtual threads et structured concurrency', 20),
    ('w2', 'Spring Boot 4 en pratique', 2),
    ('w3', 'PostgreSQL pour les développeurs', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO reservations (user_id, workshop_id) VALUES
    ('u1', 'w2'),
    ('u2', 'w3')
ON CONFLICT (user_id, workshop_id) DO NOTHING;
