import { assertEquals } from "https://deno.land/std@0.206.0/assert/mod.ts";
import { clamp, decodeJwtPayload, extractAccessToken, getUserIdFromJwt, normalizeRole } from "./utils.ts";

Deno.test("clamp enforces min and max", () => {
  assertEquals(clamp(0, 1, 5), 1);
  assertEquals(clamp(3, 1, 5), 3);
  assertEquals(clamp(9, 1, 5), 5);
});

Deno.test("normalizeRole accepts assistant synonyms", () => {
  assertEquals(normalizeRole("assistant"), "assistant");
  assertEquals(normalizeRole("AI"), "assistant");
});

Deno.test("normalizeRole defaults to user", () => {
  assertEquals(normalizeRole("system"), "user");
  assertEquals(normalizeRole(null), "user");
});

Deno.test("extractAccessToken prioritizes Authorization header", () => {
  assertEquals(extractAccessToken("Bearer abc", {}), "abc");
  assertEquals(extractAccessToken("xyz", {}), "xyz");
});

Deno.test("extractAccessToken falls back to payload token", () => {
  assertEquals(extractAccessToken(null, { access_token: "tok" }), "tok");
  assertEquals(extractAccessToken("", { access_token: " tok " }), "tok");
  assertEquals(extractAccessToken("", {}), null);
});

Deno.test("decodeJwtPayload parses base64url payload", () => {
  const token = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ1c2VyLTEyMyJ9.sig";
  const payload = decodeJwtPayload(token) || {};
  assertEquals(payload.sub, "user-123");
});

Deno.test("getUserIdFromJwt returns sub", () => {
  const token = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhYmMifQ.sig";
  assertEquals(getUserIdFromJwt(token), "abc");
  assertEquals(getUserIdFromJwt("invalid"), null);
});
