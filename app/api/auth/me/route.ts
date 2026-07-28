import { ACCOUNT_AUTH_ENABLED, accountAuthDisabledResponse, getCurrentUser, publicUser } from "../../../lib/auth";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  if (!ACCOUNT_AUTH_ENABLED) return accountAuthDisabledResponse();
  const user = await getCurrentUser(request);
  if (!user) return Response.json({ user: null }, { status: 200, headers: { "Cache-Control": "no-store" } });
  return Response.json({ user: publicUser(user) }, { headers: { "Cache-Control": "no-store" } });
}
