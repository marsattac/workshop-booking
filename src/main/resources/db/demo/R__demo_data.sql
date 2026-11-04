-- Demo data, loaded only with the dev profile (see application-dev.yaml).
-- Repeatable migration: Flyway reruns it whenever this file changes, hence the ON CONFLICT clauses.

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
