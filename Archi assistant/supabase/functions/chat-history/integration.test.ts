import { assert, assertEquals } from "https://deno.land/std@0.206.0/assert/mod.ts";
import { createClient } from "npm:@supabase/supabase-js@2.92.0";

const FUNCTION_NAME = "chat-history";

type TestConfig = {
  email: string;
  password: string;
  url: string;
  anonKey: string;
  missing: string[];
};

function firstEnv(...keys: string[]) {
  for (const key of keys) {
    const value = Deno.env.get(key);
    if (value && value.trim()) {
      return value.trim();
    }
  }
  return "";
}

async function resolveSupabaseConfig() {
  let url = firstEnv("SUPABASE_URL");
  let anonKey = firstEnv("SUPABASE_ANON_KEY");
  if (url && anonKey) {
    return { url, anonKey };
  }
  try {
    const configUrl = new URL("../../../genai-ui/config.js", import.meta.url);
    const text = await Deno.readTextFile(configUrl);
    if (!url) {
      const match = text.match(/url:\s*"([^"]+)"/);
      if (match) {
        url = match[1];
      }
    }
    if (!anonKey) {
      const match = text.match(/anonKey:\s*"([^"]+)"/);
      if (match) {
        anonKey = match[1];
      }
    }
  } catch {
    // Leave config empty if we cannot read defaults.
  }
  return { url, anonKey };
}

async function loadTestConfig(): Promise<TestConfig> {
  const email = firstEnv("SUPABASE_EMAIL", "SUPABASE_USERNAME", "SUPABASE_USER");
  const password = firstEnv("SUPABASE_PASSWORD");
  const { url, anonKey } = await resolveSupabaseConfig();
  const missing: string[] = [];
  if (!email) missing.push("SUPABASE_EMAIL/USERNAME");
  if (!password) missing.push("SUPABASE_PASSWORD");
  if (!url) missing.push("SUPABASE_URL");
  if (!anonKey) missing.push("SUPABASE_ANON_KEY");
  return { email, password, url, anonKey, missing };
}

function createSupabaseClient(url: string, anonKey: string) {
  return createClient(url, anonKey, {
    auth: {
      persistSession: false,
      autoRefreshToken: false,
      detectSessionInUrl: false,
    },
  });
}

async function signIn(client: ReturnType<typeof createSupabaseClient>, email: string, password: string) {
  const signIn = await client.auth.signInWithPassword({ email, password });
  if (signIn.error) {
    throw new Error(`Supabase sign-in failed: ${signIn.error.message}`);
  }
  const token = signIn.data?.session?.access_token || "";
  assert(token, "Missing access token from Supabase auth.");
  return { token, user: signIn.data?.user || null };
}

function logSkip(missing: string[]) {
  console.warn(`Skipping integration test: missing ${missing.join(", ")}.`);
}

Deno.test("supabase auth sign-in works", async () => {
  const config = await loadTestConfig();
  if (config.missing.length) {
    logSkip(config.missing);
    return;
  }

  const client = createSupabaseClient(config.url, config.anonKey);
  try {
    const { user, token } = await signIn(client, config.email, config.password);
    const userEmail = user?.email ? user.email.toLowerCase() : "";
    assertEquals(userEmail, config.email.toLowerCase());
    assert(token);
  } finally {
    try {
      await client.auth.signOut();
    } catch {
      // Ignore sign-out errors to avoid masking test failures.
    }
  }
});

Deno.test("chat-history function round-trip", async () => {
  const config = await loadTestConfig();
  if (config.missing.length) {
    logSkip(config.missing);
    return;
  }

  const client = createSupabaseClient(config.url, config.anonKey);

  const { token } = await signIn(client, config.email, config.password);

  if (!token) {
    console.warn(
      "Skipping integration test: missing access token after Supabase auth.",
    );
    return;
  }

  const invoke = async (body: Record<string, unknown>) => {
    const { data, error } = await client.functions.invoke(FUNCTION_NAME, {
      body,
      headers: {
        Authorization: `Bearer ${token}`,
        apikey: config.anonKey,
      },
    });
    if (error) {
      throw new Error(error.message || String(error));
    }
    return data as Record<string, unknown>;
  };

  let sessionId = "";
  try {
    const created = await invoke({
      action: "create_session",
      title: "CI chat-history test",
    });
    const createdSession = (created as { session?: { id?: string } }).session;
    sessionId = typeof createdSession?.id === "string" ? createdSession.id : "";
    assert(sessionId, "Expected session id from create_session.");

    const appended = await invoke({
      action: "append_message",
      session_id: sessionId,
      role: "user",
      content: "hello from ci",
    });
    const appendedMessage = (appended as { message?: { session_id?: string } })
      .message;
    assertEquals(appendedMessage?.session_id, sessionId);

    const gotMessages = await invoke({
      action: "get_messages",
      session_id: sessionId,
    });
    const messages = (gotMessages as { messages?: unknown[] }).messages || [];
    assert(Array.isArray(messages));
    assert(messages.length >= 1);

    const renamed = await invoke({
      action: "rename_session",
      session_id: sessionId,
      title: "CI chat-history test renamed",
    });
    const renamedSession = (renamed as { session?: { title?: string } }).session;
    assertEquals(renamedSession?.title, "CI chat-history test renamed");

    const listed = await invoke({
      action: "list_sessions",
      limit: 20,
    });
    const sessions = (listed as { sessions?: Array<{ id?: string }> }).sessions ||
      [];
    const ids = sessions.map((session) => session?.id).filter(Boolean);
    assert(ids.includes(sessionId));
  } finally {
    if (sessionId) {
      try {
        await invoke({ action: "delete_session", session_id: sessionId });
      } catch (err) {
        console.warn("Cleanup failed:", err);
      }
    }
    try {
      await client.auth.signOut();
    } catch {
      // Ignore sign-out errors to avoid masking test failures.
    }
  }
});
