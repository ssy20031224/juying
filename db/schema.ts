import { sql } from "drizzle-orm";
import {
  index,
  integer,
  primaryKey,
  sqliteTable,
  text,
  uniqueIndex,
} from "drizzle-orm/sqlite-core";

const now = () => sql`(unixepoch())`;

export const users = sqliteTable(
  "users",
  {
    id: text("id").primaryKey(),
    email: text("email").notNull(),
    passwordHash: text("password_hash").notNull(),
    nickname: text("nickname").notNull(),
    avatarUrl: text("avatar_url").notNull().default(""),
    createdAt: integer("created_at").notNull().default(now()),
    updatedAt: integer("updated_at").notNull().default(now()),
  },
  (table) => [uniqueIndex("users_email_unique").on(table.email)],
);

export const authSessions = sqliteTable(
  "auth_sessions",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    tokenHash: text("token_hash").notNull(),
    expiresAt: integer("expires_at").notNull(),
    createdAt: integer("created_at").notNull().default(now()),
  },
  (table) => [
    uniqueIndex("auth_sessions_token_unique").on(table.tokenHash),
    index("auth_sessions_user_idx").on(table.userId),
    index("auth_sessions_expires_idx").on(table.expiresAt),
  ],
);

export const verificationCodes = sqliteTable(
  "verification_codes",
  {
    id: text("id").primaryKey(),
    email: text("email").notNull(),
    purpose: text("purpose").notNull(),
    codeHash: text("code_hash").notNull(),
    expiresAt: integer("expires_at").notNull(),
    consumedAt: integer("consumed_at"),
    createdAt: integer("created_at").notNull().default(now()),
  },
  (table) => [
    index("verification_codes_email_purpose_idx").on(table.email, table.purpose),
    index("verification_codes_expires_idx").on(table.expiresAt),
  ],
);

export const favorites = sqliteTable(
  "favorites",
  {
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    mediaKey: text("media_key").notNull(),
    mediaSnapshot: text("media_snapshot").notNull().default("{}"),
    createdAt: integer("created_at").notNull().default(now()),
    updatedAt: integer("updated_at").notNull().default(now()),
  },
  (table) => [
    primaryKey({ columns: [table.userId, table.mediaKey] }),
    index("favorites_user_updated_idx").on(table.userId, table.updatedAt),
  ],
);

export const watchProgress = sqliteTable(
  "watch_progress",
  {
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    mediaKey: text("media_key").notNull(),
    episodeKey: text("episode_key").notNull(),
    mediaSnapshot: text("media_snapshot").notNull().default("{}"),
    episodeName: text("episode_name").notNull().default(""),
    sourceKey: text("source_key").notNull().default(""),
    positionMs: integer("position_ms").notNull().default(0),
    durationMs: integer("duration_ms").notNull().default(0),
    completed: integer("completed", { mode: "boolean" }).notNull().default(false),
    updatedAt: integer("updated_at").notNull().default(now()),
  },
  (table) => [
    primaryKey({ columns: [table.userId, table.mediaKey, table.episodeKey] }),
    index("watch_progress_user_updated_idx").on(table.userId, table.updatedAt),
  ],
);

export const comments = sqliteTable(
  "comments",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    mediaKey: text("media_key").notNull(),
    episodeKey: text("episode_key").notNull().default(""),
    text: text("text").notNull(),
    createdAt: integer("created_at").notNull().default(now()),
  },
  (table) => [
    index("comments_media_created_idx").on(table.mediaKey, table.createdAt),
    index("comments_user_created_idx").on(table.userId, table.createdAt),
  ],
);

export const feedback = sqliteTable(
  "feedback",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    category: text("category").notNull().default("suggestion"),
    text: text("text").notNull(),
    appVersion: text("app_version").notNull().default(""),
    device: text("device").notNull().default(""),
    createdAt: integer("created_at").notNull().default(now()),
  },
  (table) => [index("feedback_user_created_idx").on(table.userId, table.createdAt)],
);

export const deviceCacheItems = sqliteTable(
  "device_cache_items",
  {
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    deviceId: text("device_id").notNull(),
    mediaKey: text("media_key").notNull(),
    episodeKey: text("episode_key").notNull(),
    status: text("status").notNull().default("downloaded"),
    updatedAt: integer("updated_at").notNull().default(now()),
  },
  (table) => [
    primaryKey({ columns: [table.userId, table.deviceId, table.mediaKey, table.episodeKey] }),
    index("device_cache_user_updated_idx").on(table.userId, table.updatedAt),
  ],
);
