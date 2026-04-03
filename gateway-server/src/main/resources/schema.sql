-- ============================================================
-- Distributed File Storage System - MySQL Schema
-- ============================================================

CREATE DATABASE IF NOT EXISTS dfs_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE dfs_db;

-- ============================================================
-- Table: users
-- ============================================================
CREATE TABLE users (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          ENUM('ROLE_USER', 'ROLE_ADMIN') NOT NULL DEFAULT 'ROLE_USER',
    storage_quota BIGINT UNSIGNED NOT NULL DEFAULT 10737418240, -- 10 GB default
    storage_used  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_users_email (email),
    INDEX idx_users_username (username)
) ENGINE=InnoDB;

-- ============================================================
-- Table: storage_nodes
-- ============================================================
CREATE TABLE storage_nodes (
    id              VARCHAR(50)  PRIMARY KEY,            -- e.g. "node-1"
    base_url        VARCHAR(255) NOT NULL,               -- e.g. "http://localhost:8081"
    zone            VARCHAR(50)  NOT NULL DEFAULT 'default',
    status          ENUM('ONLINE', 'OFFLINE', 'DRAINING') NOT NULL DEFAULT 'ONLINE',
    total_capacity  BIGINT UNSIGNED NOT NULL DEFAULT 0,  -- bytes
    used_capacity   BIGINT UNSIGNED NOT NULL DEFAULT 0,  -- bytes
    last_heartbeat  TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_nodes_status (status)
) ENGINE=InnoDB;

-- ============================================================
-- Table: files
-- ============================================================
CREATE TABLE files (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    owner_id       BIGINT UNSIGNED NOT NULL,
    original_name  VARCHAR(255)    NOT NULL,
    stored_name    VARCHAR(255)    NOT NULL UNIQUE,      -- UUID-based internal name
    mime_type      VARCHAR(127)    NOT NULL DEFAULT 'application/octet-stream',
    file_size      BIGINT UNSIGNED NOT NULL DEFAULT 0,   -- total bytes
    total_chunks   INT UNSIGNED    NOT NULL DEFAULT 0,
    checksum       VARCHAR(64)     NOT NULL,             -- SHA-256 of full file
    upload_status  ENUM('UPLOADING', 'COMPLETE', 'FAILED') NOT NULL DEFAULT 'UPLOADING',
    is_public      BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at     TIMESTAMP       NULL DEFAULT NULL,    -- soft-delete marker
    created_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_files_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,

    INDEX idx_files_owner        (owner_id),
    INDEX idx_files_deleted_at   (deleted_at),
    INDEX idx_files_upload_status(upload_status),
    INDEX idx_files_stored_name  (stored_name)
) ENGINE=InnoDB;

-- ============================================================
-- Table: file_chunks
-- ============================================================
CREATE TABLE file_chunks (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    file_id      BIGINT UNSIGNED NOT NULL,
    node_id      VARCHAR(50)     NOT NULL,               -- which storage node holds this copy
    chunk_order  INT UNSIGNED    NOT NULL,               -- 0-based sequence index
    chunk_size   INT UNSIGNED    NOT NULL,               -- bytes in this chunk
    checksum     VARCHAR(64)     NOT NULL,               -- SHA-256 of this chunk
    is_replica   BOOLEAN         NOT NULL DEFAULT FALSE, -- TRUE = replica copy
    storage_path VARCHAR(512)    NOT NULL,               -- absolute path on node
    created_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_chunks_file  FOREIGN KEY (file_id)  REFERENCES files(id)         ON DELETE CASCADE,
    CONSTRAINT fk_chunks_node  FOREIGN KEY (node_id)  REFERENCES storage_nodes(id) ON DELETE RESTRICT,

    -- Primary copy uniqueness: one primary chunk per (file, order)
    UNIQUE KEY uq_primary_chunk (file_id, chunk_order, is_replica),

    INDEX idx_chunks_file_order (file_id, chunk_order),
    INDEX idx_chunks_node       (node_id)
) ENGINE=InnoDB;

-- ============================================================
-- Table: access_tokens  (refresh / revocation tracking)
-- ============================================================
CREATE TABLE refresh_tokens (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT UNSIGNED NOT NULL,
    token_hash  VARCHAR(255)    NOT NULL UNIQUE,
    expires_at  TIMESTAMP       NOT NULL,
    revoked     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_rt_user    (user_id),
    INDEX idx_rt_expires (expires_at)
) ENGINE=InnoDB;

-- ============================================================
-- Seed: default storage nodes
-- ============================================================
INSERT INTO storage_nodes (id, base_url, zone, status, total_capacity)
VALUES
    ('node-1', 'http://localhost:8081', 'zone-a', 'ONLINE', 107374182400),
    ('node-2', 'http://localhost:8082', 'zone-b', 'ONLINE', 107374182400),
    ('node-3', 'http://localhost:8083', 'zone-c', 'ONLINE', 107374182400);

-- ============================================================
-- Stored Procedure: purge_expired_soft_deletes
-- Run daily via scheduler or cron
-- ============================================================
DELIMITER $$

CREATE PROCEDURE purge_expired_soft_deletes()
BEGIN
    DELETE f FROM files f
    WHERE f.deleted_at IS NOT NULL
      AND f.deleted_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
END$$

DELIMITER ;
