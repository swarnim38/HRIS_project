INSERT IGNORE INTO employment_events (org_id,employee_id,department_id,effective_on,kind,status,job,job_level)
SELECT org_id,employee_id,department_id,joined_on,'HIRE',status,job,'L1' FROM employees
WHERE (org_id,employee_id) IN (('DEMO-A','E1'),('DEMO-A','E2'),('DEMO-B','E1'))
AND status <> 'EXITED';
UPDATE employment_events
SET job=CASE WHEN org_id='DEMO-A' AND employee_id='E2' THEN 'Analyst' ELSE 'Engineer' END,job_level='L1'
WHERE kind='HIRE' AND job IS NULL AND (org_id,employee_id) IN (('DEMO-A','E1'),('DEMO-A','E2'),('DEMO-B','E1'));
INSERT IGNORE INTO employment_coverage VALUES ('DEMO-A','ENG','2020-01-01','2099-12-31'),
  ('DEMO-A','OPS','2020-01-01','2099-12-31'), ('DEMO-B','ENG','2022-01-01','2099-12-31');
INSERT IGNORE INTO metric_settings VALUES ('DEMO-A',3,2,5,50,25,25), ('DEMO-B',3,2,5,50,25,25);
INSERT IGNORE INTO financial_periods VALUES ('DEMO-A','2025-01-01','2025-01-31','USD',1200,10000,30000);
INSERT IGNORE INTO attendance_days VALUES ('DEMO-A','E1','ENG','2025-01-06',8,2),
  ('DEMO-A','E2','OPS','2025-01-06',8,0);
INSERT IGNORE INTO promotions VALUES ('DEMO-A','E1','ENG','2025-01-01','L1','L2');
UPDATE employees SET job_level='L2' WHERE org_id='DEMO-A' AND employee_id='E1' AND job_level IS NULL;
INSERT IGNORE INTO surveys VALUES ('DEMO-A','PULSE',1,'ENG','ENGAGEMENT','How engaged do you feel at work?'),
  ('DEMO-A','SAT',1,'OPS','SATISFACTION','How satisfied are you with your work?');
INSERT INTO survey_responses (org_id,survey_id,version_no,answered_on,score)
SELECT 'DEMO-A', sample.survey_id, 1, '2025-01-06', sample.score
FROM (SELECT 'PULSE' AS survey_id, 3 AS score UNION ALL SELECT 'PULSE',4
      UNION ALL SELECT 'PULSE',5 UNION ALL SELECT 'SAT',3) sample
WHERE NOT EXISTS (SELECT 1 FROM survey_responses WHERE org_id='DEMO-A' AND survey_id=sample.survey_id AND version_no=1);
