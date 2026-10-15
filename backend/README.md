# backend (Spring)

등기부 분석, 위험 판단, 시세 조회, 회원·변호사 인증, 상담, 결제를 처리하는 서버 (Spring Boot 4.1 · Java 25).

## 실행 방법

```bash
cp .env.example .env   # 필요한 키를 채운다. 각 항목 설명은 .env.example 주석 참고
./gradlew bootRun      # http://localhost:8080
```

키가 비어 있는 외부 기능(시세 조회, OCR, 메일, 푸시, 결제)은 그 기능만 건너뛰거나 실패하고 서버는 뜬다.

## 테스트

```bash
./gradlew test
```

외부 API는 대체하고 인메모리 DB를 써서 키 없이 실행된다.

## 패키지 구성

| 패키지 | 내용 |
|---|---|
| `api` | `POST /api/analyze`, 분석 이용 횟수·이용권 |
| `registry` | 등기부 PDF 추출(PDFBox)·촬영본 OCR(Google Vision)·파싱 |
| `risk` | 위험 판단 규칙 |
| `market` | 실거래가·건축물대장·공시가격·주소 검색 |
| `checklist` | 계약 전 체크리스트 |
| `auth` · `lawyer` · `admin` | 회원·변호사·관리자 인증 |
| `consultation` · `moderation` · `adminmessage` | 상담 대화, 신고·차단, 관리자 메시지 |
| `payment` | 토스페이먼츠 결제·환불 |
| `push` | 웹 푸시·앱 푸시(FCM) |

설계 결정과 그 이유는 [../docs/결정사항.md](../docs/결정사항.md) 참고.
