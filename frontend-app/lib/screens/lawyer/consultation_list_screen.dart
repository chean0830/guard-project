import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/lawyer_api.dart';
import '../../theme.dart';
import '../../widgets/common.dart';
import 'consultation_thread_screen.dart';
import 'lawyer_settings_screen.dart';

/// 변호사 로그인 후 첫 화면: 배정된 상담 문의 목록.
class ConsultationListScreen extends StatefulWidget {
  final ApiClient api;
  final Session session;
  final VoidCallback onLoggedOut;

  const ConsultationListScreen({super.key, required this.api, required this.session, required this.onLoggedOut});

  @override
  State<ConsultationListScreen> createState() => _ConsultationListScreenState();
}

class _ConsultationListScreenState extends State<ConsultationListScreen> {
  late final LawyerApi _lawyerApi = LawyerApi(widget.api);
  List<ConsultationSummary>? _items;
  String? _error;

  WebSocket? _socket;
  Timer? _reconnect;
  bool _connected = false;
  bool _disposed = false;

  /// 실시간 목록: 새 문의·새 메시지가 생기면 서버가 소켓으로 알려주고, 그때 목록을 다시 불러온다.
  /// 주기적인 자동 갱신은 하지 않는다.
  @override
  void initState() {
    super.initState();
    _connect();
  }

  @override
  void dispose() {
    _disposed = true;
    _reconnect?.cancel();
    _socket?.close();
    super.dispose();
  }

  Future<void> _connect() async {
    if (_disposed) return;
    try {
      final socket = await _lawyerApi.connectInbox();
      if (_disposed) {
        socket.close();
        return;
      }
      _socket = socket;
      socket.listen(
        (data) {
          if (LawyerApi.isInboxEvent(data)) _load();
        },
        onDone: _scheduleReconnect,
        onError: (_) => _scheduleReconnect(),
        cancelOnError: true,
      );
      if (mounted) setState(() => _connected = true);
      await _load(); // 처음 들어올 때와 재연결 때, 연결 전 사이의 변화를 채운다.
    } on ApiException catch (e) {
      if (e.loginRequired) return widget.onLoggedOut();
      if (_items == null) await _load();
      _scheduleReconnect();
    } catch (_) {
      if (_items == null) await _load(); // 소켓이 안 열려도 목록은 보여준다.
      _scheduleReconnect();
    }
  }

  void _scheduleReconnect() {
    if (_disposed) return;
    if (mounted && _connected) setState(() => _connected = false);
    _socket = null;
    _reconnect?.cancel();
    _reconnect = Timer(const Duration(seconds: 3), _connect);
  }

  Future<void> _load() async {
    try {
      final items = await _lawyerApi.listConsultations();
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

  Future<void> _open(ConsultationSummary item) async {
    await Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => ConsultationThreadScreen(
        lawyerApi: _lawyerApi,
        consultationId: item.id,
        counterpartName: item.userDisplayName,
        onLoggedOut: widget.onLoggedOut,
      ),
    ));
    // 대화방에서 실시간으로 받은 메시지는 서버에 다시 묻지 않았으므로, 나올 때 한 번 읽음 처리한다.
    try {
      await _lawyerApi.thread(item.id);
    } catch (_) {
      // 읽음 처리가 실패해도 목록은 다시 불러온다.
    }
    _load();
  }

  Future<void> _logout() async {
    await widget.api.logout();
    widget.onLoggedOut();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: brandAppBar(actions: [
        IconButton(
          tooltip: '설정',
          icon: const Icon(Icons.settings_outlined, color: AppColors.zinc600),
          onPressed: () => Navigator.of(context).push(MaterialPageRoute<void>(
            builder: (_) => LawyerSettingsScreen(lawyerApi: _lawyerApi, onLoggedOut: widget.onLoggedOut),
          )),
        ),
        TextButton(
          onPressed: _logout,
          child: const Text('로그아웃', style: TextStyle(color: AppColors.zinc600, fontSize: 14)),
        ),
        const SizedBox(width: 4),
      ]),
      body: RefreshIndicator(
        color: AppColors.orange500,
        onRefresh: _load,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 28, 16, 24),
          children: [
            Row(children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(color: AppColors.zinc900, borderRadius: BorderRadius.circular(99)),
                child: const Text('변호사',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: Colors.white)),
              ),
              const SizedBox(width: 8),
              Flexible(
                child: Text('${widget.session.name ?? '변호사'}님',
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 14, color: AppColors.zinc500)),
              ),
            ]),
            const SizedBox(height: 12),
            Row(children: [
              const Text('상담 문의', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
              const SizedBox(width: 10),
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
                  style: const TextStyle(fontSize: 12, color: AppColors.zinc500)),
            ]),
            const SizedBox(height: 6),
            Text(keepAll('배정된 문의에 답변해주세요. 새 답변이 오면 회원에게 알림이 가요.'),
                style: const TextStyle(fontSize: 14, color: AppColors.zinc500, height: 1.5)),
            const SizedBox(height: 20),
            if (_error != null) ...[NoticeBox(_error!), const SizedBox(height: 12)],
            if (_items == null && _error == null)
              const Padding(
                padding: EdgeInsets.only(top: 48),
                child: Center(child: CircularProgressIndicator(color: AppColors.orange500)),
              )
            else if (_items != null && _items!.isEmpty)
              Container(
                padding: const EdgeInsets.symmetric(vertical: 48),
                decoration: BoxDecoration(
                  color: AppColors.zinc50,
                  border: Border.all(color: AppColors.zinc200),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: const Column(children: [
                  Icon(Icons.forum_outlined, size: 36, color: AppColors.zinc400),
                  SizedBox(height: 10),
                  Text('아직 들어온 문의가 없어요.', style: TextStyle(fontSize: 14, color: AppColors.zinc500)),
                ]),
              )
            else if (_items != null)
              for (final item in _items!) _ConsultationTile(item: item, onTap: () => _open(item)),
          ],
        ),
      ),
    );
  }
}

class _ConsultationTile extends StatelessWidget {
  final ConsultationSummary item;
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
              CircleAvatar(
                radius: 22,
                backgroundColor: AppColors.orange100,
                child: Text(
                  item.userDisplayName.isEmpty ? '?' : item.userDisplayName.characters.first.toUpperCase(),
                  style: const TextStyle(color: AppColors.orange700, fontWeight: FontWeight.w700),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(children: [
                    Expanded(
                      child: Text(item.userDisplayName,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(fontSize: 15, fontWeight: unread ? FontWeight.w700 : FontWeight.w600)),
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
