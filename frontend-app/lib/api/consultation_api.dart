import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'api_client.dart';

/// 상담 대화방 API. 회원(`/api/consultations`)과 변호사(`/api/lawyer/consultations`)가 같은 모양이라
/// 경로와 "내 쪽" 발신자 종류만 바꿔 한 화면에서 같이 쓴다.
class ConsultationChatApi {
  final ApiClient _api;
  final String _base;

  /// 내 메시지의 senderType (회원: USER, 변호사: LAWYER).
  final String mySenderType;

  /// 상대방을 부르는 말 (회원 화면: 변호사, 변호사 화면: 문의자).
  final String counterpartLabel;

  ConsultationChatApi.member(this._api)
      : _base = '/api/consultations',
        mySenderType = 'USER',
        counterpartLabel = '변호사';

  ConsultationChatApi.lawyer(this._api)
      : _base = '/api/lawyer/consultations',
        mySenderType = 'LAWYER',
        counterpartLabel = '문의자';

  /// 대화 내용을 불러온다. 서버는 이 조회를 읽음 처리로도 쓴다.
  Future<ConsultationThread> thread(int id) async =>
      ConsultationThread.fromJson(await _api.authedJson('GET', '$_base/$id') as Map<String, dynamic>);

  Future<ChatMessage> sendMessage(int id, String content) async => ChatMessage.fromJson(
      await _api.authedJson('POST', '$_base/$id/messages', body: {'content': content}) as Map<String, dynamic>);

  Future<String> report(int id, String reason, String? detail) async {
    final res = await _api.authedJson('POST', '$_base/$id/report', body: {'reason': reason, 'detail': detail})
        as Map<String, dynamic>;
    return res['message'] as String;
  }

  Future<void> block(int id) => _api.authedJson('POST', '$_base/$id/block');

  /// 대화방 실시간 알림에 접속한다. 1회용 입장권을 받아 소켓을 연다.
  /// 서버는 새 메시지가 저장될 때마다 `{"type":"message", ...}`를 보낸다.
  Future<WebSocket> connectThread(int id) async {
    final res = await _api.authedJson('POST', '$_base/$id/socket-ticket') as Map<String, dynamic>;
    final uri = _api.webSocketUri('/ws/consultations', {'ticket': res['ticket'] as String});
    return WebSocket.connect(uri.toString()).timeout(const Duration(seconds: 10));
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

/// blockState: NONE(차단 없음) · BLOCKED_BY_ME(내가 차단) · BLOCKED_ME(상대가 차단)
class ConsultationThread {
  /// 회원 화면에서는 변호사 이름, 변호사 화면에서는 문의자 이름.
  final String counterpartName;

  /// 회원 화면에서만: 변호사 소속.
  final String? counterpartDetail;
  final bool counterpartBlocked;
  final String blockState;
  final List<ChatMessage> messages;

  ConsultationThread({
    required this.counterpartName,
    this.counterpartDetail,
    required this.counterpartBlocked,
    required this.blockState,
    required this.messages,
  });

  bool get canSend => !counterpartBlocked && blockState == 'NONE';

  /// 실시간으로 받은 메시지를 덧붙인다. 내가 보낸 메시지는 전송 응답과 소켓 알림으로 두 번 오므로 한 번만 넣는다.
  ConsultationThread withMessage(ChatMessage message) {
    if (messages.any((m) => m.isSameAs(message))) return this;
    return ConsultationThread(
      counterpartName: counterpartName,
      counterpartDetail: counterpartDetail,
      counterpartBlocked: counterpartBlocked,
      blockState: blockState,
      messages: [...messages, message],
    );
  }

  /// 회원 응답은 lawyerName/lawFirm, 변호사 응답은 userDisplayName을 준다.
  factory ConsultationThread.fromJson(Map<String, dynamic> json) => ConsultationThread(
        counterpartName: (json['userDisplayName'] ?? json['lawyerName'] ?? '알 수 없음') as String,
        counterpartDetail: json['lawFirm'] as String?,
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
