<div align="center">

# 🛡️ Project Guard

**등기부등본을 올리면 위험 신호를 근거와 함께 알려주는 전·월세 사기 예방 서비스**

<br>

<img src="docs/badges/java-25-000000.svg" />
<img src="docs/badges/spring-boot-4-6db33f.svg" />
<img src="docs/badges/jpa-59666c.svg" />
<img src="docs/badges/websocket-010101.svg" />
<img src="docs/badges/pdfbox-3-d22128.svg" />
<img src="docs/badges/google-cloud-vision-4285f4.svg" />
<br>
<img src="docs/badges/next-js-16-000000.svg" />
<img src="docs/badges/react-19-61dafb.svg" />
<img src="docs/badges/typescript-3178c6.svg" />
<img src="docs/badges/tailwind-css-4-06b6d4.svg" />
<img src="docs/badges/playwright-2ead33.svg" />
<br>
<img src="docs/badges/flutter-02569b.svg" />
<img src="docs/badges/firebase-fcm-dd2c00.svg" />
<img src="docs/badges/toss-payments-0064ff.svg" />

</div>

<br>

## 📑 프로젝트 포트폴리오

<div align="center">
  <img src="docs/portfolio/slide-01.jpg" width="800" />
</div>

<div align="center">

[📥 포트폴리오 PDF 내려받기](docs/portfolio/ProjectGuard_Portfolio.pdf)

</div>

<details>
<summary><b>01. 프로젝트 개요</b></summary>
<br>

<img src="docs/portfolio/slide-02.jpg" width="800" />
<img src="docs/portfolio/slide-03.jpg" width="800" />
<img src="docs/portfolio/slide-04.jpg" width="800" />
<img src="docs/portfolio/slide-05.jpg" width="800" />
<img src="docs/portfolio/slide-06.jpg" width="800" />

</details>

<details>
<summary><b>02. 설계와 기술</b></summary>
<br>

<img src="docs/portfolio/slide-07.jpg" width="800" />
<img src="docs/portfolio/slide-08.jpg" width="800" />
<img src="docs/portfolio/slide-09.jpg" width="800" />
<img src="docs/portfolio/slide-10.jpg" width="800" />

</details>

<details>
<summary><b>03. 핵심 기능</b></summary>
<br>

<img src="docs/portfolio/slide-11.jpg" width="800" />
<img src="docs/portfolio/slide-12.jpg" width="800" />
<img src="docs/portfolio/slide-13.jpg" width="800" />
<img src="docs/portfolio/slide-14.jpg" width="800" />
<img src="docs/portfolio/slide-15.jpg" width="800" />
<img src="docs/portfolio/slide-16.jpg" width="800" />
<img src="docs/portfolio/slide-17.jpg" width="800" />
<img src="docs/portfolio/slide-18.jpg" width="800" />
<img src="docs/portfolio/slide-19.jpg" width="800" />
<img src="docs/portfolio/slide-20.jpg" width="800" />

</details>

<details>
<summary><b>04. 트러블슈팅</b></summary>
<br>

<img src="docs/portfolio/slide-21.jpg" width="800" />
<img src="docs/portfolio/slide-22.jpg" width="800" />
<img src="docs/portfolio/slide-23.jpg" width="800" />
<img src="docs/portfolio/slide-24.jpg" width="800" />
<img src="docs/portfolio/slide-25.jpg" width="800" />

</details>

<details>
<summary><b>05. 평가와 개선</b></summary>
<br>

<img src="docs/portfolio/slide-26.jpg" width="800" />
<img src="docs/portfolio/slide-27.jpg" width="800" />
<img src="docs/portfolio/slide-28.jpg" width="800" />

</details>

<br>

## 📌 주요 기능

| 분류 | 기능 |
| --- | --- |
| 📄 **등기부 판독** | PDF 업로드(PDFBox), 촬영 이미지 여러 장 OCR(Google Cloud Vision), 표제부·갑구·을구 구조화 |
| 🚨 **위험 판단** | 규칙 16개 — 선순위 채권 비율, 전세가율·월세 환산, 압류·소유자 불일치, 소액임차인 최우선변제 등. 신호마다 근거 유형(사실 확인·법적 기준·정부 권고) 표시 |
| 🏢 **공공데이터** | 국토부 실거래가(아파트·오피스텔·연립다세대), 건축물대장 표제부·층별개요, 도로명주소, 브이월드 공시가격 |
| 🏠 **다가구 조건부 분석** | 선순위 보증금 직접 입력, 토지 등기부 함께 분석, HUG 반환보증 가입 가능성 추정 — '안전' 판정은 내리지 않음 |
| ✅ **체크리스트** | 계약 형태·권리관계에 따라 달라지는 직접 확인 항목 안내 |
| ⚖️ **변호사 상담** | 관리자 승인을 거친 변호사와 1:1 실시간 채팅(WebSocket), 신고·이용 정지·개인 차단 |
| 💳 **결제** | 토스페이먼츠 — 분석 이용권(990원), 변호사 직접 선택(2,900원), 결제 취소·환불 |
| 🔔 **알림** | 이메일, 웹 푸시(VAPID), 앱 푸시(FCM) |
| 👤 **회원** | 이메일 인증 가입, 구글·카카오·네이버 소셜 로그인, 비밀번호 찾기, 회원 탈퇴 |
| 🛠 **관리자** | 변호사 승인, 신고·회원·결제 관리 (웹·앱 공통) |

