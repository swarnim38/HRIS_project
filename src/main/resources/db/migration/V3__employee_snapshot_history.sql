ALTER TABLE employment_events
    ADD COLUMN job VARCHAR(160),
    ADD COLUMN job_level VARCHAR(64),
    DROP CHECK employment_events_chk_1,
    ADD CONSTRAINT chk_employment_event_kind
        CHECK (kind IN ('HIRE','CHANGE','SEPARATION','REHIRE','SNAPSHOT'));
