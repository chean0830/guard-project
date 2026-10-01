import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../api/api_client.dart';
import '../../api/consultation_api.dart';
import '../../theme.dart';
import '../../widgets/common.dart';

/// 상담 대화방 (회원·변호사 공통). 실시간 채팅: 새 메시지는 소켓으로 받아 바로 화면에 붙이고, 주기 조회는 하지 않는다.
/// 소켓이 끊겼다가 다시 연결될 때만 대화를 한 번 다시 불러와 끊긴 사이의 메시지를 채운다.
class ConsultationThreadScreen extends StatefulWidget {
  final ConsultationChatApi chat;
  final int consultationId;
  final String counterpartName;
  final VoidCallback onLoggedOut;

  const ConsultationThreadScreen({
    super.key,
    required this.chat,
    required this.consultationId,
    required this.counterpartName,
    required this.onLoggedOut,
  });

  @override
  State<ConsultationThreadScreen> createState() => _ConsultationThreadScreenState();
}

class _ConsultationThreadScreenState extends State<ConsultationThreadScreen> {
  final _input = TextEditingController();
  ConsultationThread? _thread;
  String? _error;
  bool _sending = false;

  WebSocket? _socket;
  Timer? _reconnect;
  bool _connected = false;
  bool _disposed = false;

  @override
  void initState() {
    super.initState();
    _connect();
    WidgetsBinding.instance.addPostFrameCallback((_) => showChatPolicyNoticeIfNeeded(context));
  }