<br>

## 🏗 구조

```
guard-project
├── backend/        # Spring Boot 서버 (:8080) — 등기부 판독, 위험 판단, 시세 조회, 인증, 상담, 결제
├── frontend-web/   # Next.js 웹 (:3000)
├── frontend-app/   # Flutter 앱 (iOS / Android), 웹과 같은 백엔드 API 사용
└── docs/           # 기획서, 개발 결정사항, 포트폴리오
```

- 분석 로직은 Spring 서버 한 곳에만 있고, 웹과 앱은 같은 REST API를 호출합니다.
- 위험 판단은 `RiskRule` 인터페이스를 구현한 규칙들을 `List<RiskRule>`로 주입받아 각각 평가합니다. 규칙을 추가해도 엔진 코드는 바뀌지 않습니다.
- 실시간 채팅은 WebSocket으로 주고받으며, 브라우저는 Next.js 서버가 발급받은 1회용 입장권으로만 접속합니다.
- 업로드한 등기부등본 원본은 메모리에서만 처리하고 저장하지 않습니다.

<br>

## 🚀 실행 방법

### 1. 준비물
- Java 25, Node.js
- 앱을 실행하려면 Flutter SDK, Android Studio

### 2. 환경변수 설정
`backend/.env.example`을 복사해 `backend/.env`를, `frontend-web/.env.example`을 복사해 `frontend-web/.env.local`을 만들고 값을 채웁니다.

| 이름 | 설명 |
| --- | --- |
| `DATA_GO_KR_API_KEY`, `MOLIT_*_ENDPOINT` | 국토부 실거래가 API |
| `BUILDING_REGISTER_ENDPOINT` | 건축물대장(건축HUB) |
| `JUSO_ADDRESS_API_KEY`, `VWORLD_API_KEY` | 도로명주소, 브이월드 공시가격 |
| `GOOGLE_APPLICATION_CREDENTIALS` | Google Cloud Vision 서비스 계정 키 경로 |
| `TOSS_SECRET_KEY`, `NEXT_PUBLIC_TOSS_CLIENT_KEY` | 토스페이먼츠 결제 |
| `GOOGLE_* / KAKAO_* / NAVER_*` | 소셜 로그인 (웹 `.env.local`) |
| `INTERNAL_SYNC_SECRET` | 웹 서버 ↔ 백엔드 내부 통신 비밀값 (양쪽 동일) |
| `MAIL_*`, `VAPID_*`, `FCM_CREDENTIALS_PATH` | 메일, 웹 푸시, 앱 푸시 |

> 개발 DB는 파일 기반 H2라 별도 설치가 필요 없습니다. 키가 없는 외부 연동은 조회만 건너뛰고 분석은 그대로 동작합니다.

### 3. 서버 실행

```bash
# 백엔드 (http://localhost:8080)
cd backend && ./gradlew bootRun

# 웹 (http://localhost:3000)
cd frontend-web && npm install && npm run dev

# 앱 (에뮬레이터를 켜 둔 상태에서)
cd frontend-app && flutter pub get && flutter run
```

### 4. 테스트

```bash
cd backend && ./gradlew test             # 백엔드 단위·통합 테스트
cd frontend-web && npm run test:e2e      # Playwright e2e 테스트
```

> 앱 실행 방법과 백엔드 주소 설정은 [frontend-app/README.md](frontend-app/README.md)를 참고하세요.

<br>

## 📚 문서

| 문서 | 내용 |
| --- | --- |
| [기획서](docs/기획서.md) | 기획 배경, 기능 우선순위, 화면 구성, 보안·개인정보 체크리스트 |
| [개발 결정사항](docs/결정사항.md) | 기획 대비 실제 개발 결정 51건과 그 이유, 트러블슈팅 기록 |

<br>

## 👤 개발자

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/chean0830">
        <img src="https://github.com/chean0830.png" width="100" /><br>
        <b>chean0830</b>
      </a>
      <br>
      기획 · 백엔드 · 웹 · 앱
    </td>
  </tr>
</table>

> 이 서비스는 법률 조언이 아니며 참고용 정보를 제공합니다.
