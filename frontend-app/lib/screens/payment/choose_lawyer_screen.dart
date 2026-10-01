import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/member_api.dart';
import '../../api/payment_api.dart';
import '../../theme.dart';
import '../../widgets/common.dart';
import '../consultation/consultation_thread_screen.dart';
import 'toss_payment_screen.dart';

/// 원하는 변호사 직접 선택 (웹 /consult/choose). 이용권(1회 2,900원)이 있어야 변호사 목록을 볼 수 있고,
/// 한 명을 골라 첫 문의를 보내면 이용권 1장이 쓰이고 그 변호사와 대화방이 열린다.
class ChooseLawyerScreen extends StatefulWidget {
  final PaymentApi paymentApi;
  final MemberApi memberApi;
  final VoidCallback onLoggedOut;

  const ChooseLawyerScreen({super.key, required this.paymentApi, required this.memberApi, required this.onLoggedOut});

  @override
  State<ChooseLawyerScreen> createState() => _ChooseLawyerScreenState();
}

class _ChooseLawyerScreenState extends State<ChooseLawyerScreen> {
  int? _credits;
  int _price = 2900;
  List<LawyerCard>? _lawyers;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final c = await widget.paymentApi.lawyerSelectionCredits();
      final lawyers = c.credits > 0 ? await widget.paymentApi.directory() : null;
      setState(() {
        _credits = c.credits;
        _price = c.price;
        _lawyers = lawyers;
        _error = null;
      });
    } on ApiException catch (e) {
      if (e.loginRequired) return widget.onLoggedOut();
      setState(() => _error = e.message);
    } catch (e, stack) {
      setState(() => _error = describeError(e, stack));
    }
  }

  Future<void> _pay() async {
    final outcome = await TossPaymentScreen.open(context, widget.paymentApi, 'LAWYER_SELECTION');
    if (outcome == PaymentOutcome.paid) await _load();
  }

  Future<void> _choose(LawyerCard lawyer) async {
    final consultationId = await showModalBottomSheet<int>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      showDragHandle: true,
      builder: (_) => _FirstMessageSheet(paymentApi: widget.paymentApi, lawyer: lawyer),
    );
    if (consultationId == null || !mounted) return;
    await Navigator.of(context).pushReplacement(MaterialPageRoute<void>(
      builder: (_) => ConsultationThreadScreen(
        chat: widget.memberApi.chat,
        consultationId: consultationId,
        counterpartName: lawyer.name,
        onLoggedOut: widget.onLoggedOut,
      ),
    ));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.zinc50,
      appBar: brandAppBar(showBack: true),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 28, 16, 32),
          children: [
            const Text('원하는 변호사 선택', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            Text(keepAll('입점 변호사의 강점·경력·수임료를 보고 직접 골라 상담할 수 있어요. 1회 ${formatWon(_price)}이에요.'),
                style: const TextStyle(fontSize: 14, color: AppColors.zinc500, height: 1.5)),
            const SizedBox(height: 20),
            if (_error != null) ...[NoticeBox(_error!), const SizedBox(height: 12)],
            if (_credits == null && _error == null)
              const Padding(
                padding: EdgeInsets.only(top: 40),
                child: Center(child: CircularProgressIndicator(color: AppColors.orange500)),
              )
            else if (_credits == 0)
              _payCard()
            else if (_lawyers != null) ...[
              Text('이용권 $_credits장 남음 · 한 분을 골라 첫 문의를 보내주세요.',
                  style: const TextStyle(fontSize: 13, color: AppColors.emerald600, fontWeight: FontWeight.w600)),
              const SizedBox(height: 12),
              if (_lawyers!.isEmpty)
                const Text('지금 상담 가능한 변호사가 없어요.', style: TextStyle(fontSize: 14, color: AppColors.zinc500)),
              for (final l in _lawyers!) _LawyerTile(lawyer: l, onChoose: () => _choose(l)),
            ],
          ],
        ),
      ),
    );
  }

  Widget _payCard() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: AppColors.zinc200),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const Icon(Icons.lock_outline, size: 32, color: AppColors.zinc400),
        const SizedBox(height: 10),
        Text(keepAll('변호사 목록은 결제 후 확인할 수 있어요.'),
            textAlign: TextAlign.center, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
        const SizedBox(height: 4),
        Text(keepAll('변호사를 고르지 않은 이용권은 결제 내역에서 바로 취소할 수 있어요.'),
            textAlign: TextAlign.center, style: const TextStyle(fontSize: 13, color: AppColors.zinc500)),
        const SizedBox(height: 16),
        PillButton(label: '${formatWon(_price)} 결제하고 변호사 보기', onPressed: _pay),
      ]),
    );
  }
}