  @override
  void dispose() {
    _disposed = true;
    _reconnect?.cancel();
    _socket?.close();
    _input.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final thread = await widget.chat.thread(widget.consultationId);
      if (mounted) {
        setState(() {
          _thread = thread;
          _error = null;
        });
      }
    } on ApiException catch (e) {
      if (e.loginRequired) return _loggedOut();
      if (mounted) setState(() => _error = e.message);
    } catch (e, stack) {
      if (mounted) setState(() => _error = describeError(e, stack));
    }
  }

  void _loggedOut() {
    Navigator.of(context).popUntil((r) => r.isFirst);
    widget.onLoggedOut();
  }

  Future<void> _connect() async {
    if (_disposed) return;
    try {
      final socket = await widget.chat.connectThread(widget.consultationId);
      if (_disposed) {
        socket.close();
        return;
      }
      _socket = socket;
      socket.listen(
        (data) {
          final message = ConsultationChatApi.parseSocketMessage(data);
          if (message != null) _append(message);
        },
        onDone: _scheduleReconnect,
        onError: (_) => _scheduleReconnect(),
        cancelOnError: true,
      );
      if (mounted) setState(() => _connected = true);
      // 처음 들어올 때와 재연결 때, 연결 전 사이의 메시지를 채운다.
      await _load();
    } catch (_) {
      if (_thread == null) await _load(); // 소켓이 안 열려도 대화 내용은 보여준다.
      _scheduleReconnect();
    }
  }

  void _append(ChatMessage message) {
    if (!mounted || _thread == null) return;
    setState(() => _thread = _thread!.withMessage(message));
  }

  void _scheduleReconnect() {
    if (_disposed) return;
    if (mounted && _connected) setState(() => _connected = false);
    _socket = null;
    _reconnect?.cancel();
    _reconnect = Timer(const Duration(seconds: 3), _connect);
  }

  Future<void> _send() async {
    final text = _input.text.trim();
    if (text.isEmpty || _sending) return;
    setState(() => _sending = true);
    try {
      final sent = await widget.chat.sendMessage(widget.consultationId, text);
      _input.clear();
      _append(sent);
    } on ApiException catch (e) {
      if (e.loginRequired) return _loggedOut();
      _toast(e.message);
    } catch (e, stack) {
      _toast(describeError(e, stack));
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  void _toast(String text) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _report() async {
    final message = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      showDragHandle: true,
      builder: (_) => _ReportSheet(chat: widget.chat, consultationId: widget.consultationId),
    );
    if (message != null) _toast(message);
  }

  Future<void> _block() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: Colors.white,
        title: Text('이 ${widget.chat.counterpartLabel}를 차단할까요?',
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
        content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          for (final line in const [
            '· 서로 더 이상 메시지를 주고받을 수 없어요.',
            '· 새 문의에서도 서로 연결되지 않아요.',
            "· 설정의 '차단 관리'에서 언제든 해제할 수 있어요.",
          ])
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Text(keepAll(line), style: const TextStyle(fontSize: 14, color: AppColors.zinc700)),
            ),
          const SizedBox(height: 8),
          Text(keepAll('욕설이나 금전 요구가 있었다면 🚨 신고도 함께 해주세요.'),
              style: const TextStyle(fontSize: 12, color: AppColors.zinc400)),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('취소')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppColors.red600),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('차단하기'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await widget.chat.block(widget.consultationId);
      await _load();
      _toast('차단했어요.');
    } catch (e, stack) {
      _toast(describeError(e, stack));
    }
  }

  @override
  Widget build(BuildContext context) {
    final thread = _thread;
    return Scaffold(
      backgroundColor: AppColors.zinc50,
      appBar: AppBar(
        titleSpacing: 0,
        title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(
              [widget.chat.counterpartLabel, if (thread?.counterpartDetail != null) thread!.counterpartDetail!]
                  .join(' · '),
              style: const TextStyle(fontSize: 12, color: AppColors.zinc500, fontWeight: FontWeight.w400)),
          Text(thread?.counterpartName ?? widget.counterpartName,
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
          Row(children: [
            Container(
              width: 7,
              height: 7,
              decoration: BoxDecoration(
                color: _connected ? AppColors.emerald600 : AppColors.zinc400,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: 4),
            Text(_connected ? '실시간 연결됨' : '연결 중...',
                style: const TextStyle(fontSize: 11, color: AppColors.zinc500, fontWeight: FontWeight.w400)),
          ]),
        ]),
        actions: [
          IconButton(tooltip: '신고하기', onPressed: _report, icon: const Text('🚨', style: TextStyle(fontSize: 20))),
          if (thread?.blockState != 'BLOCKED_BY_ME')
            IconButton(tooltip: '차단하기', onPressed: _block, icon: const Text('🚫', style: TextStyle(fontSize: 20))),
          const SizedBox(width: 4),
        ],
        bottom: const PreferredSize(preferredSize: Size.fromHeight(1), child: Divider()),
      ),
      body: SafeArea(
        child: Column(children: [
          if (_error != null) Padding(padding: const EdgeInsets.all(12), child: NoticeBox(_error!)),
          Expanded(
            child: thread == null
                ? const Center(child: CircularProgressIndicator(color: AppColors.orange500))
                : ListView.builder(
                    reverse: true, // 최신 메시지가 아래에 붙어 있게
                    padding: const EdgeInsets.fromLTRB(12, 16, 12, 16),
                    itemCount: thread.messages.length,
                    itemBuilder: (context, i) {
                      final message = thread.messages[thread.messages.length - 1 - i];
                      return _Bubble(message: message, mine: message.senderType == widget.chat.mySenderType);
                    },
                  ),
          ),
          if (thread != null) _composer(thread),
        ]),
      ),
    );
  }

  Widget _composer(ConsultationThread thread) {
    String? blockedText;
    if (thread.counterpartBlocked) {
      blockedText = '상대방이 이용 정지되어 더 이상 대화할 수 없어요.';
    } else if (thread.blockState == 'BLOCKED_BY_ME') {
      blockedText = "차단한 상대예요. 메시지를 주고받을 수 없어요. 설정의 '차단 관리'에서 해제할 수 있어요.";
    } else if (thread.blockState == 'BLOCKED_ME') {
      blockedText = '상대방에게 메시지를 보낼 수 없어요.';
    }

    return Container(
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: AppColors.zinc200)),
      ),
      child: blockedText != null
          ? Text(keepAll(blockedText),
              textAlign: TextAlign.center, style: const TextStyle(fontSize: 13, color: AppColors.zinc500))
          : Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
              Expanded(
                child: TextField(
                  controller: _input,
                  minLines: 1,
                  maxLines: 5,
                  textInputAction: TextInputAction.newline,
                  decoration: InputDecoration(
                      hintText: widget.chat.mySenderType == 'LAWYER' ? '답변을 입력하세요' : '메시지를 입력하세요'),
                ),
              ),
              const SizedBox(width: 8),
              SizedBox(
                height: 46,
                child: FilledButton(
                  onPressed: _sending ? null : _send,
                  style: FilledButton.styleFrom(
                    backgroundColor: AppColors.orange500,
                    shape: const StadiumBorder(),
                    padding: const EdgeInsets.symmetric(horizontal: 18),
                  ),
                  child: _sending
                      ? const SizedBox(
                          width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                      : const Text('보내기', style: TextStyle(fontWeight: FontWeight.w700)),
                ),
              ),
            ]),
    );
  }
}

