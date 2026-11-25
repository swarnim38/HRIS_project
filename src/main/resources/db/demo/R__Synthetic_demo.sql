INSERT IGNORE INTO organizations VALUES ('DEMO-A', 'Synthetic A'), ('DEMO-B', 'Synthetic B');
INSERT IGNORE INTO departments VALUES ('DEMO-A', 'ENG', 'Engineering'), ('DEMO-A', 'OPS', 'Operations'),
    ('DEMO-B', 'ENG', 'Engineering');
INSERT IGNORE INTO demo_actor_assignments VALUES ('a-admin', 'DEMO-A', NULL, 'HR_ADMIN'),
    ('a-manager', 'DEMO-A', 'ENG', 'MANAGER'), ('a-finance', 'DEMO-A', NULL, 'FINANCE'),
    ('b-admin', 'DEMO-B', NULL, 'HR_ADMIN');
INSERT IGNORE INTO employees (org_id, department_id, employee_id, full_name, job, joined_on, status)
    VALUES ('DEMO-A', 'ENG', 'E1', 'Synthetic A Engineer', 'Engineer', '2020-01-01', 'ACTIVE'),
    ('DEMO-A', 'OPS', 'E2', 'Synthetic A Analyst', 'Analyst', '2021-01-01', 'PROBATION'),
    ('DEMO-B', 'ENG', 'E1', 'Synthetic B Engineer', 'Engineer', '2022-01-01', 'ACTIVE');
INSERT IGNORE INTO compensation_history VALUES ('DEMO-A', 'E1', 'ENG', '2023-01-01', 5000, 'USD', 'MONTHLY', 1),
    ('DEMO-A', 'E2', 'OPS', '2023-01-01', 4500, 'USD', 'MONTHLY', 1),
    ('DEMO-B', 'E1', 'ENG', '2023-01-01', 7000, 'USD', 'MONTHLY', 1);
INSERT IGNORE INTO candidates VALUES ('DEMO-A', 'ENG', 'C1', 'Synthetic A Engineering Candidate'),
    ('DEMO-A', 'OPS', 'C2', 'Synthetic A Operations Candidate'),
    ('DEMO-B', 'ENG', 'C1', 'Synthetic B Engineering Candidate');
