import 'dart:io';

/// 백엔드 주소. 실행할 때 `--dart-define=API_BASE_URL=http://192.168.0.10:8080`처럼 바꿀 수 있다.
/// 지정하지 않으면 개발용 로컬 백엔드(8080)를 쓴다. 안드로이드 에뮬레이터에서는 PC의 localhost가
/// 10.0.2.2로 보인다.
class AppConfig {
  static const _fromEnv = String.fromEnvironment('API_BASE_URL');

  static String get apiBaseUrl {
    if (_fromEnv.isNotEmpty) return _fromEnv;
    return Platform.isAndroid ? 'http://10.0.2.2:8080' : 'http://localhost:8080';
  }
}
