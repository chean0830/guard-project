import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/lawyer_api.dart';
import '../../theme.dart';
import '../../widgets/common.dart';
import '../admin/admin_common.dart' show formatDateTime;

/// 변호사의 관리자 메시지 알림함 (읽기 전용). 열면 안 읽은 메시지가 모두 읽음 처리된다.
class LawyerAdminMessagesScreen extends StatefulWidget {
  final LawyerApi lawyerApi;
  final VoidCallback onLoggedOut;

  const LawyerAdminMessagesScreen({super.key, required this.lawyerApi, required this.onLoggedOut});

  @override
  State<LawyerAdminMessagesScreen> createState() => _LawyerAdminMessagesScreenState();
}

class _LawyerAdminMessagesScreenState extends State<LawyerAdminMessagesScreen> {
  List<AdminMessageItem>? _messages;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      // 먼저 목록을 받아 안 읽음 표시를 보여준 뒤 읽음 처리한다 (이번에 새로 온 것이 무엇인지 보이게).
      final messages = await widget.lawyerApi.adminMessages();
      if (mounted) {
        setState(() {
          _messages = messages;
          _error = null;
        });
      }
      if (messages.any((m) => m.readAt == null)) await widget.lawyerApi.markAdminMessagesRead();
    } catch (e, stack) {
      if (e is ApiException && e.loginRequired) {
        if (mounted) Navigator.of(context).popUntil((route) => route.isFirst);
        return widget.onLoggedOut();
      }
      if (mounted) setState(() => _error = describeError(e, stack));
    }
  }

  @override
  Widget build(BuildContext context) {
    final messages = _messages;
    return Scaffold(
      backgroundColor: AppColors.zinc50,
      appBar: brandAppBar(showBack: true),
      body: SafeArea(
        child: RefreshIndicator(
          color: AppColors.orange500,
          onRefresh: _load,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 28, 16, 32),
            children: [
              const Text('관리자 메시지', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
              const SizedBox(height: 6),
              Text(keepAll('Project Guard 운영팀이 보낸 안내예요. 문의는 회신 메일로 보내주세요.'),
                  style: const TextStyle(fontSize: 13, color: AppColors.zinc500, height: 1.5)),
              const SizedBox(height: 20),
              if (_error != null) NoticeBox(_error!),
              if (messages == null && _error == null)
                const Padding(
                  padding: EdgeInsets.only(top: 40),
                  child: Center(child: CircularProgressIndicator(color: AppColors.orange500)),
                )
              else if (messages != null && messages.isEmpty)
                const Padding(
                  padding: EdgeInsets.only(top: 40),
                  child: Text('받은 메시지가 없어요.',
                      textAlign: TextAlign.center, style: TextStyle(fontSize: 14, color: AppColors.zinc500)),
                )
              else
                for (final m in messages ?? const <AdminMessageItem>[])
                  Container(
                    margin: const EdgeInsets.only(bottom: 10),
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      border: Border.all(color: m.readAt == null ? AppColors.orange400 : AppColors.zinc200),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Row(children: [
                        if (m.readAt == null) ...[
                          Container(
                            width: 7,
                            height: 7,
                            decoration: const BoxDecoration(color: AppColors.orange500, shape: BoxShape.circle),
                          ),
                          const SizedBox(width: 6),
                        ],
                        Text(formatDateTime(m.createdAt), style: const TextStyle(fontSize: 12, color: AppColors.zinc500)),
                      ]),
                      const SizedBox(height: 6),
                      Text(m.content, style: const TextStyle(fontSize: 14, height: 1.5)),
                    ]),
                  ),
            ],
          ),
        ),
      ),
    );
  }
}
