import 'package:flutter_test/flutter_test.dart';
import 'package:project_guard/api/lawyer_api.dart';

void main() {
  test('상담 목록 응답을 모델로 변환한다', () {
    final item = ConsultationSummary.fromJson({
      'id': 3,
      'userDisplayName': '홍길동',
      'lastMessagePreview': '보증금 문의드립니다',
      'lastMessageAt': '2026-10-01T03:00:00Z',
      'unreadCount': 2,
    });
    expect(item.id, 3);
    expect(item.unreadCount, 2);
    expect(item.lastMessageAt!.isUtc, isTrue);
  });

  test('차단·정지 상태에서는 답장을 보낼 수 없다', () {
    ConsultationThread thread(String blockState, {bool suspended = false}) => ConsultationThread.fromJson({
          'userDisplayName': '홍길동',
          'counterpartBlocked': suspended,
          'blockState': blockState,
          'messages': [
            {'senderType': 'USER', 'content': '안녕하세요', 'createdAt': '2026-10-01T03:00:00Z'},
          ],
        });

    expect(thread('NONE').canSend, isTrue);
    expect(thread('BLOCKED_BY_ME').canSend, isFalse);
    expect(thread('BLOCKED_ME').canSend, isFalse);
    expect(thread('NONE', suspended: true).canSend, isFalse);
  });

  test('실시간 소켓에서 새 메시지 알림만 메시지로 바꾼다', () {
    final message = LawyerApi.parseSocketMessage(
        '{"type":"message","senderType":"USER","content":"안녕하세요","createdAt":"2026-10-01T03:00:00.123456Z"}');
    expect(message!.senderType, 'USER');
    expect(message.content, '안녕하세요');
    expect(LawyerApi.parseSocketMessage('{"type":"ping"}'), isNull);
    expect(LawyerApi.parseSocketMessage('not json'), isNull);
  });

  test('같은 메시지가 전송 응답과 소켓으로 두 번 와도 한 번만 들어간다', () {
    final thread = ConsultationThread.fromJson({
      'userDisplayName': '홍길동',
      'counterpartBlocked': false,
      'blockState': 'NONE',
      'messages': [],
    });
    final sent = ChatMessage(senderType: 'LAWYER', content: '답변드립니다', createdAt: DateTime.utc(2026, 10, 1, 3));
    final echoed = ChatMessage(senderType: 'LAWYER', content: '답변드립니다', createdAt: DateTime.utc(2026, 10, 1, 3));

    final updated = thread.withMessage(sent).withMessage(echoed);
    expect(updated.messages, hasLength(1));
  });

  test('상담 목록 소켓 알림을 구분한다', () {
    expect(LawyerApi.isInboxEvent('{"type":"inbox","reason":"NEW_MESSAGE","consultationId":3}'), isTrue);
    expect(LawyerApi.isInboxEvent('{"type":"message"}'), isFalse);
    expect(LawyerApi.isInboxEvent('not json'), isFalse);
  });
}
