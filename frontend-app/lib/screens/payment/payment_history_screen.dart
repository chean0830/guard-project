import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/consultation_api.dart' show formatRelativeTime;
import '../../api/payment_api.dart';
import '../../theme.dart';
import '../../widgets/common.dart';

const _statusLabel = {
  'READY': '결제 대기',
  'PAID': '결제 완료',
  'FAILED': '결제 실패',
  'CANCELED': '결제 취소',
  'REFUND_REQUESTED': '환불 검토 중',
  'REFUNDED': '환불 완료',
};

/// 결제 내역 (웹 /consult/payments). 쓰지 않은 이용권은 바로 결제 취소, 이미 쓴 이용권은 환불 요청만 할 수 있다.
class PaymentHistoryScreen extends StatefulWidget {
  final PaymentApi paymentApi;

  const PaymentHistoryScreen({super.key, required this.paymentApi});

  @override
  State<PaymentHistoryScreen> createState() => _PaymentHistoryScreenState();
}

class _PaymentHistoryScreenState extends State<PaymentHistoryScreen> {
  List<PaymentHistoryItem>? _items;
  String? _error;
  String? _busyOrderId;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final items = await widget.paymentApi.history();
      setState(() {
        _items = items;
        _error = null;
      });
    } catch (e, stack) {
      setState(() => _error = describeError(e, stack));
    }
  }

  void _toast(String text) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  void _replace(PaymentHistoryItem updated) =>
      setState(() => _items = [for (final i in _items!) i.orderId == updated.orderId ? updated : i]);

  Future<void> _cancel(PaymentHistoryItem item) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: Colors.white,
        title: const Text('결제를 취소할까요?', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
        content: Text(keepAll('${formatWon(item.amount)} 결제를 취소할까요? 이용권 1장이 사라지고 결제 금액이 돌려받아져요.')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('닫기')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppColors.red600),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('결제 취소'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    setState(() => _busyOrderId = item.orderId);
    try {
      _replace(await widget.paymentApi.cancel(item.orderId));
      _toast('결제를 취소했어요.');
    } catch (e, stack) {
      _toast(describeError(e, stack));
    } finally {
      if (mounted) setState(() => _busyOrderId = null);
    }
  }

  Future<void> _requestRefund(PaymentHistoryItem item) async {
    final reason = TextEditingController();
    final send = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: Colors.white,
        title: const Text('환불 요청', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
        content: TextField(
          controller: reason,
          minLines: 3,
          maxLines: 5,
          decoration: const InputDecoration(hintText: '환불을 요청하는 이유를 적어주세요. (예: 변호사 답변을 받지 못했어요)'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('취소')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppColors.zinc900),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('환불 요청 보내기'),
          ),
        ],
      ),
    );
    final text = reason.text.trim();
    reason.dispose();
    if (send != true) return;
    if (text.isEmpty) return _toast('환불을 요청하는 이유를 적어주세요.');
    setState(() => _busyOrderId = item.orderId);
    try {
      _replace(await widget.paymentApi.requestRefund(item.orderId, text));
      _toast('환불 요청을 보냈어요. 관리자가 확인한 뒤 처리돼요.');
    } catch (e, stack) {
      _toast(describeError(e, stack));
    } finally {
      if (mounted) setState(() => _busyOrderId = null);
    }
  }

  String _usage(PaymentHistoryItem item) {
    if (item.status != 'PAID' && item.status != 'REFUND_REQUESTED' && item.status != 'REFUNDED') return '';
    if (item.used) return item.productType == 'ANALYSIS' ? '분석에 사용함' : '상담에 사용함';
    return item.productType == 'ANALYSIS' ? '미사용 · 등기부 분석에서 자동으로 쓰여요' : '미사용 · 변호사 고르러 가기';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.zinc50,
      appBar: brandAppBar(showBack: true),
      body: SafeArea(
        child: RefreshIndicator(
          color: AppColors.orange500,
          onRefresh: _load,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 28, 16, 32),
            children: [
              const Text('결제 내역', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              Text(keepAll('· 쓰지 않은 이용권은 바로 결제 취소할 수 있어요.'),
                  style: const TextStyle(fontSize: 13, color: AppColors.zinc500, height: 1.5)),
              Text(keepAll('· 이미 상담에 쓴 이용권은 환불 요청 후 관리자가 확인해 승인하면 환불돼요.'),
                  style: const TextStyle(fontSize: 13, color: AppColors.zinc500, height: 1.5)),
              const SizedBox(height: 20),
              if (_error != null) NoticeBox(_error!),
              if (_items == null && _error == null)
                const Padding(
                  padding: EdgeInsets.only(top: 40),
                  child: Center(child: CircularProgressIndicator(color: AppColors.orange500)),
                )
              else if (_items != null && _items!.isEmpty)
                const Padding(
                  padding: EdgeInsets.only(top: 40),
                  child: Text('결제 내역이 없어요.',
                      textAlign: TextAlign.center, style: TextStyle(fontSize: 14, color: AppColors.zinc500)),
                )
              else if (_items != null)
                for (final item in _items!) _tile(item),
            ],
          ),
        ),
      ),
    );
  }

  Widget _tile(PaymentHistoryItem item) {
    final busy = _busyOrderId == item.orderId;
    final statusColor = switch (item.status) {
      'PAID' => AppColors.emerald600,
      'REFUND_REQUESTED' => AppColors.amber500,
      'FAILED' => AppColors.red600,
      _ => AppColors.zinc500,
    };
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: AppColors.zinc200),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Expanded(child: Text(item.orderName, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600))),
          Text(_statusLabel[item.status] ?? item.status,
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: statusColor)),
        ]),
        const SizedBox(height: 4),
        Text(
          [formatWon(item.amount), if (item.paidAt != null) formatRelativeTime(item.paidAt!)].join(' · '),
          style: const TextStyle(fontSize: 13, color: AppColors.zinc600),
        ),
        if (_usage(item).isNotEmpty) ...[
          const SizedBox(height: 2),
          Text(_usage(item), style: const TextStyle(fontSize: 12, color: AppColors.zinc500)),
        ],
        if (item.refundReason != null) ...[
          const SizedBox(height: 4),
          Text('환불 사유: ${item.refundReason}', style: const TextStyle(fontSize: 12, color: AppColors.zinc500)),
        ],
        if (item.refundRejectedReason != null) ...[
          const SizedBox(height: 4),
          Text('환불 요청이 거절됐어요: ${item.refundRejectedReason}',
              style: const TextStyle(fontSize: 12, color: AppColors.red600)),
        ],
        const SizedBox(height: 2),
        Text('주문번호 ${item.orderId}', style: const TextStyle(fontSize: 11, color: AppColors.zinc400)),
        if (item.canCancel || item.canRequestRefund) ...[
          const SizedBox(height: 10),
          Align(
            alignment: Alignment.centerRight,
            child: OutlinedButton(
              onPressed: busy ? null : () => item.canCancel ? _cancel(item) : _requestRefund(item),
              style: OutlinedButton.styleFrom(
                foregroundColor: item.canCancel ? AppColors.red600 : AppColors.zinc700,
                side: BorderSide(color: item.canCancel ? const Color(0xFFFCA5A5) : AppColors.zinc300),
                shape: const StadiumBorder(),
              ),
              child: Text(busy
                  ? (item.canCancel ? '취소 중...' : '요청 중...')
                  : (item.canCancel ? '결제 취소' : '환불 요청')),
            ),
          ),
        ],
      ]),
    );
  }
}
