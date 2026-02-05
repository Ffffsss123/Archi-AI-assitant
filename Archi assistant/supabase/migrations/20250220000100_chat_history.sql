create extension if not exists "pgcrypto";

create table if not exists public.chat_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  title text,
  created_at timestamptz not null default now(),
  last_message_at timestamptz not null default now()
);

create table if not exists public.chat_messages (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.chat_sessions(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null,
  content text not null,
  created_at timestamptz not null default now()
);

create index if not exists chat_sessions_user_created_at_idx
  on public.chat_sessions(user_id, created_at desc);

create index if not exists chat_messages_session_created_at_idx
  on public.chat_messages(session_id, created_at asc);

create index if not exists chat_messages_user_idx
  on public.chat_messages(user_id);

alter table public.chat_sessions enable row level security;
alter table public.chat_messages enable row level security;

create policy "Users can manage their sessions"
  on public.chat_sessions
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

create policy "Users can manage their messages"
  on public.chat_messages
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
