import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { cookies } from "next/headers";
import "./globals.css";
import { SiteHeader } from "@/app/ui/site-header";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  metadataBase: new URL("http://localhost:3000"),
  title: "Project Guard",
  description: "전/월세 계약 전 등기부등본 위험 요소 분석 서비스",
  openGraph: {
    title: "Project Guard",
    description: "계약하기 전, 등기부등본부터 확인하세요",
    type: "website",
    locale: "ko_KR",
  },
  twitter: {
    card: "summary_large_image",
    title: "Project Guard",
    description: "계약하기 전, 등기부등본부터 확인하세요",
  },
};

export default async function RootLayout({ children }: LayoutProps<"/">) {
  // 세 계정 체계(회원·변호사·관리자)의 로그인 상태를 머리글에 넘긴다. 토큰 자체는 넘기지 않는다.
  const cookieStore = await cookies();
  const sessions = {
    userEmail: cookieStore.has("session") ? (cookieStore.get("session_email")?.value ?? "") : null,
    lawyerName: cookieStore.has("lawyer_session") ? (cookieStore.get("lawyer_session_name")?.value ?? "") : null,
    isAdmin: cookieStore.has("admin_session"),
  };

  return (
    <html
      lang="ko"
      className={`${geistSans.variable} ${geistMono.variable} h-full scroll-smooth antialiased`}
    >
      <body className="flex min-h-full flex-col">
        <SiteHeader sessions={sessions} />
        {children}
      </body>
    </html>
  );
}
