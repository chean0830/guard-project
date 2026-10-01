# frontend-app (Flutter)

iOS/Android 공통 앱. 웹(frontend-web)과 같은 백엔드 API를 그대로 호출한다.

## 현재 구현 범위

- 서비스 소개(랜딩) → 로그인 → 회원가입(이메일 인증) 흐름, 웹과 같은 디자인
- 로그인 (이메일/비밀번호, 토큰은 기기 보안 저장소에 보관)
  - 관리자는 일반 로그인 화면에서 함께 로그인 (회원 로그인 실패 시 관리자 계정인지 확인, 웹과 동일)
  - 변호사는 별도 "변호사 로그인" 화면. 관리자 로그인 후 화면은 아직 웹 안내만 표시
- 변호사 상담 관리: 배정된 문의 목록(실시간, `/ws/lawyer-inbox`), 대화방(실시간 채팅, `/ws/consultations`), 답변 보내기,
  신고(욕설·모욕 / 금전 요구)·차단, 상담 이용 안내(일주일 동안 안 보기), 설정(프로필·강점, 이메일 알림, 비밀번호 변경, 차단 관리)
- 회원 홈: 아래 탭으로 등기부 분석 / 변호사 상담 / 설정
- 이용약관·개인정보처리방침: 웹 문서를 그대로 옮긴 화면 (가입 화면·소개 화면·설정에서 열기).
  내용은 웹 파일(`frontend-web/src/app/{terms,privacy}/page.tsx`)만 고치고 `python frontend-app/tool/sync_legal_docs.py`로 앱에 반영
- 변호사 회원가입: 자격 서류(PDF·JPG, 파일/앨범/카메라) 첨부 → 관리자 승인 후 로그인
- 회원 설정: 프로필(표시 이름), 비밀번호 변경(이메일 가입만), 차단 관리, 회원 탈퇴. 비밀번호 찾기(회원·변호사, 재설정 링크는 웹 화면)
- 회원 변호사 상담: 새 문의(무작위 매칭) → 접수 안내 → 대화방, 내 문의 내역(실시간, `/ws/user-inbox`),
  대화방은 변호사와 같은 화면(실시간 채팅·신고·차단·이용 안내), 고위험 분석 결과에서 상담으로 바로 이동
- 결제(토스페이먼츠 결제창을 앱 안 WebView로): 무료 분석 소진 시 분석 이용권(990원) 결제 후 바로 재분석,
  변호사 직접 선택(2,900원/회, 이용권 방식) → 입점 변호사 목록에서 골라 문의, 결제 내역(미사용 취소·사용 후 환불 요청)
- 관리자 콘솔(일반 로그인 화면에서 관리자 계정으로 로그인): 아래 탭으로 변호사 승인(자격 서류 열기·승인·거절) /
  신고 관리(대화 원문 확인·이용 정지·기각) / 회원 관리(회원·변호사 검색, 이용 정지·해제) / 결제 관리(환불 승인·거절)
- 푸시 알림(FCM): 회원은 변호사 답장, 변호사는 새 문의·회원 메시지 알림. 알림을 누르면 그 대화방이 열리고,
  앱을 보고 있을 때는 화면 아래 알림줄로 보여준다. 로그아웃하면 그 기기로는 알림이 오지 않는다
- 등기부등본 촬영 · 사진 선택 · PDF 업로드 → `POST /api/analyze`
- 분석 결과: 위험 신호, 직접 확인할 체크리스트, 등기부 요약, 건축물대장, 갑구/을구/압류 내역

아직 없는 것: 소셜 로그인

## 실행 방법

준비물: Flutter SDK, Android Studio(안드로이드 SDK·에뮬레이터)

```bash
# 1. 백엔드를 먼저 켠다 (http://localhost:8080)
cd backend && ./gradlew bootRun

# 2. 앱 실행 (에뮬레이터를 켜 둔 상태에서)
cd frontend-app
flutter pub get
flutter run
```

### 백엔드 주소

| 실행 환경 | 기본 주소 |
|---|---|
| 안드로이드 에뮬레이터 | `http://10.0.2.2:8080` (PC의 localhost) |
| iOS 시뮬레이터 | `http://localhost:8080` |

실제 휴대폰에서는 PC와 같은 와이파이에 연결한 뒤 PC의 IP를 지정한다.

```bash
flutter run --dart-define=API_BASE_URL=http://192.168.0.10:8080
```

### 결제 (토스페이먼츠)

웹의 `NEXT_PUBLIC_TOSS_CLIENT_KEY`와 같은 클라이언트 키를 넣어야 결제 버튼이 동작한다 (없으면 안내만 표시).

```bash
flutter run --dart-define=TOSS_CLIENT_KEY=test_ck_...
```

### 푸시 알림 (Firebase Cloud Messaging)

설정 파일이 없으면 푸시만 꺼지고 나머지는 그대로 동작한다.

1. [Firebase 콘솔](https://console.firebase.google.com)에서 프로젝트를 만들고 안드로이드 앱(패키지 `com.projectguard.project_guard`)을 추가한다.
2. 받은 `google-services.json`을 `frontend-app/android/app/`에 둔다 (git에는 올리지 않음).
3. 프로젝트 설정 > 서비스 계정 > "새 비공개 키 생성"으로 받은 JSON을 `backend/credentials/`에 두고
   백엔드 `.env`에 `FCM_CREDENTIALS_PATH=./credentials/<파일명>.json`을 넣는다.
4. iOS는 Firebase에 iOS 앱 추가 → `GoogleService-Info.plist`를 `ios/Runner/`에, APNs 인증 키를 Firebase에 등록하고
   Xcode에서 Push Notifications·Background Modes(Remote notifications) 기능을 켠다 (Mac 필요).

개발 빌드에서만 HTTP(평문) 통신을 허용한다. 배포 빌드는 HTTPS 백엔드 주소가 필요하다.
