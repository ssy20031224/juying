import { clearSessionCookie, revokeCurrentSession } from "../../../lib/auth";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
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
