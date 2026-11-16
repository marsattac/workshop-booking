-- Capacity: total number of seats of a workshop; seats_left is what remains of it.
ALTER TABLE workshops ADD COLUMN capacity INTEGER;

-- Existing workshops: their capacity is the seats left plus the seats already booked.
UPDATE workshops w
SET capacity = w.seats_left + (SELECT count(*) FROM reservations r WHERE r.workshop_id = w.id);

ALTER TABLE workshops ALTER COLUMN capacity SET NOT NULL;

-- Replaces CHECK (seats_left >= 0): seats left can neither go below zero nor exceed the capacity.
ALTER TABLE workshops DROP CONSTRAINT workshops_seats_left_not_negative;
ALTER TABLE workshops ADD CONSTRAINT workshops_seats_left_within_capacity
    CHECK (seats_left BETWEEN 0 AND capacity);
