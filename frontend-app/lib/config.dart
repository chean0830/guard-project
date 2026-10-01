import 'dart:io';

/// 백엔드 주소. 실행할 때 `--dart-define=API_BASE_URL=http://192.168.0.10:8080`처럼 바꿀 수 있다.
/// 지정하지 않으면 개발용 로컬 백엔드(8080)를 쓴다. 안드로이드 에뮬레이터에서는 PC의 localhost가
/// 10.0.2.2로 보인다.
class AppConfig {
  static const _fromEnv = String.fromEnvironment('API_BASE_URL');

  /// 토스페이먼츠 클라이언트 키 (공개 키, 웹 NEXT_PUBLIC_TOSS_CLIENT_KEY와 같은 값).
  /// `--dart-define=TOSS_CLIENT_KEY=test_ck_...`로 넣는다. 없으면 결제 버튼이 안내만 띄운다.
  static const tossClientKey = String.fromEnvironment('TOSS_CLIENT_KEY');

  /// 웹(Next.js) 주소. 소셜 로그인은 웹의 로그인 흐름을 그대로 쓴다 (구글·카카오·네이버에 등록된 돌아올 주소가 웹이라서).
  /// 기본값 localhost:3000은 웹의 OAUTH_BASE_URL과 같아야 하며, 안드로이드에서는 `adb reverse tcp:3000 tcp:3000`이 필요하다.
  static const webBaseUrl = String.fromEnvironment('WEB_BASE_URL', defaultValue: 'http://localhost:3000');

  static String get apiBaseUrl {
    if (_fromEnv.isNotEmpty) return _fromEnv;
    return Platform.isAndroid ? 'http://10.0.2.2:8080' : 'http://localhost:8080';
  }
}
