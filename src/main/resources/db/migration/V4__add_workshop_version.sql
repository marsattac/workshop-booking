-- Optimistic locking: every seat update increments the version, so a writer can detect
-- that someone else updated the workshop since it read it.
ALTER TABLE workshops ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