class _Bubble extends StatelessWidget {
  final ChatMessage message;
  final bool mine;

  const _Bubble({required this.message, required this.mine});

  @override
  Widget build(BuildContext context) {
    final bubble = Container(
      constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.72),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: mine ? AppColors.orange500 : Colors.white,
        border: mine ? null : Border.all(color: AppColors.zinc200),
        borderRadius: BorderRadius.only(
          topLeft: const Radius.circular(16),
          topRight: const Radius.circular(16),
          bottomLeft: Radius.circular(mine ? 16 : 4),
          bottomRight: Radius.circular(mine ? 4 : 16),
        ),
      ),
      child: Text(keepAll(message.content),
          style: TextStyle(fontSize: 15, height: 1.45, color: mine ? Colors.white : AppColors.zinc900)),
    );
    final time = Text(formatMessageTime(message.createdAt),
        style: const TextStyle(fontSize: 11, color: AppColors.zinc400));

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: mine ? MainAxisAlignment.end : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: mine
            ? [time, const SizedBox(width: 6), bubble]
            : [bubble, const SizedBox(width: 6), time],
      ),
    );
  }
}

const _reportReasons = [
  ('ABUSIVE_LANGUAGE', '욕설·모욕', '욕설, 비하, 인신공격 등 모욕적인 표현'),
  ('MONEY_REQUEST', '금전 요구', '개인 계좌 입금, 선입금, 수수료 등 돈을 요구하는 행위'),
];

class _ReportSheet extends StatefulWidget {
  final ConsultationChatApi chat;
  final int consultationId;

  const _ReportSheet({required this.chat, required this.consultationId});

  @override
  State<_ReportSheet> createState() => _ReportSheetState();
}

class _ReportSheetState extends State<_ReportSheet> {
  String? _reason;
  final _detail = TextEditingController();
  bool _pending = false;
  String? _error;

