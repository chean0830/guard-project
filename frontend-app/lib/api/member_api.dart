import 'dart:async';
import 'dart:io';

import 'api_client.dart';
import 'consultation_api.dart';

export 'consultation_api.dart';

/// 회원 전용 API: 변호사 상담 시작·내 문의 목록 (`/api/consultations`), 차단 관리 (`/api/blocks`).
class MemberApi {
  final ApiClient _api;

  /// 대화방(대화·메시지·신고·차단·실시간)은 변호사와 같은 화면을 쓴다.
  final ConsultationChatApi chat;

  MemberApi(this._api) : chat = ConsultationChatApi.member(_api);

  /// 새 문의를 남긴다. 승인된 변호사 중 한 분과 무작위로 연결된다.
  Future<MemberConsultationSummary> startConsultation(String message) async => MemberConsultationSummary.fromJson(
      await _api.authedJson('POST', '/api/consultations', body: {'message': message}) as Map<String, dynamic>);

  Future<List<MemberConsultationSummary>> listConsultations() async {
    final list = await _api.authedJson('GET', '/api/consultations') as List;
    return list.map((e) => MemberConsultationSummary.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// 내 문의 목록 실시간 알림에 접속한다. 내 대화방에 새 메시지가 생기면
  /// 서버가 `{"type":"inbox", ...}`를 보낸다 (변호사 문의함과 같은 형식).
  Future<WebSocket> connectInbox() async {
    final res = await _api.authedJson('POST', '/api/consultations/inbox/socket-ticket') as Map<String, dynamic>;
    final uri = _api.webSocketUri('/ws/user-inbox', {'ticket': res['ticket'] as String});
    return WebSocket.connect(uri.toString()).timeout(const Duration(seconds: 10));
  }

  Future<List<BlockedEntry>> listBlocks() async {
    final list = await _api.authedJson('GET', '/api/blocks') as List;
    return list.map((e) => BlockedEntry.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> unblock(int blockId) => _api.authedJson('DELETE', '/api/blocks/$blockId');

  Future<MemberProfile> profile() async =>
      MemberProfile.fromJson(await _api.authedJson('GET', '/api/profile') as Map<String, dynamic>);

  Future<MemberProfile> updateName(String? name) async => MemberProfile.fromJson(
      await _api.authedJson('PUT', '/api/profile', body: {'name': name}) as Map<String, dynamic>);

  Future<void> changePassword(String current, String next) =>
      _api.authedJson('POST', '/api/profile/password', body: {'currentPassword': current, 'newPassword': next});

  /// 회원 탈퇴. 이메일 가입은 비밀번호, 소셜 전용 계정은 확인 문구("탈퇴")로 본인 의사를 확인한다.
  Future<void> withdraw({String? password, String? confirmText}) =>
      _api.authedJson('DELETE', '/api/profile', body: {'password': password, 'confirmText': confirmText});
}

class MemberProfile {
  final String email;
  final String? name;

  /// LOCAL(이메일 가입) 또는 GOOGLE·KAKAO·NAVER.
  final String provider;
  final bool hasPassword;

  MemberProfile({required this.email, this.name, required this.provider, required this.hasPassword});

  bool get isLocal => provider == 'LOCAL';

  factory MemberProfile.fromJson(Map<String, dynamic> json) => MemberProfile(
        email: json['email'] as String,
        name: json['name'] as String?,
        provider: (json['provider'] ?? 'LOCAL') as String,
        hasPassword: (json['hasPassword'] ?? true) as bool,
      );
}

class MemberConsultationSummary {
  final int id;
  final String lawyerName;
  final String? lawFirm;
  final String? lastMessagePreview;
  final DateTime? lastMessageAt;
  final int unreadCount;

  MemberConsultationSummary({
    required this.id,
    required this.lawyerName,
    required this.lawFirm,
    required this.lastMessagePreview,
    required this.lastMessageAt,
    required this.unreadCount,
  });

  factory MemberConsultationSummary.fromJson(Map<String, dynamic> json) => MemberConsultationSummary(
        id: (json['id'] as num).toInt(),
        lawyerName: (json['lawyerName'] ?? '알 수 없음') as String,
        lawFirm: json['lawFirm'] as String?,
        lastMessagePreview: json['lastMessagePreview'] as String?,
        lastMessageAt: json['lastMessageAt'] == null ? null : DateTime.parse(json['lastMessageAt'] as String),
        unreadCount: (json['unreadCount'] as num).toInt(),
      );
}
