CREATE TABLE organizations (
    org_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(160) NOT NULL
);
CREATE TABLE departments (
    org_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    PRIMARY KEY (org_id, department_id),
    FOREIGN KEY (org_id) REFERENCES organizations(org_id)
);
CREATE TABLE demo_actor_assignments (
    actor_id VARCHAR(64) PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64),
    role VARCHAR(16) NOT NULL,
    CHECK ((role = 'MANAGER' AND department_id IS NOT NULL)
        OR (role IN ('HR_ADMIN', 'FINANCE') AND department_id IS NULL)),
    FOREIGN KEY (org_id) REFERENCES organizations(org_id),
    FOREIGN KEY (org_id, department_id) REFERENCES departments(org_id, department_id)
);
CREATE TABLE employees (
    org_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    job VARCHAR(160) NOT NULL,
    joined_on DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    CHECK (status IN ('ACTIVE', 'PROBATION', 'NOTICE_PERIOD', 'EXITED')),
    PRIMARY KEY (org_id, employee_id),
    FOREIGN KEY (org_id, department_id) REFERENCES departments(org_id, department_id)
);
CREATE TABLE compensation_history (
    org_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    effective_on DATE NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    pay_basis VARCHAR(12) NOT NULL,
    fte DECIMAL(5,4) NOT NULL,
    CHECK (amount >= 0 AND fte > 0 AND fte <= 1 AND pay_basis IN ('HOURLY', 'MONTHLY', 'ANNUAL')),
    PRIMARY KEY (org_id, employee_id, effective_on),
    FOREIGN KEY (org_id, employee_id) REFERENCES employees(org_id, employee_id),
    FOREIGN KEY (org_id, department_id) REFERENCES departments(org_id, department_id)
);
CREATE TABLE candidates (
    org_id VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    candidate_id VARCHAR(64) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    PRIMARY KEY (org_id, candidate_id),
    FOREIGN KEY (org_id, department_id) REFERENCES departments(org_id, department_id)
);
