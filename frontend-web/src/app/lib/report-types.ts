export type ReportReason = 'ABUSIVE_LANGUAGE' | 'MONEY_REQUEST'

export type ReportResult = { ok: true; message: string } | { ok: false; message: string }
