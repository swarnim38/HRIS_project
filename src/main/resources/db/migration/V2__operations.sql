CREATE TABLE local_accounts (
    org_id VARCHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(16) NOT NULL,
    department_id VARCHAR(64),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (org_id, username),
    FOREIGN KEY (org_id) REFERENCES organizations(org_id),
    FOREIGN KEY (org_id, department_id) REFERENCES departments(org_id, department_id),
    CHECK ((role = 'MANAGER' AND department_id IS NOT NULL) OR
           (role IN ('HR_ADMIN', 'FINANCE') AND department_id IS NULL))
);

CREATE TABLE audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    action VARCHAR(48) NOT NULL,
    entity_id VARCHAR(160) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY (org_id, id),
    FOREIGN KEY (org_id, username) REFERENCES local_accounts(org_id, username)
);

ALTER TABLE employees ADD COLUMN job_level VARCHAR(64), ADD COLUMN demographic VARCHAR(64);
CREATE TABLE employment_events (
    org_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    effective_on DATE NOT NULL,
    kind VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    PRIMARY KEY (org_id, employee_id, effective_on),
    FOREIGN KEY (org_id, employee_id) REFERENCES employees(org_id, employee_id),
    FOREIGN KEY (org_id, department_id) REFERENCES departments(org_id, department_id),
    CHECK (kind IN ('HIRE','CHANGE','SEPARATION','REHIRE')),
    CHECK (status IN ('ACTIVE','PROBATION','NOTICE_PERIOD','EXITED'))
);

CREATE TABLE employment_coverage (
    org_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    from_date DATE NOT NULL,
    through_date DATE NOT NULL,
    PRIMARY KEY (org_id, department_id),
    FOREIGN KEY (org_id, department_id) REFERENCES departments(org_id, department_id),
    CHECK (through_date >= from_date)
);

CREATE TABLE promotions (
    org_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    effective_on DATE NOT NULL,
    prior_level VARCHAR(64),
    new_level VARCHAR(64) NOT NULL,
    PRIMARY KEY (org_id, employee_id, effective_on),
    FOREIGN KEY (org_id, employee_id) REFERENCES employees(org_id, employee_id),
    FOREIGN KEY (org_id, department_id) REFERENCES departments(org_id, department_id)
);

CREATE TABLE requisitions (
    org_id VARCHAR(64) NOT NULL,
    requisition_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    job VARCHAR(160) NOT NULL,
    opened_on DATE NOT NULL,
    responsible_username VARCHAR(64) NOT NULL,
    PRIMARY KEY (org_id, requisition_id),
    FOREIGN KEY (org_id, department_id) REFERENCES departments(org_id, department_id),
    FOREIGN KEY (org_id, responsible_username) REFERENCES local_accounts(org_id, username)
);

CREATE TABLE applications (
    org_id VARCHAR(64) NOT NULL,
    application_id VARCHAR(64) NOT NULL,
    requisition_id VARCHAR(64) NOT NULL,
    candidate_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (org_id, application_id),
    UNIQUE (org_id, requisition_id, candidate_id),
    FOREIGN KEY (org_id, requisition_id) REFERENCES requisitions(org_id, requisition_id),
    FOREIGN KEY (org_id, candidate_id) REFERENCES candidates(org_id, candidate_id)
);

CREATE TABLE application_stage_events (
    org_id VARCHAR(64) NOT NULL,
    application_id VARCHAR(64) NOT NULL,
    sequence_no INT NOT NULL,
    stage VARCHAR(16) NOT NULL,
    effective_on DATE NOT NULL,
    reason VARCHAR(160),
    PRIMARY KEY (org_id, application_id, sequence_no),
    FOREIGN KEY (org_id, application_id) REFERENCES applications(org_id, application_id),
    CHECK (stage IN ('APPLIED','SCREENING','INTERVIEW','OFFERED','ACCEPTED','DECLINED','REJECTED','WITHDRAWN','HIRED'))
);

CREATE TABLE hires (
    org_id VARCHAR(64) NOT NULL,
    application_id VARCHAR(64) NOT NULL,
    requisition_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (org_id, application_id),
    UNIQUE (org_id, requisition_id),
    UNIQUE (org_id, employee_id),
    FOREIGN KEY (org_id, application_id) REFERENCES applications(org_id, application_id),
    FOREIGN KEY (org_id, requisition_id) REFERENCES requisitions(org_id, requisition_id),
    FOREIGN KEY (org_id, employee_id) REFERENCES employees(org_id, employee_id)
);

