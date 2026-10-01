import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/live_socket.dart';
import '../../api/member_api.dart';
import '../../theme.dart';
import '../../widgets/common.dart';
import '../consultation/consultation_thread_screen.dart';

/// 회원 변호사 상담 (웹 /consult): 새 문의 시작 + 내 문의 내역(실시간 갱신).
class ConsultScreen extends StatefulWidget {
  final MemberApi memberApi;
  final VoidCallback onLoggedOut;

  const ConsultScreen({super.key, required this.memberApi, required this.onLoggedOut});

  @override
  State<ConsultScreen> createState() => _ConsultScreenState();
}

class _ConsultScreenState extends State<ConsultScreen> {
  final _message = TextEditingController();
  List<MemberConsultationSummary>? _items;
  String? _error;
  bool _sending = false;
  late final LiveSocket _live;

  @override
  void initState() {
    super.initState();
    // 새 문의·새 답변이 오면 서버가 알려주고, 그때 목록을 다시 불러온다. 주기적인 자동 갱신은 하지 않는다.
    _live = LiveSocket(
      connectSocket: widget.memberApi.connectInbox,
      onData: (data) {
        if (isInboxEvent(data)) _load();
      },
      onConnected: _load,
    );
    _live.start(onError: (e) {
      if (e is ApiException && e.loginRequired) widget.onLoggedOut();
      if (_items == null) _load(); // 소켓이 안 열려도 목록은 보여준다.
    });
  }

  @override
  void dispose() {
    _live.close();
    _message.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final items = await widget.memberApi.listConsultations();
      if (mounted) {
        setState(() {
          _items = items;
          _error = null;
        });
      }
    } on ApiException catch (e) {
      if (e.loginRequired) return widget.onLoggedOut();
      if (mounted) setState(() => _error = e.message);
    } catch (e, stack) {
      if (mounted) setState(() => _error = describeError(e, stack));
    }
  }

  Future<void> _start() async {
    final text = _message.text.trim();
    if (text.isEmpty) {
      _toast('문의 내용을 입력해주세요.');
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() => _sending = true);
    try {
      final created = await widget.memberApi.startConsultation(text);
      _message.clear();
      _load();
      if (!mounted) return;
      final open = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          backgroundColor: Colors.white,
          icon: const Text('📨', style: TextStyle(fontSize: 36)),
          title: const Text('문의가 접수됐어요', style: TextStyle(fontSize: 19, fontWeight: FontWeight.w700)),
          content: Text(keepAll('${created.lawyerName} 변호사님이 답변을 준비중이에요. 답변이 오면 내 문의 내역에 바로 표시돼요.'),
              textAlign: TextAlign.center, style: const TextStyle(fontSize: 14, color: AppColors.zinc600)),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('닫기')),
            FilledButton(
              style: FilledButton.styleFrom(backgroundColor: AppColors.orange500),
              onPressed: () => Navigator.pop(context, true),
              child: const Text('대화 보기'),
            ),
          ],
        ),
      );
      if (open == true) _open(created.id, created.lawyerName);
    } on ApiException catch (e) {
      if (e.loginRequired) return widget.onLoggedOut();
      _toast(e.message);
    } catch (e, stack) {
      _toast(describeError(e, stack));
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _open(int id, String lawyerName) async {
    await Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => ConsultationThreadScreen(
        chat: widget.memberApi.chat,
        consultationId: id,
        counterpartName: lawyerName,
        onLoggedOut: widget.onLoggedOut,
      ),
    ));
    // 대화방에서 실시간으로 받은 메시지는 서버에 다시 묻지 않았으므로, 나올 때 한 번 읽음 처리한다.
    try {
      await widget.memberApi.chat.thread(id);
    } catch (_) {
      // 읽음 처리가 실패해도 목록은 다시 불러온다.
    }
    _load();
  }

  void _toast(String text) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      color: AppColors.orange500,
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 28, 16, 32),
        children: [
          const Text('변호사 상담', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
          const SizedBox(height: 6),
          Text(keepAll('문의를 남기면 승인된 변호사 중 한 분과 무작위로 연결돼요. 실제 변호사가 직접 확인 후 답변드립니다.'),
              style: const TextStyle(fontSize: 14, color: AppColors.zinc500, height: 1.5)),
          const SizedBox(height: 20),
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: Colors.white,
              border: Border.all(color: AppColors.zinc200),
              borderRadius: BorderRadius.circular(20),
              boxShadow: const [BoxShadow(color: Color(0x0F000000), blurRadius: 16, offset: Offset(0, 6))],
            ),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const Text('새 문의 시작하기', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
              const SizedBox(height: 12),
              TextField(
                controller: _message,
                minLines: 4,
                maxLines: 8,
                decoration: const InputDecoration(
                  hintText: '예: 등기부에 근저당이 있는데 계약해도 괜찮을까요? 상황을 자세히 적어주시면 더 정확한 답변을 받을 수 있어요.',
                  hintMaxLines: 4,
                ),
              ),
              const SizedBox(height: 14),
              PillButton(label: _sending ? '등록 중...' : '문의 보내기', loading: _sending, onPressed: _start),
            ]),
          ),
          const SizedBox(height: 32),
          Row(children: [
            const Text('내 문의 내역', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
            const SizedBox(width: 10),
            ValueListenableBuilder<bool>(
              valueListenable: _live.connected,
              builder: (_, connected, _) => LiveIndicator(connected: connected),
            ),
          ]),
          const SizedBox(height: 12),
          if (_error != null) ...[NoticeBox(_error!), const SizedBox(height: 12)],
          if (_items == null && _error == null)
            const Padding(
              padding: EdgeInsets.only(top: 24),
              child: Center(child: CircularProgressIndicator(color: AppColors.orange500)),
            )
          else if (_items != null && _items!.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 24),
              child: Text('아직 남긴 문의가 없어요.',
                  textAlign: TextAlign.center, style: TextStyle(fontSize: 14, color: AppColors.zinc500)),
            )
          else if (_items != null)
            for (final item in _items!)
              _ConsultationTile(item: item, onTap: () => _open(item.id, item.lawyerName)),
        ],
      ),
    );
  }
}

