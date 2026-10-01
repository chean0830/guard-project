import 'package:flutter/material.dart';

import '../../api/admin_api.dart';
import '../../api/api_client.dart';
import '../../api/payment_api.dart' show formatWon;
import '../../theme.dart';
import 'admin_common.dart';

const _statusLabel = {
  'READY': '결제 대기',
  'PAID': '결제 완료',
  'FAILED': '결제 실패',
  'CANCELED': '즉시 환불',
  'REFUND_REQUESTED': '환불 요청',
  'REFUNDED': '환불 완료',
};

/// 결제 관리 (웹 관리자 > 결제 관리): 이미 쓴 이용권의 환불 요청을 승인(토스 결제 취소)하거나 거절한다.
class AdminPaymentScreen extends StatefulWidget {
  final AdminApi adminApi;
  final VoidCallback onLoggedOut;

  const AdminPaymentScreen({super.key, required this.adminApi, required this.onLoggedOut});

  @override
  State<AdminPaymentScreen> createState() => _AdminPaymentScreenState();
}

class _AdminPaymentScreenState extends State<AdminPaymentScreen> {
  /// ''는 전체 결제.
  String _status = 'REFUND_REQUESTED';
  List<AdminPayment>? _items;
  String? _error;
  String? _busyOrderId;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final status = _status;
    try {
      final items = await widget.adminApi.payments(status.isEmpty ? null : status);
      if (mounted && status == _status) {
        setState(() {
          _items = items;
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
      _items = null;
      _error = null;
    });
    _load();
  }

  void _toast(String text) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _run(String orderId, Future<void> Function() action, String done) async {
    setState(() => _busyOrderId = orderId);
    try {
      await action();
      _toast(done);
      await _load();
    } catch (e, stack) {
      _handleError(e, stack);
    } finally {
      if (mounted) setState(() => _busyOrderId = null);
    }
  }

  Future<void> _approve(AdminPayment item) async {
    final ok = await confirmDialog(context,
        title: '환불할까요?',
        message: '${item.userEmail}의 ${formatWon(item.payment.amount)} 결제를 환불할까요? 토스에서 결제가 취소돼요.',
        confirmLabel: '환불 승인',
        color: AppColors.emerald600);
    final orderId = item.payment.orderId;
    if (ok) await _run(orderId, () => widget.adminApi.approveRefund(orderId), '환불했어요.');
  }

  Future<void> _reject(AdminPayment item) async {
    final reason = await reasonDialog(context,
        title: '환불 요청 거절', hint: '거절 사유 — 회원 결제 내역에 표시돼요.', confirmLabel: '거절');
    final orderId = item.payment.orderId;
    if (reason != null) await _run(orderId, () => widget.adminApi.rejectRefund(orderId, reason), '환불 요청을 거절했어요.');
  }

  @override
  Widget build(BuildContext context) {
    return AdminListView(
      title: '결제 관리',
      description: '쓰지 않은 이용권은 회원이 바로 환불해요(즉시 환불 탭). 이미 쓴 이용권의 환불 요청만 여기서 승인·거절해요.',
      filters: FilterChips<String>(
        options: const [('REFUND_REQUESTED', '환불 요청'), ('', '전체 결제'), ('REFUNDED', '환불 완료'), ('CANCELED', '즉시 환불')],
        selected: _status,
        onSelected: _select,
      ),
      error: _error,
      loading: _items == null && _error == null,
      empty: _items != null && _items!.isEmpty,
      emptyText: '해당하는 결제가 없어요.',
      onRefresh: _load,
      children: [for (final i in _items ?? const <AdminPayment>[]) _tile(i)],
    );
  }

  Widget _tile(AdminPayment item) {
    final p = item.payment;
    final busy = _busyOrderId == p.orderId;
    final statusColor = switch (p.status) {
      'PAID' => AppColors.emerald600,
      'REFUND_REQUESTED' => AppColors.amber500,
      'FAILED' => AppColors.red600,
      _ => AppColors.zinc500,
    };
    return AdminCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Expanded(child: Text(p.orderName, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600))),
          Text(_statusLabel[p.status] ?? p.status,
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: statusColor)),
        ]),
        const SizedBox(height: 4),
        InfoRow('회원', item.userEmail),
        InfoRow('금액', '${formatWon(p.amount)} · ${p.used ? '사용함' : '미사용'}'),
        InfoRow('결제일', formatDateTime(p.paidAt)),
        if (p.canceledAt != null) InfoRow('취소일', formatDateTime(p.canceledAt)),
        if (p.refundReason != null) InfoRow('환불 사유', p.refundReason!),
        if (p.refundRejectedReason != null) InfoRow('거절 사유', p.refundRejectedReason!),
        InfoRow('주문번호', p.orderId),
        if (p.status == 'REFUND_REQUESTED') ...[
          const SizedBox(height: 12),
          Row(children: [
            smallPill(busy ? '처리 중...' : '환불 승인', busy ? null : () => _approve(item),
                color: AppColors.emerald600, filled: true),
            const SizedBox(width: 8),
            smallPill('거절', busy ? null : () => _reject(item), color: AppColors.red600),
          ]),
        ],
      ]),
    );
  }
}
