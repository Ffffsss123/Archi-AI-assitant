import { createClient } from "npm:@supabase/supabase-js@2.92.0";
import { corsHeaders } from "../_shared/cors.ts";
import { clamp, extractAccessToken, normalizeRole } from "./utils.ts";

type Json = Record<string, unknown>;

function jsonResponse(status: number, body: Json) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse(405, { error: "Method not allowed." });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const supabaseKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  if (!supabaseUrl || !supabaseKey) {
    return jsonResponse(500, { error: "Supabase config missing." });
  }

  let payload: Json = {};
  try {
    payload = (await req.json()) as Json;
  } catch (_err) {
    payload = {};
  }

  const action = String(payload.action || "");
  const token = extractAccessToken(req.headers.get("Authorization"), payload);
  if (!token) {
    return jsonResponse(401, { error: "Unauthorized." });
  }

  const authClient = createClient(supabaseUrl, supabaseKey, {
    auth: { persistSession: false },
  });

  const {
    data: { user },
    error: authError,
  } = await authClient.auth.getUser(token);

  if (authError || !user) {
    return jsonResponse(401, { error: "Unauthorized." });
  }

  const userId = user.id;

  const supabase = createClient(supabaseUrl, supabaseKey, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { persistSession: false },
  });

  if (action === "list_sessions") {
    const limit = clamp(Number(payload.limit ?? 15), 1, 50);
    const cursor = typeof payload.cursor === "string" ? payload.cursor : null;
    let query = supabase
      .from("chat_sessions")
      .select("id,title,created_at,last_message_at")
      .eq("user_id", userId)
      .order("created_at", { ascending: false })
      .limit(limit + 1);
    if (cursor) {
      query = query.lt("created_at", cursor);
    }
    const { data, error } = await query;
    if (error) {
      return jsonResponse(500, { error: error.message });
    }
    const sessions = data ?? [];
    const hasMore = sessions.length > limit;
    const page = hasMore ? sessions.slice(0, limit) : sessions;
    const nextCursor = page.length ? page[page.length - 1].created_at : null;
    return jsonResponse(200, {
      sessions: page,
      next_cursor: nextCursor,
      has_more: hasMore,
    });
  }

  if (action === "create_session") {
    const title = typeof payload.title === "string" ? payload.title.trim() : "";
    const { data, error } = await supabase
      .from("chat_sessions")
      .insert({
        user_id: userId,
        title: title || null,
      })
      .select("id,title,created_at,last_message_at")
      .single();
    if (error) {
      return jsonResponse(500, { error: error.message });
    }
    return jsonResponse(200, { session: data });
  }

  if (action === "get_messages") {
    const sessionId = typeof payload.session_id === "string" ? payload.session_id : "";
    if (!sessionId) {
      return jsonResponse(400, { error: "Missing session_id." });
    }
    const { data, error } = await supabase
      .from("chat_messages")
      .select("id,session_id,role,content,created_at")
      .eq("session_id", sessionId)
      .eq("user_id", userId)
      .order("created_at", { ascending: true });
    if (error) {
      return jsonResponse(500, { error: error.message });
    }
    return jsonResponse(200, { messages: data ?? [] });
  }

  if (action === "append_message") {
    const sessionId = typeof payload.session_id === "string" ? payload.session_id : "";
    const content = typeof payload.content === "string" ? payload.content.trim() : "";
    if (!sessionId || !content) {
      return jsonResponse(400, { error: "Missing session_id or content." });
    }
    const role = normalizeRole(payload.role);
    const { data, error } = await supabase
      .from("chat_messages")
      .insert({
        session_id: sessionId,
        user_id: userId,
        role,
        content,
      })
      .select("id,session_id,role,content,created_at")
      .single();
    if (error) {
      return jsonResponse(500, { error: error.message });
    }
    const { error: updateError } = await supabase
      .from("chat_sessions")
      .update({ last_message_at: new Date().toISOString() })
      .eq("id", sessionId)
      .eq("user_id", userId);
    if (updateError) {
      return jsonResponse(500, { error: updateError.message });
    }
    return jsonResponse(200, { message: data });
  }

  if (action === "rename_session") {
    const sessionId = typeof payload.session_id === "string" ? payload.session_id : "";
    const title = typeof payload.title === "string" ? payload.title.trim() : "";
    if (!sessionId || !title) {
      return jsonResponse(400, { error: "Missing session_id or title." });
    }
    const { data, error } = await supabase
      .from("chat_sessions")
      .update({ title })
      .eq("id", sessionId)
      .eq("user_id", userId)
      .select("id,title,created_at,last_message_at")
      .single();
    if (error) {
      return jsonResponse(500, { error: error.message });
    }
    return jsonResponse(200, { session: data });
  }

  if (action === "delete_session") {
    const sessionId = typeof payload.session_id === "string" ? payload.session_id : "";
    if (!sessionId) {
      return jsonResponse(400, { error: "Missing session_id." });
    }
    const { error } = await supabase
      .from("chat_sessions")
      .delete()
      .eq("id", sessionId)
      .eq("user_id", userId);
    if (error) {
      return jsonResponse(500, { error: error.message });
    }
    return jsonResponse(200, { deleted: true, session_id: sessionId });
  }

  return jsonResponse(400, { error: "Unsupported action." });
});
