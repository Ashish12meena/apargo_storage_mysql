-- LOCAL PROFILE ONLY. Never on the Flyway path.
-- The predecessor's V1 ran inserts like these in every environment.
INSERT IGNORE INTO org_storage     (org_id, max_bytes, used_bytes) VALUES (1, 10737418240, 0);
INSERT IGNORE INTO project_storage (org_id, project_id, max_bytes, used_bytes) VALUES (1, 1, 5368709120, 0);
