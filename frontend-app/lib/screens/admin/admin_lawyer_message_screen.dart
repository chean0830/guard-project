import 'package:flutter/material.dart';

import '../../api/admin_api.dart';
import '../../api/api_client.dart';
import '../../theme.dart';
import '../../widgets/common.dart';
import 'admin_common.dart';

/// 관리자 → 변호사 1:1 메시지. 위에 보낸 기록(읽음 여부), 아래에 입력창. 변호사는 알림함에서 읽기만 한다.
class AdminLawyerMessageScreen extends StatefulWidget {
  final AdminApi adminApi;
  final int lawyerId;
  final String lawyerName;
  final VoidCallback onLoggedOut;

  const AdminLawyerMessageScreen({
    super.key,
    required this.adminApi,
    required this.lawyerId,
    required this.lawyerName,
    required this.onLoggedOut,
  });

  @override
  State<AdminLawyerMessageScreen> createState() => _AdminLawyerMessageScreenState();
}

class _AdminLawyerMessageScreenState extends State<AdminLawyerMessageScreen> {
  final _input = TextEditingController();
  List<AdminMessageItem>? _messages;
  String? _error;
  bool _sending = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _input.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final messages = await widget.adminApi.lawyerMessages(widget.lawyerId);
      if (mounted) {
        setState(() {
          _messages = messages;
          _error = null;
        });
      }
    } catch (e, stack) {
      _handleError(e, stack);
    }
  }

  void _handleError(Object e, StackTrace stack) {
    if (e is ApiException && e.loginRequired) {
      Navigator.of(context).popUntil((route) => route.isFirst);
      return widget.onLoggedOut();
    }
    if (mounted) setState(() => _error = describeError(e, stack));
  }

  Future<void> _send() async {
    final text = _input.text.trim();
    if (text.isEmpty) return;
    setState(() => _sending = true);
    try {
      final sent = await widget.adminApi.sendLawyerMessage(widget.lawyerId, text);
      _input.clear();
      setState(() {
        _messages = [sent, ...?_messages];
        _error = null;
      });
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('메시지를 보냈어요.')));
    } catch (e, stack) {
      _handleError(e, stack);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final messages = _messages;
    return Scaffold(
      backgroundColor: AppColors.zinc50,
      appBar: AppBar(
        title: Text('${widget.lawyerName} 변호사에게 메시지',
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
        bottom: const PreferredSize(preferredSize: Size.fromHeight(1), child: Divider()),
      ),
      body: SafeArea(
        child: Column(children: [
          Expanded(
            child: RefreshIndicator(
              color: AppColors.orange500,
              onRefresh: _load,
              child: ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(16),
                children: [
                  Text(keepAll('변호사의 관리자 메시지 알림함으로 전달되고 앱 푸시도 가요. 변호사는 답장할 수 없어요.'),
                      style: const TextStyle(fontSize: 13, color: AppColors.zinc500, height: 1.5)),
                  const SizedBox(height: 12),
                  if (_error != null) ...[NoticeBox(_error!), const SizedBox(height: 12)],
                  if (messages == null && _error == null)
                    const Padding(
                      padding: EdgeInsets.only(top: 40),
                      child: Center(child: CircularProgressIndicator(color: AppColors.orange500)),
                    )
                  else if (messages != null && messages.isEmpty)
                    const Padding(
                      padding: EdgeInsets.only(top: 40),
                      child: Text('아직 보낸 메시지가 없어요.',
                          textAlign: TextAlign.center, style: TextStyle(fontSize: 14, color: AppColors.zinc500)),
                    )
                  else
                    for (final m in messages ?? const <AdminMessageItem>[])
                      AdminCard(
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Row(children: [
                            Expanded(
                              child: Text(formatDateTime(m.createdAt),
                                  style: const TextStyle(fontSize: 12, color: AppColors.zinc500)),
                            ),
                            m.readAt == null
                                ? const StatusBadge('안 읽음', background: AppColors.amber50, foreground: AppColors.amber900)
                                : StatusBadge('읽음 ${formatDateTime(m.readAt)}',
                                    background: AppColors.emerald50, foreground: AppColors.emerald900),
                          ]),
                          const SizedBox(height: 6),
                          Text(m.content, style: const TextStyle(fontSize: 14, height: 1.5)),
                        ]),
                      ),
                ],
              ),
            ),
          ),
          Container(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
            decoration: const BoxDecoration(
              color: Colors.white,
              border: Border(top: BorderSide(color: AppColors.zinc200)),
            ),
            child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
              Expanded(
                child: TextField(
                  controller: _input,
                  minLines: 1,
                  maxLines: 5,
                  maxLength: 2000,
                  onChanged: (_) => setState(() {}),
                  decoration: const InputDecoration(hintText: '변호사에게 보낼 내용', counterText: ''),
                ),
              ),
              const SizedBox(width: 8),
              smallPill(_sending ? '보내는 중...' : '보내기',
                  _sending || _input.text.trim().isEmpty ? null : _send,
                  color: AppColors.orange500, filled: true),
            ]),
          ),
        ]),
      ),
    );
  }
}
