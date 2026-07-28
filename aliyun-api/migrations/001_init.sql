CREATE DATABASE IF NOT EXISTS lanerc
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE lanerc;

CREATE TABLE IF NOT EXISTS users (
  id CHAR(36) PRIMARY KEY,
  email VARCHAR(320) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(24) NOT NULL,
  avatar_url VARCHAR(1024) NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  UNIQUE KEY users_email_unique (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS auth_sessions (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expires_at BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  UNIQUE KEY auth_sessions_token_unique (token_hash),
  KEY auth_sessions_user_idx (user_id),
  KEY auth_sessions_expires_idx (expires_at),
  CONSTRAINT auth_sessions_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS verification_codes (
  id CHAR(36) PRIMARY KEY,
  email VARCHAR(320) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  code_hash VARCHAR(64) NOT NULL,
  expires_at BIGINT NOT NULL,
  consumed_at BIGINT NULL,
  created_at BIGINT NOT NULL,
  KEY verification_codes_email_purpose_idx (email, purpose),
  KEY verification_codes_expires_idx (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS favorites (
  user_id CHAR(36) NOT NULL,
  media_key VARCHAR(180) NOT NULL,
  media_snapshot JSON NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (user_id, media_key),
  KEY favorites_user_updated_idx (user_id, updated_at),
  CONSTRAINT favorites_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS watch_progress (
  user_id CHAR(36) NOT NULL,
  media_key VARCHAR(180) NOT NULL,
  episode_key VARCHAR(180) NOT NULL,
  media_snapshot JSON NOT NULL,
  episode_name VARCHAR(180) NOT NULL DEFAULT '',
  source_key VARCHAR(120) NOT NULL DEFAULT '',
  position_ms BIGINT NOT NULL DEFAULT 0,
  duration_ms BIGINT NOT NULL DEFAULT 0,
  completed TINYINT(1) NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (user_id, media_key, episode_key),
  KEY watch_progress_user_updated_idx (user_id, updated_at),
  CONSTRAINT watch_progress_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS comments (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  media_key VARCHAR(180) NOT NULL,
  episode_key VARCHAR(180) NOT NULL DEFAULT '',
  text VARCHAR(200) NOT NULL,
  created_at BIGINT NOT NULL,
  KEY comments_media_created_idx (media_key, created_at),
  KEY comments_user_created_idx (user_id, created_at),
  CONSTRAINT comments_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS device_cache_items (
  user_id CHAR(36) NOT NULL,
  device_id VARCHAR(120) NOT NULL,
  media_key VARCHAR(180) NOT NULL,
  episode_key VARCHAR(180) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'downloaded',
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (user_id, device_id, media_key, episode_key),
  KEY device_cache_user_updated_idx (user_id, updated_at),
  CONSTRAINT device_cache_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
