import 'package:flutter/material.dart';

import '../../api/admin_api.dart';
import '../../api/api_client.dart';
import '../../theme.dart';
import 'admin_common.dart';

/// 신고 관리 (웹 관리자 > 신고 관리): 대화 원문을 확인하고 기준을 충족하면 이용 정지, 아니면 기각.
class AdminReportScreen extends StatefulWidget {
  final AdminApi adminApi;
  final VoidCallback onLoggedOut;

  const AdminReportScreen({super.key, required this.adminApi, required this.onLoggedOut});

  @override
  State<AdminReportScreen> createState() => _AdminReportScreenState();
}

class _AdminReportScreenState extends State<AdminReportScreen> {
  String _status = 'PENDING';
  List<AdminReport>? _reports;
  String? _error;
  int? _busyId;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final status = _status;
    try {
      final reports = await widget.adminApi.reports(status);
      if (mounted && status == _status) {
        setState(() {
          _reports = reports;
          _error = null;
        });
      }
    } catch (e, stack) {
      _handleError(e, stack);
    }
  }

  void _handleError(Object e, StackTrace stack) {
    if (e is ApiException && e.loginRequired) return widget.onLoggedOut();
    if (mounted) setState(() => _error = describeError(e, stack));
  }

  void _select(String status) {
    setState(() {
      _status = status;
      _reports = null;
      _error = null;
    });
    _load();
  }

  void _toast(String text) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _run(int id, Future<void> Function() action, String done) async {
    setState(() => _busyId = id);
    try {
      await action();
      _toast(done);
      await _load();
    } catch (e, stack) {
      _handleError(e, stack);
    } finally {
      if (mounted) setState(() => _busyId = null);
    }
  }

  Future<void> _action(AdminReport report) async {
    final ok = await confirmDialog(context,
        title: '이용을 정지할까요?',
        message: '${partyLabel[report.targetType]} "${report.targetName}"의 이용을 정지할까요?\n'
            '정지되면 즉시 로그아웃되고 다시 로그인할 수 없어요. 같은 대상에 대한 다른 대기 신고도 함께 처리돼요.',
        confirmLabel: '이용 정지');
    if (ok) await _run(report.id, () => widget.adminApi.actionReport(report.id), '이용을 정지했어요.');
  }

  Future<void> _dismiss(AdminReport report) =>
      _run(report.id, () => widget.adminApi.dismissReport(report.id), '신고를 기각했어요.');

  void _openTranscript(AdminReport report) {
    Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => _TranscriptScreen(adminApi: widget.adminApi, report: report),
    ));
  }

  @override
  Widget build(BuildContext context) {
    return AdminListView(
      title: '신고 관리',
      description: '욕설·모욕, 금전 요구 신고가 들어와요. 대화 원문을 확인해 기준을 충족할 때만 정지 처리하세요.',
      filters: FilterChips<String>(
        options: const [('PENDING', '처리 대기'), ('ACTIONED', '정지 처리됨'), ('DISMISSED', '기각됨')],
        selected: _status,
        onSelected: _select,
      ),
      error: _error,
      loading: _reports == null && _error == null,
      empty: _reports != null && _reports!.isEmpty,
      emptyText: '해당 상태의 신고가 없어요.',
      onRefresh: _load,
      children: [for (final r in _reports ?? const <AdminReport>[]) _tile(r)],
    );
  }

  Widget _tile(AdminReport report) {
    final busy = _busyId == report.id;
    return AdminCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Wrap(spacing: 6, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
          StatusBadge(report.reasonLabel, background: AppColors.red50, foreground: AppColors.red700),
          Text('${partyLabel[report.targetType]} ${report.targetName}',
              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
          if (report.targetBlocked) const StatusBadge('정지됨', background: AppColors.zinc900, foreground: Colors.white),
        ]),
        const SizedBox(height: 6),
        InfoRow('대상', '${report.targetEmail ?? '-'} (누적 신고 ${report.targetReportCount}건)'),
        InfoRow('신고자', '${partyLabel[report.reporterType]} ${report.reporterName}'),
        InfoRow('신고일', formatDateTime(report.createdAt)),
        if (report.detail != null && report.detail!.trim().isNotEmpty) InfoRow('내용', report.detail!),
        const SizedBox(height: 12),
        Wrap(spacing: 8, runSpacing: 8, children: [
          smallPill('대화 원문 보기', () => _openTranscript(report)),
          if (report.status == 'PENDING') ...[
            smallPill(busy ? '처리 중...' : '기준 충족 · 이용 정지', busy ? null : () => _action(report),
                color: AppColors.red600, filled: true),
            smallPill('기각', busy ? null : () => _dismiss(report)),
          ],
        ]),
      ]),
    );
  }
}

/// 신고된 대화방의 원문 전체. 신고 대상이 보낸 말은 빨간 테두리로 표시한다.
class _TranscriptScreen extends StatefulWidget {
  final AdminApi adminApi;
  final AdminReport report;

  const _TranscriptScreen({required this.adminApi, required this.report});

  @override
  State<_TranscriptScreen> createState() => _TranscriptScreenState();
}

class _TranscriptScreenState extends State<_TranscriptScreen> {
  late final Future<List<ReportMessage>> _messages = widget.adminApi.reportMessages(widget.report.id);

  @override
  Widget build(BuildContext context) {
    final report = widget.report;
    return Scaffold(
      backgroundColor: AppColors.zinc50,
      appBar: AppBar(
        title: Text('대화 원문 · ${report.reasonLabel}', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
        bottom: const PreferredSize(preferredSize: Size.fromHeight(1), child: Divider()),
      ),
      body: FutureBuilder<List<ReportMessage>>(
        future: _messages,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(describeError(snapshot.error!, snapshot.stackTrace ?? StackTrace.current)),
              ),
            );
          }
          if (!snapshot.hasData) {
            return const Center(child: CircularProgressIndicator(color: AppColors.orange500));
          }
          final messages = snapshot.data!;
          if (messages.isEmpty) return const Center(child: Text('대화 내용이 없어요.'));
          return ListView.builder(
            padding: const EdgeInsets.all(16),
            itemCount: messages.length,
            itemBuilder: (context, i) {
              final m = messages[i];
              final fromTarget = m.senderType == report.targetType;
              return Align(
                alignment: fromTarget ? Alignment.centerLeft : Alignment.centerRight,
                child: Container(
                  constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.8),
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: fromTarget ? Colors.white : AppColors.zinc200,
                    border: fromTarget ? Border.all(color: const Color(0xFFFCA5A5)) : null,
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(
                      '${partyLabel[m.senderType] ?? m.senderType}${fromTarget ? ' (신고 대상)' : ''} · ${formatDateTime(m.createdAt)}',
                      style: const TextStyle(fontSize: 11, color: AppColors.zinc500),
                    ),
                    const SizedBox(height: 2),
                    Text(m.content, style: const TextStyle(fontSize: 14, height: 1.4)),
                  ]),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
