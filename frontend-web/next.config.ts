import fs from "node:fs";
import path from "node:path";
import type { NextConfig } from "next";

/**
 * 이 프로젝트 전용이어야 하는 키는 .env.local에 적힌 값만 쓴다.
 * Next.js는 OS 환경변수가 .env.local보다 우선이라, 이 PC에 다른 프로젝트(Festlog)용으로 남아 있던
 * GOOGLE_/KAKAO_/NAVER_CLIENT_* 가 그대로 섞여 들어와 소셜 로그인이 Festlog 앱으로 연결되는 일이 실제로
 * 있었다(네이버: "FESTLOG에 로그인할 수 없습니다"). 백엔드 BackendApplication의
 * SENSITIVE_KEYS_REQUIRING_EXPLICIT_ENV와 같은 원칙 — .env.local에 없으면 OS 값이 있어도 비운다.
 */
const PROJECT_ONLY_KEYS = [
  "GOOGLE_CLIENT_ID",
  "GOOGLE_CLIENT_SECRET",
  "KAKAO_CLIENT_ID",
  "KAKAO_CLIENT_SECRET",
  "NAVER_CLIENT_ID",
  "NAVER_CLIENT_SECRET",
  "OAUTH_BASE_URL",
  "INTERNAL_SYNC_SECRET",
  "NEXT_PUBLIC_TOSS_CLIENT_KEY",
];

function readEnvLocal(): Record<string, string> {
  const file = path.join(process.cwd(), ".env.local");
  if (!fs.existsSync(file)) return {};
  const entries: Record<string, string> = {};
  for (const raw of fs.readFileSync(file, "utf-8").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#") || !line.includes("=")) continue;
    const index = line.indexOf("=");
    entries[line.slice(0, index).trim()] = line.slice(index + 1).trim().replace(/^(['"])(.*)\1$/, "$2");
  }
  return entries;
}

const envLocal = readEnvLocal();
for (const key of PROJECT_ONLY_KEYS) {
  if (envLocal[key]) {
    process.env[key] = envLocal[key];
  } else {
    delete process.env[key];
  }
}

const nextConfig: NextConfig = {
  /* config options here */
};

export default nextConfig;
