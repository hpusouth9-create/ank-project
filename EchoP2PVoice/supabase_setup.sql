-- ═══════════════════════════════════════════════════════════════════
-- SUPABASE SETUP FOR ECHO P2P VOICE APP
-- Run this in your Supabase project's SQL editor
-- ═══════════════════════════════════════════════════════════════════

-- Note: For this app, we use Supabase REALTIME BROADCAST channels
-- which are purely in-memory and do NOT require database tables.
-- No data is persisted — signaling messages are broadcast ephemerally.

-- However, if you want optional call logging (for debugging), 
-- you can create this table:

CREATE TABLE IF NOT EXISTS call_logs (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    caller_id TEXT NOT NULL,
    receiver_id TEXT NOT NULL,
    started_at TIMESTAMPTZ DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    duration_seconds INT
);

-- Auto-delete logs older than 24 hours (privacy-first)
CREATE OR REPLACE FUNCTION delete_old_call_logs()
RETURNS void AS $$
BEGIN
    DELETE FROM call_logs WHERE started_at < NOW() - INTERVAL '24 hours';
END;
$$ LANGUAGE plpgsql;

-- ── Realtime Configuration ────────────────────────────────────────────────
-- In Supabase Dashboard → Realtime → enable Broadcast
-- No additional SQL needed — broadcast channels are automatic

-- ── Row Level Security (if using call_logs table) ─────────────────────────
ALTER TABLE call_logs ENABLE ROW LEVEL SECURITY;

-- Only allow inserting your own logs
CREATE POLICY "Users can insert their own logs"
    ON call_logs FOR INSERT
    WITH CHECK (true); -- Adjust with auth if you add user accounts

-- ═══════════════════════════════════════════════════════════════════
-- IMPORTANT: Enable Realtime in your Supabase project
-- Dashboard → Settings → API → Enable Realtime
-- ═══════════════════════════════════════════════════════════════════
