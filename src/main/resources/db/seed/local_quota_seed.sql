-- ═══════════════════════════════════════════════════════════════════════════
-- storage-service — QUOTA SEED
--
-- org 1, projects 1, 2, 3, 257, 767.
--
-- Run AFTER the service has booted once and Flyway has created the schema.
-- Safe to re-run: every statement is INSERT IGNORE, and the repair block at
-- the bottom only rewrites values that are already invalid.
--
-- WHY THIS MATTERS: there is no auto-provisioning anywhere in the codebase.
-- A project without a quota row cannot upload anything — every file fails
-- with QUOTA_NOT_PROVISIONED regardless of its content, and both the org and
-- the project scope are reserved against on every single upload, so one row
-- without the other is no better than neither.
--
-- Usage:
--     mysql -u <user> -p <database> < quota_seed.sql
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Org scope ──────────────────────────────────────────────────────────────
-- MUST run before the project rows: project_storage carries
-- fk_project_storage_org, and INSERT IGNORE downgrades a foreign-key violation
-- to a warning. Seed the projects first and they will silently not be inserted,
-- leaving you with QUOTA_NOT_PROVISIONED and no error explaining it.
--
-- 12.5 GiB — five projects at 2.5 GiB each.
--
-- This was 10 GiB when there were four projects, which left the org ceiling
-- exactly equal to the sum of the project ceilings. Adding project 257 without
-- raising it would oversubscribe the org: each upload reserves against BOTH
-- scopes, so projects could sit well under their own limit and still start
-- failing once the org total crossed 10 GiB.
INSERT IGNORE INTO org_storage (org_id, max_bytes, used_bytes, created_at, updated_at)
VALUES (1, 13421772800, 0, NOW(6), NOW(6));

-- INSERT IGNORE is a no-op on an org row that already exists, so it will NOT
-- raise a ceiling seeded at the old 10 GiB. This does. Guarded so a deliberately
-- larger ceiling set by hand is never lowered.
UPDATE org_storage
SET max_bytes  = 13421772800,
    updated_at = NOW(6)
WHERE org_id = 1
  AND max_bytes < 13421772800;

-- ── Project scope ──────────────────────────────────────────────────────────
-- 2.5 GiB each.
--
-- created_at and updated_at are set EXPLICITLY rather than left to the column
-- defaults. This is not redundant. The earlier version of this seed omitted
-- them, and on a table whose DATETIME columns lack DEFAULT CURRENT_TIMESTAMP(6)
-- — or under an sql_mode without NO_ZERO_DATE — MySQL fills a NOT NULL DATETIME
-- with 0000-00-00 00:00:00. Connector/J 9.x then refuses to read the row at
-- all ("Zero date value prohibited"), so every upload for that tenant returns
-- 500 at the quota lookup, long after the file was already written. Passing
-- NOW(6) makes the seed correct regardless of how the target schema was built.
INSERT IGNORE INTO project_storage (org_id, project_id, max_bytes, used_bytes, created_at, updated_at)
VALUES (1, 1, 2684354560, 0, NOW(6), NOW(6));

INSERT IGNORE INTO project_storage (org_id, project_id, max_bytes, used_bytes, created_at, updated_at)
VALUES (1, 2, 2684354560, 0, NOW(6), NOW(6));

INSERT IGNORE INTO project_storage (org_id, project_id, max_bytes, used_bytes, created_at, updated_at)
VALUES (1, 3, 2684354560, 0, NOW(6), NOW(6));

-- Also on the shared WABA 1436853954305849 — same quota as the rest.
INSERT IGNORE INTO project_storage (org_id, project_id, max_bytes, used_bytes, created_at, updated_at)
VALUES (1, 257, 2684354560, 0, NOW(6), NOW(6));

-- The WhatsApp template-sync project.
INSERT IGNORE INTO project_storage (org_id, project_id, max_bytes, used_bytes, created_at, updated_at)
VALUES (1, 767, 2684354560, 0, NOW(6), NOW(6));

-- ── Repair existing rows ───────────────────────────────────────────────────
-- INSERT IGNORE skips rows that already exist, so a tenant seeded by the old
-- statements keeps its zero dates and keeps failing. These two statements fix
-- them in place and are no-ops on healthy rows.
UPDATE org_storage
SET created_at = IF(CAST(created_at AS CHAR) LIKE '0000%', NOW(6), created_at),
    updated_at = IF(CAST(updated_at AS CHAR) LIKE '0000%', NOW(6), updated_at)
WHERE org_id = 1;

UPDATE project_storage
SET created_at = IF(CAST(created_at AS CHAR) LIKE '0000%', NOW(6), created_at),
    updated_at = IF(CAST(updated_at AS CHAR) LIKE '0000%', NOW(6), updated_at)
WHERE org_id = 1;

-- ── Verify ─────────────────────────────────────────────────────────────────
-- Expect 1 org row at 13421772800 (12.5 GiB) and 5 project rows (1, 2, 3, 257,
-- 767) at 2684354560 each, every timestamp a real date.
SELECT 'org' AS scope, org_id, NULL AS project_id, max_bytes, used_bytes, created_at, updated_at
FROM org_storage WHERE org_id = 1
UNION ALL
SELECT 'project', org_id, project_id, max_bytes, used_bytes, created_at, updated_at
FROM project_storage WHERE org_id = 1
ORDER BY scope DESC, project_id;

-- Must return ZERO rows. Anything here will 500 every upload for that tenant.
SELECT 'ZERO DATE FOUND' AS problem, org_id, project_id
FROM project_storage
WHERE CAST(created_at AS CHAR) LIKE '0000%' OR CAST(updated_at AS CHAR) LIKE '0000%'
UNION ALL
SELECT 'ZERO DATE FOUND', org_id, NULL
FROM org_storage
WHERE CAST(created_at AS CHAR) LIKE '0000%' OR CAST(updated_at AS CHAR) LIKE '0000%';

-- Org ceiling vs. the sum of its project ceilings. project_total must not
-- exceed org_max, or projects can fail on the org reservation while still
-- under their own limit.
SELECT o.max_bytes                        AS org_max,
       SUM(p.max_bytes)                   AS project_total,
       o.max_bytes - SUM(p.max_bytes)     AS headroom,
       IF(SUM(p.max_bytes) > o.max_bytes, 'OVERSUBSCRIBED', 'OK') AS verdict
FROM org_storage o
JOIN project_storage p ON p.org_id = o.org_id
WHERE o.org_id = 1
GROUP BY o.max_bytes;