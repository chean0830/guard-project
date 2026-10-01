import 'dart:io';

import 'package:flutter/material.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

import '../../api/admin_api.dart';
import '../../api/api_client.dart';
import '../../theme.dart';
import 'admin_common.dart';

const _statusBadge = {
  'PENDING': ('승인 대기', AppColors.amber50, AppColors.amber900),
  'APPROVED': ('승인됨', AppColors.emerald50, AppColors.emerald900),
  'REJECTED': ('거절됨', AppColors.red50, AppColors.red800),
};

/// 변호사 가입 승인 (웹 관리자 > 변호사 승인): 자격 서류 확인 후 승인·거절. 결과는 변호사에게 메일로 간다.
class AdminLawyerScreen extends StatefulWidget {
  final AdminApi adminApi;
  final VoidCallback onLoggedOut;

  const AdminLawyerScreen({super.key, required this.adminApi, required this.onLoggedOut});

  @override
  State<AdminLawyerScreen> createState() => _AdminLawyerScreenState();
}

class _AdminLawyerScreenState extends State<AdminLawyerScreen> {
  String _status = 'PENDING';
  List<AdminLawyer>? _lawyers;
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
      final lawyers = await widget.adminApi.lawyers(status);
      if (mounted && status == _status) {
        setState(() {
          _lawyers = lawyers;
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
      _lawyers = null;
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

  Future<void> _approve(AdminLawyer lawyer) async {
    final ok = await confirmDialog(context,
        title: '가입을 승인할까요?',
        message: '${lawyer.name} 변호사의 가입을 승인하면 바로 로그인해 상담에 답변할 수 있어요. 승인 메일이 발송돼요.',
        confirmLabel: '승인',
        color: AppColors.emerald600);
    if (ok) await _run(lawyer.id, () => widget.adminApi.approveLawyer(lawyer.id), '승인했어요.');
  }

  Future<void> _reject(AdminLawyer lawyer) async {
    final reason = await reasonDialog(context,
        title: '가입 신청 거절', hint: '거절 사유 (선택) — 변호사에게 메일로 전달돼요.', confirmLabel: '거절 확정');
    if (reason != null) await _run(lawyer.id, () => widget.adminApi.rejectLawyer(lawyer.id, reason), '거절했어요.');
  }

  Future<void> _revoke(AdminLawyer lawyer) async {
    final ok = await confirmDialog(context,
        title: '승인 취소',
        message: '정말로 ${lawyer.name} 변호사의 승인을 취소하시겠습니까?\n\n'
            '승인 대기로 돌아가고 바로 로그아웃되며, 다시 승인할 때까지 로그인과 새 상담 배정이 막혀요.',
        confirmLabel: '승인 취소');
    if (ok) {
      await _run(lawyer.id, () => widget.adminApi.revokeLawyer(lawyer.id), '승인을 취소했어요. 승인 대기 탭에서 다시 처리할 수 있어요.');
    }
  }

  /// 서류를 내려받아 기기의 기본 앱(PDF 뷰어·갤러리)으로 연다.
  Future<void> _openDocument(AdminLawyer lawyer, AdminLawyerDocument doc) async {
    try {
      final file = await widget.adminApi.lawyerDocument(lawyer.id, doc.id);
      final dir = await getTemporaryDirectory();
      final safeName = doc.fileName.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_');
      final path = '${dir.path}/lawyer_${lawyer.id}_${doc.id}_$safeName';
      await File(path).writeAsBytes(file.bytes, flush: true);
      final result = await OpenFilex.open(path, type: file.contentType.split(';').first);
      if (result.type != ResultType.done) _toast('서류를 열 수 있는 앱이 없어요. (${result.message})');
    } catch (e, stack) {
      _handleError(e, stack);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AdminListView(
      title: '변호사 승인',
      description: '제출된 자격 서류를 확인한 뒤 승인하거나 거절하세요. 결과는 변호사에게 메일로 안내돼요.',
      filters: FilterChips<String>(
        options: const [('PENDING', '승인 대기'), ('APPROVED', '승인됨'), ('REJECTED', '거절됨')],
        selected: _status,
        onSelected: _select,
      ),
      error: _error,
      loading: _lawyers == null && _error == null,
      empty: _lawyers != null && _lawyers!.isEmpty,
      emptyText: '해당 상태의 신청이 없어요.',
      onRefresh: _load,
      children: [for (final l in _lawyers ?? const <AdminLawyer>[]) _tile(l)],
    );
  }

  Widget _tile(AdminLawyer lawyer) {
    final (label, bg, fg) = _statusBadge[lawyer.status] ?? (lawyer.status, AppColors.zinc100, AppColors.zinc600);
    final busy = _busyId == lawyer.id;
    return AdminCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Flexible(child: Text(lawyer.name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700))),
          const SizedBox(width: 6),
          StatusBadge(label, background: bg, foreground: fg),
        ]),
        const SizedBox(height: 2),
        Text(lawyer.email, style: const TextStyle(fontSize: 13, color: AppColors.zinc500)),
        const SizedBox(height: 6),
        InfoRow('소속', lawyer.lawFirm ?? '-'),
        InfoRow('등록번호', lawyer.barNumber),
        InfoRow('신청일', formatDateTime(lawyer.createdAt)),
        if (lawyer.status == 'REJECTED') InfoRow('거절 사유', lawyer.rejectionReason ?? '-'),
        if (lawyer.documents.isNotEmpty) ...[
          const SizedBox(height: 10),
          Wrap(spacing: 6, runSpacing: 6, children: [
            for (final doc in lawyer.documents)
              ActionChip(
                avatar: Icon(
                  doc.contentType.contains('pdf') ? Icons.picture_as_pdf_outlined : Icons.image_outlined,
                  size: 16,
                  color: AppColors.zinc600,
                ),
                label: Text(doc.fileName, style: const TextStyle(fontSize: 12, color: AppColors.zinc700)),
                backgroundColor: Colors.white,
                side: const BorderSide(color: AppColors.zinc300),
                shape: const StadiumBorder(),
                onPressed: () => _openDocument(lawyer, doc),
              ),
          ]),
        ],
        if (lawyer.status == 'APPROVED') ...[
          const SizedBox(height: 12),
          smallPill(busy ? '처리 중...' : '승인 취소', busy ? null : () => _revoke(lawyer), color: AppColors.red600),
        ],
        if (lawyer.status == 'PENDING') ...[
          const SizedBox(height: 12),
          Row(children: [
            smallPill(busy ? '처리 중...' : '승인', busy ? null : () => _approve(lawyer),
                color: AppColors.emerald600, filled: true),
            const SizedBox(width: 8),
            smallPill('거절', busy ? null : () => _reject(lawyer), color: AppColors.red600),
          ]),
        ],
      ]),
    );
  }
}
