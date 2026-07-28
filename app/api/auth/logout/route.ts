import { ACCOUNT_AUTH_ENABLED, accountAuthDisabledResponse, clearSessionCookie, revokeCurrentSession } from "../../../lib/auth";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  if (!ACCOUNT_AUTH_ENABLED) return accountAuthDisabledResponse();
  await revokeCurrentSession(request);
  return new Response(JSON.stringify({ ok: true }), {
    status: 200,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Set-Cookie": clearSessionCookie(),
      "Cache-Control": "no-store",
    },
  });
}
