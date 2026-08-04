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
    deviceName: text("device_name").notNull().default(""),
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
    imageUrl: text("image_url").notNull().default(""),
    parentId: text("parent_id"),
    replyToNick: text("reply_to_nick").notNull().default(""),
    createdAt: integer("created_at").notNull().default(now()),
  },
  (table) => [
    index("comments_media_created_idx").on(table.mediaKey, table.createdAt),
    index("comments_user_created_idx").on(table.userId, table.createdAt),
    index("comments_parent_idx").on(table.parentId),
  ],
);

export const commentLikes = sqliteTable(
  "comment_likes",
  {
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    commentId: text("comment_id")
      .notNull()
      .references(() => comments.id, { onDelete: "cascade" }),
    createdAt: integer("created_at").notNull().default(now()),
  },
  (table) => [
    primaryKey({ columns: [table.userId, table.commentId] }),
    index("comment_likes_comment_idx").on(table.commentId),
  ],
);

export const notifications = sqliteTable(
  "notifications",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    type: text("type").notNull().default("favorite_update"),
    title: text("title").notNull().default(""),
    body: text("body").notNull().default(""),
    mediaKey: text("media_key").notNull().default(""),
    episodeName: text("episode_name").notNull().default(""),
    commentId: text("comment_id").notNull().default(""),
    mediaSnapshot: text("media_snapshot").notNull().default("{}"),
    read: integer("read", { mode: "boolean" }).notNull().default(false),
    createdAt: integer("created_at").notNull().default(now()),
  },
  (table) => [
    index("notifications_user_created_idx").on(table.userId, table.createdAt),
  ],
);

export const danmakus = sqliteTable(
  "danmakus",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    mediaKey: text("media_key").notNull(),
    episodeKey: text("episode_key").notNull(),
    positionMs: integer("position_ms").notNull().default(0),
    text: text("text").notNull(),
    color: text("color").notNull().default("#FFFFFFFF"),
    createdAt: integer("created_at").notNull().default(now()),
  },
  (table) => [
    index("danmakus_media_episode_idx").on(table.mediaKey, table.episodeKey, table.positionMs),
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
