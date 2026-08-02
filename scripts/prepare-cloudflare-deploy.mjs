import { readFile, writeFile } from "node:fs/promises";

const configPath = new URL("../dist/server/wrangler.json", import.meta.url);
const workerName = process.env.CLOUDFLARE_WORKER_NAME?.trim() || "juying-api";
const databaseName = process.env.CLOUDFLARE_D1_DATABASE_NAME?.trim() || "juying-prod";
const databaseId =
  process.env.CLOUDFLARE_D1_DATABASE_ID?.trim() ||
  "ad3e0fc5-ef3f-487c-b1e3-d1c7fa8b9764";

const config = JSON.parse(await readFile(configPath, "utf8"));
const database = config.d1_databases?.find((item) => item.binding === "DB");

if (!database) {
  throw new Error("The production build does not contain the required DB binding");
}

config.name = workerName;
config.topLevelName = workerName;
config.keep_vars = true;
config.preview_urls = false;
config.vars = {
  ...config.vars,
  ACCOUNT_AUTH_ENABLED: "true",
  COMMENTS_POSTING_ENABLED: "true",
  COMMENTS_REQUIRE_ACCOUNT: "true",
};
database.database_name = databaseName;
database.database_id = databaseId;

await writeFile(configPath, `${JSON.stringify(config, null, 2)}\n`, "utf8");
console.log(`Prepared ${workerName} with D1 database ${databaseName}.`);