CREATE TABLE legacy_recruiting (
    org_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    record_hash CHAR(64) NOT NULL,
    candidate_id VARCHAR(64) NOT NULL,
    applied_on DATE,
    offered_on DATE,
    rejected_on DATE,
    reason VARCHAR(160),
    PRIMARY KEY (org_id, record_hash),
    FOREIGN KEY (org_id, candidate_id) REFERENCES candidates(org_id, candidate_id),
    FOREIGN KEY (org_id, department_id) REFERENCES departments(org_id, department_id)
);

CREATE TABLE import_receipts (
    org_id VARCHAR(64) NOT NULL,
    source_hash CHAR(64) NOT NULL,
    row_count INT NOT NULL,
    amounts VARCHAR(2000) NOT NULL,
    PRIMARY KEY (org_id, source_hash),
    FOREIGN KEY (org_id) REFERENCES organizations(org_id)
);

CREATE TABLE financial_periods (
    org_id VARCHAR(64) NOT NULL,
    from_date DATE NOT NULL,
    through_date DATE NOT NULL,
    currency CHAR(3) NOT NULL,
    recruiting_cost DECIMAL(15,2) NOT NULL,
    employee_cost DECIMAL(15,2) NOT NULL,
    revenue DECIMAL(15,2) NOT NULL,
    PRIMARY KEY (org_id, from_date, through_date),
    FOREIGN KEY (org_id) REFERENCES organizations(org_id),
    CHECK (through_date >= from_date AND recruiting_cost >= 0 AND employee_cost >= 0)
);

CREATE TABLE attendance_days (
    org_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    work_date DATE NOT NULL,
    scheduled_hours DECIMAL(5,2) NOT NULL,
    unplanned_absence DECIMAL(5,2) NOT NULL,
    PRIMARY KEY (org_id, employee_id, work_date),
    FOREIGN KEY (org_id, employee_id) REFERENCES employees(org_id, employee_id),
    FOREIGN KEY (org_id, department_id) REFERENCES departments(org_id, department_id),
    CHECK (scheduled_hours BETWEEN 0 AND 24 AND unplanned_absence BETWEEN 0 AND scheduled_hours)
);

CREATE TABLE post_hire_outcomes (
    org_id VARCHAR(64) NOT NULL,
    application_id VARCHAR(64) NOT NULL,
    measured_on DATE NOT NULL,
    performance DECIMAL(5,2) NOT NULL,
    manager_score DECIMAL(5,2) NOT NULL,
    retained BOOLEAN NOT NULL,
    PRIMARY KEY (org_id, application_id, measured_on),
    FOREIGN KEY (org_id, application_id) REFERENCES hires(org_id, application_id),
    CHECK (performance BETWEEN 0 AND 100 AND manager_score BETWEEN 0 AND 100)
);

CREATE TABLE surveys (
    org_id VARCHAR(64) NOT NULL,
    survey_id VARCHAR(64) NOT NULL,
    version_no INT NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    question VARCHAR(250) NOT NULL,
    PRIMARY KEY (org_id, survey_id, version_no),
    FOREIGN KEY (org_id, department_id) REFERENCES departments(org_id, department_id),
    CHECK (version_no > 0 AND kind IN ('ENGAGEMENT','SATISFACTION'))
);

CREATE TABLE survey_responses (
    response_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    survey_id VARCHAR(64) NOT NULL,
    version_no INT NOT NULL,
    answered_on DATE NOT NULL,
    score INT NOT NULL,
    FOREIGN KEY (org_id, survey_id, version_no) REFERENCES surveys(org_id, survey_id, version_no),
    CHECK (score BETWEEN 1 AND 5)
);

CREATE TABLE metric_settings (
    org_id VARCHAR(64) PRIMARY KEY,
    survey_minimum INT NOT NULL,
    pay_minimum INT NOT NULL,
    tenure_years INT NOT NULL,
    performance_weight INT NOT NULL,
    manager_weight INT NOT NULL,
    retention_weight INT NOT NULL,
    FOREIGN KEY (org_id) REFERENCES organizations(org_id),
    CHECK (survey_minimum >= 3 AND pay_minimum >= 2 AND tenure_years > 0),
    CHECK (performance_weight >= 0 AND manager_weight >= 0 AND retention_weight >= 0
        AND performance_weight + manager_weight + retention_weight = 100)
);
