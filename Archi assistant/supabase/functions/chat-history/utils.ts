export function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

export function normalizeRole(role: unknown) {
  if (typeof role !== "string") {
    return "user";
  }
  const lowered = role.trim().toLowerCase();
  if (lowered === "assistant") {
    return "assistant";
  }
  if (lowered === "user") {
    return "user";
  }
  if (lowered === "ai") {
    return "assistant";
  }
  return "user";
}

export function extractAccessToken(authHeader: string | null, payload: Record<string, unknown>) {
  if (authHeader) {
    const trimmed = authHeader.trim();
    if (trimmed) {
      return trimmed.toLowerCase().startsWith("bearer ")
        ? trimmed.slice(7).trim()
        : trimmed;
    }
  }
  const token = payload && typeof payload.access_token === "string"
    ? payload.access_token.trim()
    : "";
  return token || null;
}

function decodeBase64Url(value: string) {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/")
    .padEnd(Math.ceil(value.length / 4) * 4, "=");
  return atob(padded);
}

export function decodeJwtPayload(token: string) {
  const parts = token.split(".");
  if (parts.length < 2) {
    return null;
  }
  try {
    const json = decodeBase64Url(parts[1]);
    return JSON.parse(json) as Record<string, unknown>;
  } catch (_err) {
    return null;
  }
}

export function getUserIdFromJwt(token: string) {
  const payload = decodeJwtPayload(token);
  return payload && typeof payload.sub === "string" ? payload.sub : null;
}