/// 이름 바로 아래에 강점(한 줄 강점·경력·수임료·주요 실적)을 보여줘 비교해서 고를 수 있게 한다 (웹과 같은 구성).
class _LawyerTile extends StatelessWidget {
  final LawyerCard lawyer;
  final VoidCallback onChoose;

  const _LawyerTile({required this.lawyer, required this.onChoose});

  @override
  Widget build(BuildContext context) {
    final achievements = (lawyer.achievements ?? '').split('\n').map((s) => s.trim()).where((s) => s.isNotEmpty);
    final hasStrengths = lawyer.headline != null || lawyer.careerYears != null || lawyer.feeInfo != null;
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: AppColors.zinc200),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Expanded(
            child: Text([('${lawyer.name} 변호사'), if (lawyer.lawFirm != null) lawyer.lawFirm!].join(' · '),
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
          ),
          if (lawyer.careerYears != null)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(color: AppColors.zinc100, borderRadius: BorderRadius.circular(99)),
              child: Text('경력 ${lawyer.careerYears}년', style: const TextStyle(fontSize: 11, color: AppColors.zinc600)),
            ),
        ]),
        if (lawyer.headline != null) ...[
          const SizedBox(height: 4),
          Text(keepAll(lawyer.headline!),
              style: const TextStyle(fontSize: 14, color: AppColors.orange700, fontWeight: FontWeight.w600)),
        ],
        if (!hasStrengths) ...[
          const SizedBox(height: 4),
          const Text('아직 강점을 등록하지 않은 변호사예요.', style: TextStyle(fontSize: 12, color: AppColors.zinc400)),
        ],
        if (lawyer.specialties != null) ...[
          const SizedBox(height: 8),
          Text('전문 분야 · ${lawyer.specialties}', style: const TextStyle(fontSize: 13, color: AppColors.zinc600)),
        ],
        if (lawyer.feeInfo != null) ...[
          const SizedBox(height: 4),
          Text('수임료 · ${lawyer.feeInfo}', style: const TextStyle(fontSize: 13, color: AppColors.zinc600)),
        ],
        if (achievements.isNotEmpty) ...[
          const SizedBox(height: 8),
          const Text('주요 실적', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.zinc700)),
          for (final a in achievements)
            Text('✓ $a', style: const TextStyle(fontSize: 12, color: AppColors.zinc600, height: 1.5)),
        ],
        if (lawyer.introduction != null) ...[
          const SizedBox(height: 8),
          Text(keepAll(lawyer.introduction!),
              maxLines: 3, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13, color: AppColors.zinc500)),
        ],
        const SizedBox(height: 12),
        PillButton(label: '이 변호사에게 문의하기', onPressed: onChoose, height: 44),
      ]),
    );
  }
}

class _FirstMessageSheet extends StatefulWidget {
  final PaymentApi paymentApi;
  final LawyerCard lawyer;

  const _FirstMessageSheet({required this.paymentApi, required this.lawyer});

  @override
  State<_FirstMessageSheet> createState() => _FirstMessageSheetState();
}

class _FirstMessageSheetState extends State<_FirstMessageSheet> {
  final _message = TextEditingController();
  bool _pending = false;
  String? _error;

  @override
  void dispose() {
    _message.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _pending = true;
      _error = null;
    });
    try {
      final id = await widget.paymentApi.startDirectConsultation(widget.lawyer.id, _message.text.trim());
      if (mounted) Navigator.pop(context, id);
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
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text('${widget.lawyer.name} 변호사님께 문의', style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w700)),
        const SizedBox(height: 6),
        const Text('변호사님께 보낼 첫 문의를 적어주세요.', style: TextStyle(fontSize: 14, color: AppColors.zinc500)),
        const SizedBox(height: 12),
        TextField(
          controller: _message,
          minLines: 4,
          maxLines: 8,
          onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(hintText: '상황을 자세히 적어주시면 더 정확한 답변을 받을 수 있어요.'),
        ),
        if (_error != null) ...[const SizedBox(height: 12), NoticeBox(_error!)],
        const SizedBox(height: 16),
        PillButton(
          label: _pending ? '등록 중...' : '이용권 1장 쓰고 문의 보내기',
          loading: _pending,
          onPressed: _message.text.trim().isEmpty ? null : _submit,
        ),
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('취소')),
      ]),
    );
  }
}
