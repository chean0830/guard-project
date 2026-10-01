import 'dart:async';
import 'dart:io';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';

import 'api_client.dart';

/// 푸시 알림을 눌렀을 때 열 상담 대화방.
class PushTarget {
  final int consultationId;
  final String counterpartName;

  /// 받는 사람 계정 종류 (USER 또는 LAWYER). 로그인한 계정과 다르면 열지 않는다.
  final String recipientType;

  PushTarget({required this.consultationId, required this.counterpartName, required this.recipientType});

  static PushTarget? fromMessage(RemoteMessage message) {
    final data = message.data;
    final id = int.tryParse('${data['consultationId']}');
    if (data['type'] != 'CONSULTATION_MESSAGE' || id == null) return null;
    return PushTarget(
      consultationId: id,
      counterpartName: data['counterpartName'] as String? ?? '',
      recipientType: data['recipientType'] as String? ?? '',
    );
  }
}

/// 앱 푸시(Firebase Cloud Messaging). google-services.json(안드로이드)·GoogleService-Info.plist(iOS)가 없으면
/// Firebase 초기화가 실패하고, 그때는 푸시만 꺼진 채로 나머지 기능은 그대로 동작한다.
class PushService {
  PushService._();

  static bool _available = false;
  static String? _registeredToken;
  static StreamSubscription<String>? _tokenRefresh;

  /// 알림을 눌러 앱이 열렸을 때 (앱이 꺼져 있었으면 시작 직후 한 번).
  static final _opened = StreamController<PushTarget>.broadcast();
  static Stream<PushTarget> get opened => _opened.stream;

  /// 앱을 보고 있는 중에 온 알림 (시스템 알림이 뜨지 않으므로 화면 안에서 알려준다).
  static final _foreground = StreamController<(PushTarget, String?)>.broadcast();
  static Stream<(PushTarget, String?)> get foreground => _foreground.stream;

  /// 지금 열려 있는 대화방. 그 방의 새 메시지는 화면에 바로 보이므로 따로 알리지 않는다.
  static int? activeConsultationId;

  static PushTarget? _pendingInitial;

  static Future<void> init() async {
    try {
      await Firebase.initializeApp();
      _available = true;
    } catch (e) {
      debugPrint('Firebase 설정이 없어 앱 푸시를 끕니다: $e');
      return;
    }
    FirebaseMessaging.onMessageOpenedApp.listen((m) {
      final target = PushTarget.fromMessage(m);
      if (target != null) _opened.add(target);
    });
    FirebaseMessaging.onMessage.listen((m) {
      final target = PushTarget.fromMessage(m);
      if (target != null && target.consultationId != activeConsultationId) {
        _foreground.add((target, m.notification?.body));
      }
    });
    final initial = await FirebaseMessaging.instance.getInitialMessage();
    if (initial != null) _pendingInitial = PushTarget.fromMessage(initial);
  }

  /// 앱이 꺼진 상태에서 알림을 눌러 시작했다면 그 대상을 한 번만 돌려준다.
  static PushTarget? takeInitialTarget() {
    final target = _pendingInitial;
    _pendingInitial = null;
    return target;
  }

  /// 로그인한 기기의 푸시 토큰을 서버에 등록한다 (회원·변호사). 실패해도 앱 사용에는 지장이 없다.
  static Future<void> register(ApiClient api) async {
    if (!_available) return;
    try {
      final messaging = FirebaseMessaging.instance;
      final settings = await messaging.requestPermission();
      if (settings.authorizationStatus == AuthorizationStatus.denied) return;
      // iOS는 애플(APNs) 토큰을 받은 뒤에야 FCM 토큰이 나온다. 앱 시작 직후엔 아직 없을 수 있어 잠깐 기다린다.
      if (Platform.isIOS) {
        String? apns;
        for (var i = 0; i < 10 && apns == null; i++) {
          apns = await messaging.getAPNSToken();
          if (apns == null) await Future<void>.delayed(const Duration(seconds: 1));
        }
        if (apns == null) {
          debugPrint('APNs 토큰을 받지 못해 푸시 등록을 건너뜁니다 (실기기·푸시 권한·APNs 키 설정 확인).');
          return;
        }
      }
      final token = await messaging.getToken();
      if (token == null) return;
      await _send(api, token);
      await _tokenRefresh?.cancel();
      _tokenRefresh = messaging.onTokenRefresh.listen((t) => _send(api, t).catchError((_) {}));
    } catch (e) {
      debugPrint('푸시 토큰 등록 실패: $e');
    }
  }

  static Future<void> _send(ApiClient api, String token) async {
    await api.authedJson('POST', '/api/push/devices',
        body: {'token': token, 'platform': Platform.isIOS ? 'ios' : 'android'});
    _registeredToken = token;
  }

  /// 로그아웃 직전에 호출한다 — 이 기기로 더 이상 알림이 오지 않게 한다.
  static Future<void> unregister(ApiClient api) async {
    await _tokenRefresh?.cancel();
    _tokenRefresh = null;
    final token = _registeredToken;
    _registeredToken = null;
    if (token == null) return;
    try {
      await api.authedJson('DELETE', '/api/push/devices', body: {'token': token});
    } catch (e) {
      debugPrint('푸시 토큰 해지 실패: $e');
    }
  }
}
