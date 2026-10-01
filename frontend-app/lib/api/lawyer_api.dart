import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'admin_api.dart' show AdminMessageItem;
import 'api_client.dart';
import 'consultation_api.dart';

export 'admin_api.dart' show AdminMessageItem;
export 'consultation_api.dart';

/// 변호사 전용 API: 문의함 목록·실시간 알림, 차단 관리, 프로필 (`/api/lawyer/...`).
class LawyerApi {
  final ApiClient _api;

  /// 대화방(대화·답장·신고·차단·실시간)은 회원과 같은 화면을 쓴다.
  final ConsultationChatApi chat;

  LawyerApi(this._api) : chat = ConsultationChatApi.lawyer(_api);

  Future<List<ConsultationSummary>> listConsultations() async {
    final list = await _api.authedJson('GET', '/api/lawyer/consultations') as List;
    return list.map((e) => ConsultationSummary.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// 관리자가 보낸 메시지 알림함 (최신순).
  Future<List<AdminMessageItem>> adminMessages() async {
    final list = await _api.authedJson('GET', '/api/lawyer/admin-messages') as List;
    return list.map((e) => AdminMessageItem.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<int> adminMessageUnreadCount() async {
    final res = await _api.authedJson('GET', '/api/lawyer/admin-messages/unread-count') as Map<String, dynamic>;
    return (res['unreadCount'] as num).toInt();
  }

  /// 알림함을 열면 안 읽은 메시지를 모두 읽음 처리한다.
  Future<void> markAdminMessagesRead() => _api.authedJson('POST', '/api/lawyer/admin-messages/read');

  Future<List<BlockedEntry>> listBlocks() async {
    final list = await _api.authedJson('GET', '/api/lawyer/blocks') as List;
    return list.map((e) => BlockedEntry.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> unblock(int blockId) => _api.authedJson('DELETE', '/api/lawyer/blocks/$blockId');

  Future<LawyerProfile> profile() async =>
      LawyerProfile.fromJson(await _api.authedJson('GET', '/api/lawyer/profile') as Map<String, dynamic>);

  Future<LawyerProfile> updateProfile(LawyerProfile p) async => LawyerProfile.fromJson(
      await _api.authedJson('PUT', '/api/lawyer/profile', body: p.toUpdateJson()) as Map<String, dynamic>);

  Future<void> setEmailNotifications(bool enabled) =>
      _api.authedJson('PUT', '/api/lawyer/profile/notifications', body: {'enabled': enabled});

  Future<void> changePassword(String current, String next) =>
      _api.authedJson('POST', '/api/lawyer/profile/password', body: {'currentPassword': current, 'newPassword': next});

  /// 상담 목록 실시간 알림에 접속한다. 내게 배정된 대화방에 새 문의·새 메시지가 생기면
  /// 서버가 `{"type":"inbox", "reason":..., "consultationId":...}`를 보낸다.
  Future<WebSocket> connectInbox() async {
    final res = await _api.authedJson('POST', '/api/lawyer/inbox/socket-ticket') as Map<String, dynamic>;
    final uri = _api.webSocketUri('/ws/lawyer-inbox', {'ticket': res['ticket'] as String});
    return WebSocket.connect(uri.toString()).timeout(const Duration(seconds: 10));
  }

  /// 상담 목록 알림인지 확인한다.
  static bool isInboxEvent(Object? data) {
    if (data is! String) return false;
    try {
      return (jsonDecode(data) as Map<String, dynamic>)['type'] == 'inbox';
    } catch (_) {
      return false;
    }
  }
}

class ConsultationSummary {
  final int id;
  final String userDisplayName;
  final String? lastMessagePreview;
  final DateTime? lastMessageAt;
  final int unreadCount;

  ConsultationSummary({
    required this.id,
    required this.userDisplayName,
    required this.lastMessagePreview,
    required this.lastMessageAt,
    required this.unreadCount,
  });

  factory ConsultationSummary.fromJson(Map<String, dynamic> json) => ConsultationSummary(
        id: (json['id'] as num).toInt(),
        userDisplayName: json['userDisplayName'] as String,
        lastMessagePreview: json['lastMessagePreview'] as String?,
        lastMessageAt: json['lastMessageAt'] == null ? null : DateTime.parse(json['lastMessageAt'] as String),
        unreadCount: (json['unreadCount'] as num).toInt(),
      );
}

class LawyerProfile {
  final String email;
  final String? name;
  final String? lawFirm;
  final String? barNumber;
  final String? specialties;
  final String? introduction;
  final bool emailNotificationsEnabled;
  final String? headline;
  final int? careerYears;
  final String? feeInfo;
  final String? achievements;

  LawyerProfile({
    required this.email,
    this.name,
    this.lawFirm,
    this.barNumber,
    this.specialties,
    this.introduction,
    required this.emailNotificationsEnabled,
    this.headline,
    this.careerYears,
    this.feeInfo,
    this.achievements,
  });

  factory LawyerProfile.fromJson(Map<String, dynamic> json) => LawyerProfile(
        email: json['email'] as String,
        name: json['name'] as String?,
        lawFirm: json['lawFirm'] as String?,
        barNumber: json['barNumber'] as String?,
        specialties: json['specialties'] as String?,
        introduction: json['introduction'] as String?,
        emailNotificationsEnabled: json['emailNotificationsEnabled'] as bool,
        headline: json['headline'] as String?,
        careerYears: (json['careerYears'] as num?)?.toInt(),
        feeInfo: json['feeInfo'] as String?,
        achievements: json['achievements'] as String?,
      );

  Map<String, Object?> toUpdateJson() => {
        'name': name,
        'lawFirm': lawFirm,
        'specialties': specialties,
        'introduction': introduction,
        'headline': headline,
        'careerYears': careerYears,
        'feeInfo': feeInfo,
        'achievements': achievements,
      };
}
