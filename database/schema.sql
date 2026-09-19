-- =====================================================================
-- CleanBengaluru - reference schema
--
-- You do NOT have to run this file: the application uses
-- spring.jpa.hibernate.ddl-auto=update and creates these tables itself
-- on first startup. This file exists so the design is readable, reviewable
-- and usable if you ever switch to Flyway/Liquibase migrations.
--
-- Engine InnoDB is required for foreign keys.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS cleanbengaluru
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cleanbengaluru;

-- ---------------------------------------------------------------------
-- users : citizens, workers and admins share one table, split by `role`.
-- Keeping them together means one login flow and one FK target everywhere.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL,
    password    VARCHAR(100) NOT NULL,           -- BCrypt hash, 60 chars
    phone       VARCHAR(15)  NULL,
    role        VARCHAR(20)  NOT NULL,           -- CITIZEN | WORKER | ADMIN
    area_name   VARCHAR(100) NULL,
    active      BIT(1)       NOT NULL DEFAULT b'1',
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY idx_users_email (email),
    KEY idx_users_role (role)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- garbage_reports : one row per citizen complaint.
--   user_id          -> users.id      (who reported it)
--   parent_report_id -> garbage_reports.id (self-reference: duplicate link)
-- The (latitude, longitude) index is what makes the bounding-box
-- "nearby" query fast instead of a full table scan.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS garbage_reports (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    garbage_type     VARCHAR(40)  NOT NULL,
    description      VARCHAR(500) NULL,
    before_image     VARCHAR(255) NULL,          -- file NAME only, not the bytes
    latitude         DOUBLE       NOT NULL,
    longitude        DOUBLE       NOT NULL,
    address          VARCHAR(255) NULL,
    area_name        VARCHAR(100) NULL,
    status           VARCHAR(30)  NOT NULL,
    priority         VARCHAR(15)  NOT NULL,
    severity         INT          NOT NULL DEFAULT 3,
    road_blocked     BIT(1)       NOT NULL DEFAULT b'0',
    duplicate_count  INT          NOT NULL DEFAULT 0,
    reopen_count     INT          NOT NULL DEFAULT 0,
    parent_report_id BIGINT       NULL,
    rejection_reason VARCHAR(300) NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NULL,
    resolved_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_reports_status (status),
    KEY idx_reports_lat_lon (latitude, longitude),
    KEY idx_reports_area (area_name),
    KEY idx_reports_created (created_at),
    CONSTRAINT fk_reports_user   FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reports_parent FOREIGN KEY (parent_report_id) REFERENCES garbage_reports (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- garbage_bins : physical dustbins. No FK to users - a bin belongs to an
-- area, not a person.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS garbage_bins (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    code               VARCHAR(40)  NOT NULL,
    location_name      VARCHAR(200) NOT NULL,
    latitude           DOUBLE       NOT NULL,
    longitude          DOUBLE       NOT NULL,
    bin_type           VARCHAR(20)  NOT NULL,
    capacity_litres    INT          NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    last_collection_at DATETIME(6)  NULL,
    area_name          VARCHAR(100) NULL,
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bins_code (code),
    KEY idx_bins_lat_lon (latitude, longitude),
    KEY idx_bins_status (status),
    KEY idx_bins_area (area_name)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- assignments : one cleanup task = one report given to one worker.
-- ManyToOne on report (not OneToOne) because a reopened report gets a
-- second assignment, and we keep the first one as history.
-- Holds the AFTER photo, so the citizen's BEFORE photo is never overwritten.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assignments (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    report_id     BIGINT       NOT NULL,
    worker_id     BIGINT       NOT NULL,
    assigned_by   BIGINT       NULL,
    status        VARCHAR(20)  NOT NULL,
    after_image   VARCHAR(255) NULL,
    worker_notes  VARCHAR(500) NULL,
    reject_reason VARCHAR(300) NULL,
    assigned_at   DATETIME(6)  NOT NULL,
    accepted_at   DATETIME(6)  NULL,
    started_at    DATETIME(6)  NULL,
    completed_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_assignments_worker (worker_id),
    KEY idx_assignments_report (report_id),
    KEY idx_assignments_status (status),
    CONSTRAINT fk_assignments_report FOREIGN KEY (report_id) REFERENCES garbage_reports (id),
    CONSTRAINT fk_assignments_worker FOREIGN KEY (worker_id) REFERENCES users (id),
    CONSTRAINT fk_assignments_admin  FOREIGN KEY (assigned_by) REFERENCES users (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- citizen_verifications : history of every "was this cleaned?" answer.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS citizen_verifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    report_id   BIGINT       NOT NULL,
    citizen_id  BIGINT       NOT NULL,
    cleaned     BIT(1)       NOT NULL,
    reason      VARCHAR(400) NULL,
    proof_image VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_verifications_report (report_id),
    CONSTRAINT fk_verifications_report  FOREIGN KEY (report_id) REFERENCES garbage_reports (id),
    CONSTRAINT fk_verifications_citizen FOREIGN KEY (citizen_id) REFERENCES users (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- notifications : in-app messages. report_id is a plain column, not a FK,
-- so a notification survives if its report is ever purged.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(120) NOT NULL,
    message    VARCHAR(400) NOT NULL,
    report_id  BIGINT       NULL,
    is_read    BIT(1)       NOT NULL DEFAULT b'0',
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_user (user_id),
    KEY idx_notifications_read (is_read),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

-- =====================================================================
-- RELATIONSHIP SUMMARY
--
--   users 1 ────< N garbage_reports          (a citizen files many reports)
--   users 1 ────< N assignments              (a worker is given many tasks)
--   garbage_reports 1 ──< N assignments      (reopened report -> second task)
--   garbage_reports 1 ──< N citizen_verifications
--   garbage_reports 1 ──< N garbage_reports  (self: duplicate -> parent)
--   users 1 ────< N notifications
--   garbage_bins : standalone, linked to reports only by area/geography
--
-- WHY NO SEPARATE `workers` TABLE: a worker is a user with role=WORKER.
-- A second table would duplicate name/email/password and force a join on
-- every login. Worker-specific data lives on `assignments`.
--
-- WHY NO SEPARATE `report_duplicates` TABLE: the link is 1-to-1 from child
-- to parent, so a self-referencing FK column expresses it with no extra join.
-- =====================================================================