/// 상담 목록 알림(`{"type":"inbox"}`)인지 확인한다.
bool isInboxEvent(Object? data) => data is String && data.contains('"type":"inbox"');

class _ConsultationTile extends StatelessWidget {
  final MemberConsultationSummary item;
  final VoidCallback onTap;

  const _ConsultationTile({required this.item, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final unread = item.unreadCount > 0;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Material(
        color: Colors.white,
        shape: RoundedRectangleBorder(
          side: BorderSide(color: unread ? AppColors.orange400 : AppColors.zinc200),
          borderRadius: BorderRadius.circular(16),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(children: [
              const CircleAvatar(
                radius: 22,
                backgroundColor: AppColors.zinc900,
                child: Icon(Icons.balance_outlined, color: Colors.white, size: 20),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(children: [
                    Expanded(
                      child: Text(
                        [('${item.lawyerName} 변호사'), if (item.lawFirm != null) item.lawFirm!].join(' · '),
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(fontSize: 15, fontWeight: unread ? FontWeight.w700 : FontWeight.w600),
                      ),
                    ),
                    if (item.lastMessageAt != null)
                      Text(formatRelativeTime(item.lastMessageAt!),
                          style: const TextStyle(fontSize: 12, color: AppColors.zinc400)),
                  ]),
                  const SizedBox(height: 4),
                  Row(children: [
                    Expanded(
                      child: Text(item.lastMessagePreview ?? '',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(fontSize: 13, color: unread ? AppColors.zinc800 : AppColors.zinc500)),
                    ),
                    if (unread) ...[
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                        decoration: BoxDecoration(color: AppColors.orange500, borderRadius: BorderRadius.circular(99)),
                        child: Text('${item.unreadCount}',
                            style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: Colors.white)),
                      ),
                    ],
                  ]),
                ]),
              ),
            ]),
          ),
        ),
      ),
    );
  }
}
