CREATE TABLE workshops (
    id         TEXT    PRIMARY KEY,
    title      TEXT    NOT NULL,
    seats_left INTEGER NOT NULL,
    CONSTRAINT workshops_seats_left_not_negative CHECK (seats_left >= 0)
);
