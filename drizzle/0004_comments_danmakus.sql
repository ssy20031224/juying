ALTER TABLE `comments` ADD `parent_id` text;--> statement-breakpoint
ALTER TABLE `comments` ADD `reply_to_nick` text DEFAULT '' NOT NULL;--> statement-breakpoint
CREATE INDEX `comments_parent_idx` ON `comments` (`parent_id`);--> statement-breakpoint
CREATE TABLE `comment_likes` (
	`user_id` text NOT NULL,
	`comment_id` text NOT NULL,
	`created_at` integer DEFAULT (unixepoch()) NOT NULL,
	PRIMARY KEY(`user_id`,`comment_id`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`comment_id`) REFERENCES `comments`(`id`) ON UPDATE no action ON DELETE cascade
);--> statement-breakpoint
CREATE INDEX `comment_likes_comment_idx` ON `comment_likes` (`comment_id`);--> statement-breakpoint
CREATE TABLE `danmakus` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`media_key` text NOT NULL,
	`episode_key` text NOT NULL,
	`position_ms` integer DEFAULT 0 NOT NULL,
	`text` text NOT NULL,
	`color` text DEFAULT '#FFFFFFFF' NOT NULL,
	`created_at` integer DEFAULT (unixepoch()) NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);--> statement-breakpoint
CREATE INDEX `danmakus_media_episode_idx` ON `danmakus` (`media_key`,`episode_key`,`position_ms`);
