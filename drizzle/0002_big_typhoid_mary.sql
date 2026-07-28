CREATE TABLE `verification_codes` (
	`id` text PRIMARY KEY NOT NULL,
	`email` text NOT NULL,
	`purpose` text NOT NULL,
	`code_hash` text NOT NULL,
	`expires_at` integer NOT NULL,
	`consumed_at` integer,
	`created_at` integer DEFAULT (unixepoch()) NOT NULL
);
--> statement-breakpoint
CREATE INDEX `verification_codes_email_purpose_idx` ON `verification_codes` (`email`,`purpose`);--> statement-breakpoint
CREATE INDEX `verification_codes_expires_idx` ON `verification_codes` (`expires_at`);--> statement-breakpoint
ALTER TABLE `users` ADD `avatar_url` text DEFAULT '' NOT NULL;