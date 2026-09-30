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

// 보안 헤더. 다른 사이트가 우리 화면을 몰래 틀(iframe)에 넣어 클릭을 유도하는 공격(클릭재킹), 파일 형식 추측 공격을
// 막고, 다른 사이트로 넘어갈 때 주소 전체(토큰이 담길 수 있는 쿼리 포함)가 새지 않게 한다.
// 토스 결제창은 "우리 화면 안에" 토스 틀을 여는 것이라 frame-ancestors 제한과 충돌하지 않는다.
const SECURITY_HEADERS = [
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Content-Security-Policy", value: "frame-ancestors 'none'" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=(self)" },
  ...(process.env.NODE_ENV === "production"
    ? [{ key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" }]
    : []),
];

const nextConfig: NextConfig = {
  async headers() {
    return [{ source: "/:path*", headers: SECURITY_HEADERS }];
  },
};

export default nextConfig;
