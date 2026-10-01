import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'api_client.dart';

/// 변호사 상담 관리 API (`/api/lawyer/...`).
class LawyerApi {
  final ApiClient _api;

  LawyerApi(this._api);

  Future<List<ConsultationSummary>> listConsultations() async {
    final list = await _api.authedJson('GET', '/api/lawyer/consultations') as List;
    return list.map((e) => ConsultationSummary.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// 대화 내용을 불러온다. 서버는 이 조회를 읽음 처리로도 쓴다.
  Future<ConsultationThread> thread(int id) async =>
      ConsultationThread.fromJson(await _api.authedJson('GET', '/api/lawyer/consultations/$id') as Map<String, dynamic>);

  Future<ChatMessage> sendMessage(int id, String content) async => ChatMessage.fromJson(
      await _api.authedJson('POST', '/api/lawyer/consultations/$id/messages', body: {'content': content})
          as Map<String, dynamic>);

  Future<String> report(int id, String reason, String? detail) async {
    final res = await _api.authedJson('POST', '/api/lawyer/consultations/$id/report',
        body: {'reason': reason, 'detail': detail}) as Map<String, dynamic>;
    return res['message'] as String;
  }

  Future<void> block(int id) => _api.authedJson('POST', '/api/lawyer/consultations/$id/block');

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

  /// 대화방 실시간 알림에 접속한다. 1회용 입장권을 받아 소켓을 연다.
  /// 서버는 새 메시지가 저장될 때마다 `{"type":"message", ...}`를 보낸다.
  Future<WebSocket> connectThread(int id) async {
    final res = await _api.authedJson('POST', '/api/lawyer/consultations/$id/socket-ticket') as Map<String, dynamic>;
    final uri = _api.webSocketUri('/ws/consultations', {'ticket': res['ticket'] as String});
    return WebSocket.connect(uri.toString()).timeout(const Duration(seconds: 10));
  }

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

  /// 소켓으로 받은 새 메시지 알림을 메시지로 바꾼다. 새 메시지 알림이 아니면 null.
  static ChatMessage? parseSocketMessage(Object? data) {
    if (data is! String) return null;
    try {
      final json = jsonDecode(data) as Map<String, dynamic>;
      return json['type'] == 'message' ? ChatMessage.fromJson(json) : null;
    } catch (_) {
      return null;
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

/// blockState: NONE(차단 없음) · BLOCKED_BY_ME(내가 차단) · BLOCKED_ME(상대가 차단)
class ConsultationThread {
  final String userDisplayName;
  final bool counterpartBlocked;
  final String blockState;
  final List<ChatMessage> messages;

  ConsultationThread({
    required this.userDisplayName,
    required this.counterpartBlocked,
    required this.blockState,
    required this.messages,
  });

  bool get canSend => !counterpartBlocked && blockState == 'NONE';

  /// 실시간으로 받은 메시지를 덧붙인다. 내가 보낸 메시지는 전송 응답과 소켓 알림으로 두 번 오므로 한 번만 넣는다.
  ConsultationThread withMessage(ChatMessage message) {
    if (messages.any((m) => m.isSameAs(message))) return this;
    return ConsultationThread(
      userDisplayName: userDisplayName,
      counterpartBlocked: counterpartBlocked,
      blockState: blockState,
      messages: [...messages, message],
    );
  }

  factory ConsultationThread.fromJson(Map<String, dynamic> json) => ConsultationThread(
        userDisplayName: json['userDisplayName'] as String,
        counterpartBlocked: json['counterpartBlocked'] as bool,
        blockState: json['blockState'] as String,
        messages: (json['messages'] as List).map((e) => ChatMessage.fromJson(e as Map<String, dynamic>)).toList(),
      );
}

class ChatMessage {
  final String senderType; // USER, LAWYER
  final String content;
  final DateTime createdAt;

  ChatMessage({required this.senderType, required this.content, required this.createdAt});

  /// 전송 응답과 소켓 알림의 시각 표기 정밀도가 다를 수 있어 1초 이내면 같은 메시지로 본다.
  bool isSameAs(ChatMessage other) =>
      senderType == other.senderType &&
      content == other.content &&
      createdAt.difference(other.createdAt).abs() < const Duration(seconds: 1);

  factory ChatMessage.fromJson(Map<String, dynamic> json) => ChatMessage(
        senderType: json['senderType'] as String,
        content: json['content'] as String,
        createdAt: DateTime.parse(json['createdAt'] as String),
      );
}

class BlockedEntry {
  final int id;
  final String name;
  final String? detail;
  final DateTime blockedAt;

  BlockedEntry({required this.id, required this.name, required this.detail, required this.blockedAt});

  factory BlockedEntry.fromJson(Map<String, dynamic> json) => BlockedEntry(
        id: (json['id'] as num).toInt(),
        name: json['name'] as String,
        detail: json['detail'] as String?,
        blockedAt: DateTime.parse(json['blockedAt'] as String),
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

/// "3분 전", "어제 14:05" 같은 짧은 시간 표기.
String formatRelativeTime(DateTime time) {
  final local = time.toLocal();
  final now = DateTime.now();
  final diff = now.difference(local);
  String hm() => '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
  if (diff.inMinutes < 1) return '방금';
  if (diff.inMinutes < 60) return '${diff.inMinutes}분 전';
  final today = DateTime(now.year, now.month, now.day);
  final day = DateTime(local.year, local.month, local.day);
  if (day == today) return hm();
  if (day == today.subtract(const Duration(days: 1))) return '어제 ${hm()}';
  if (local.year == now.year) return '${local.month}월 ${local.day}일';
  return '${local.year}.${local.month}.${local.day}';
}

/// 대화방 안 메시지 시간 표기 (오전/오후 h:mm).
String formatMessageTime(DateTime time) {
  final local = time.toLocal();
  final ampm = local.hour < 12 ? '오전' : '오후';
  final h = local.hour % 12 == 0 ? 12 : local.hour % 12;
  return '$ampm $h:${local.minute.toString().padLeft(2, '0')}';
}
