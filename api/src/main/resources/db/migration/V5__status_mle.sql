ALTER TABLE submission DROP CONSTRAINT submission_status_range;
ALTER TABLE submission ADD CONSTRAINT submission_status_range CHECK (status BETWEEN 1 AND 15);
