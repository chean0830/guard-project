export type BlockResult = { ok: true } | { ok: false; message: string }

export type BlockedEntry = { id: number; name: string; detail: string | null; blockedAt: string }