  @override
  void dispose() {
    _detail.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _pending = true;
      _error = null;
    });
    try {
      final message = await widget.chat.report(
          widget.consultationId, _reason!, _detail.text.trim().isEmpty ? null : _detail.text.trim());
      if (mounted) Navigator.pop(context, message);
    } catch (e, stack) {
      setState(() => _error = describeError(e, stack));
    } finally {
      if (mounted) setState(() => _pending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 0, 20, 20 + MediaQuery.of(context).viewInsets.bottom),
      child: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Text('🚨 신고하기', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
          const SizedBox(height: 6),
          const Text('아래 기준에 해당할 때만 신고할 수 있어요.', style: TextStyle(fontSize: 14, color: AppColors.zinc500)),
          const SizedBox(height: 16),
          for (final (value, title, description) in _reportReasons)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: InkWell(
                borderRadius: BorderRadius.circular(12),
                onTap: () => setState(() => _reason = value),
                child: Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: _reason == value ? AppColors.orange50 : Colors.white,
                    border: Border.all(color: _reason == value ? AppColors.orange400 : AppColors.zinc200),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(children: [
                    Icon(_reason == value ? Icons.radio_button_checked : Icons.radio_button_off,
                        color: _reason == value ? AppColors.orange500 : AppColors.zinc400, size: 20),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(title, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
                        Text(keepAll(description), style: const TextStyle(fontSize: 12, color: AppColors.zinc500)),
                      ]),
                    ),
                  ]),
                ),
              ),
            ),
          const SizedBox(height: 8),
          TextField(
            controller: _detail,
            minLines: 2,
            maxLines: 4,
            decoration: const InputDecoration(hintText: '상황을 간단히 적어주세요 (선택)'),
          ),
          const SizedBox(height: 8),
          Text(keepAll('관리자가 대화 내용을 직접 확인한 뒤 이용 정지 여부를 결정해요.'),
              style: const TextStyle(fontSize: 12, color: AppColors.zinc400)),
          if (_error != null) ...[const SizedBox(height: 12), NoticeBox(_error!)],
          const SizedBox(height: 16),
          PillButton(
            label: _pending ? '접수 중...' : '신고하기',
            loading: _pending,
            onPressed: _reason == null ? null : _submit,
            color: AppColors.red600,
          ),
        ]),
      ),
    );
  }
}

const _policyHiddenUntilKey = 'chat_policy_hidden_until';

/// 대화방에 들어올 때 보여주는 상담 이용 안내 (웹과 같은 내용). "일주일 동안 안 보기"는 이 기기에만 저장된다.
Future<void> showChatPolicyNoticeIfNeeded(BuildContext context) async {
  SharedPreferences? prefs;
  try {
    prefs = await SharedPreferences.getInstance();
    final hiddenUntil = prefs.getInt(_policyHiddenUntilKey);
    if (hiddenUntil != null && DateTime.now().millisecondsSinceEpoch < hiddenUntil) return;
  } catch (_) {
    // 저장소를 못 읽으면 안내를 다시 보여준다.
  }
  if (!context.mounted) return;

  await showDialog<void>(
    context: context,
    builder: (context) => AlertDialog(
      backgroundColor: Colors.white,
      title: const Text('상담 이용 안내', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
      content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(keepAll('서로 존중하는 상담을 위해 아래 행위는 금지돼요.'),
            style: const TextStyle(fontSize: 14, color: AppColors.zinc600)),
        const SizedBox(height: 12),
        for (final (_, title, description) in _reportReasons)
          Container(
            width: double.infinity,
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(color: AppColors.zinc50, borderRadius: BorderRadius.circular(10)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(title, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
              Text(keepAll(description), style: const TextStyle(fontSize: 12, color: AppColors.zinc500)),
            ]),
          ),
        const SizedBox(height: 4),
        Text(keepAll('위반하면 상대방이 신고할 수 있고, 관리자가 대화 내용을 확인한 뒤 이용을 정지해요. 정지되면 즉시 로그아웃되고 다시 로그인할 수 없어요.'),
            style: const TextStyle(fontSize: 13, height: 1.5, color: AppColors.zinc700)),
        const SizedBox(height: 8),
        Text(keepAll('이런 일을 겪으셨다면 대화방 오른쪽 위 🚨 버튼으로 신고해주세요.'),
            style: const TextStyle(fontSize: 12, color: AppColors.zinc400)),
      ]),
      actions: [
        TextButton(
          onPressed: () {
            try {
              prefs?.setInt(_policyHiddenUntilKey,
                  DateTime.now().add(const Duration(days: 7)).millisecondsSinceEpoch);
            } catch (_) {
              // 저장이 막혀 있어도 이번에는 닫는다.
            }
            Navigator.pop(context);
          },
          child: const Text('일주일 동안 안 보기', style: TextStyle(color: AppColors.zinc500)),
        ),
        FilledButton(
          style: FilledButton.styleFrom(backgroundColor: AppColors.orange500),
          onPressed: () => Navigator.pop(context),
          child: const Text('확인'),
        ),
      ],
    ),
  );
}
