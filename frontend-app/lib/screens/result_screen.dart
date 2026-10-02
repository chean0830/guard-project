import 'package:flutter/material.dart';

import '../models/analyze_result.dart';
import '../theme.dart';
import '../widgets/common.dart';

const _ownershipTypeLabel = {
  'OWNERSHIP_PRESERVATION': '소유권보존',
  'OWNERSHIP_TRANSFER': '소유권이전',
  'OTHER': '기타',
};

const _seizureTypeLabel = {
  'SEIZURE': '압류',
  'PROVISIONAL_SEIZURE': '가압류',
  'AUCTION_COMMENCEMENT': '경매개시결정',
  'PROVISIONAL_DISPOSITION': '가처분',
};

const _sourceLabel = {
  'FACTUAL': '등기부 사실 확인',
  'LAW': '법적 기준',
  'GOVERNMENT_GUIDELINE': '정부 권고 기준(참고용)',
};

const _severityOrder = {'HIGH': 0, 'CAUTION': 1, 'INFO': 2};

String _won(int? amount) {
  if (amount == null) return '정보 없음';
  final digits = amount.toString();
  final buf = StringBuffer();
  for (var i = 0; i < digits.length; i++) {
    if (i > 0 && (digits.length - i) % 3 == 0) buf.write(',');
    buf.write(digits[i]);
  }
  return '$buf원';
}

class ResultScreen extends StatelessWidget {
  final AnalyzeResult result;
  final VoidCallback onOpenConsult;

  const ResultScreen({super.key, required this.result, required this.onOpenConsult});

  @override
  Widget build(BuildContext context) {
    final signals = [...result.riskSignals]
      ..sort((a, b) => (_severityOrder[a.severity] ?? 9).compareTo(_severityOrder[b.severity] ?? 9));
    final registry = result.registry;
    final building = result.buildingInfo;

    return Scaffold(
      appBar: brandAppBar(showBack: true),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _Verdict(hasHighRisk: result.hasHighRisk, isMultiHousehold: result.isMultiHousehold),
            if (result.hasHighRisk || result.isMultiHousehold) ...[
              const SizedBox(height: 12),
              _LawyerCta(onTap: () {
                Navigator.of(context).pop();
                onOpenConsult();
              }),
            ],
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.zinc50,
                border: Border.all(color: AppColors.zinc300),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(result.disclaimer, style: const TextStyle(fontSize: 14, color: AppColors.zinc700)),
            ),
            _Section('위험 신호'),
            if (signals.isEmpty) const _Empty('표시할 위험 신호가 없습니다.'),
            for (final s in signals) _SignalCard(signal: s),
            _Section('직접 확인해야 할 것'),
            Text(keepAll('서류만으로는 알 수 없어서, 계약 전에 직접 확인해보시는 게 좋아요.'),
                style: TextStyle(fontSize: 14, color: AppColors.zinc500)),
            const SizedBox(height: 8),
            if (result.checklist.isEmpty) const _Empty('표시할 항목이 없습니다.'),
            for (final item in result.checklist)
              Container(
                margin: const EdgeInsets.only(bottom: 8),
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  border: Border.all(color: AppColors.zinc200),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const Icon(Icons.check_circle_outline, size: 20, color: AppColors.orange500),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(item.title, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
                      const SizedBox(height: 4),
                      Text(keepAll(item.description),
                          style: const TextStyle(fontSize: 13, height: 1.5, color: AppColors.zinc600)),
                    ]),
                  ),
                ]),
              ),
            _Section('등기부 요약'),
            _InfoRow('주소', registry.address ?? '인식 실패'),
            _InfoRow('고유번호', registry.uniqueNumber ?? '인식 실패'),
            _InfoRow(
                result.isMultiHousehold ? '입력하신 건물 시세' : '조회된 시세',
                result.marketPrice != null
                    ? _won(result.marketPrice)
                    : result.isMultiHousehold
                        ? '입력 안 함'
                        : '조회 실패 (실거래 내역 없음)'),
            if (result.isMultiHousehold)
              _InfoRow(
                  result.officialHousePrice != null ? '공시가격 (${result.officialHousePrice!.year}년)' : '공시가격',
                  result.officialHousePrice != null
                      ? _won(result.officialHousePrice!.price) +
                          (result.marketPrice == null ? ' · 시세 미입력이라 이 값으로 계산 (보수적)' : '')
                      : '조회 실패'),
            _InfoRow('활성 근저당 합계', _won(registry.totalActiveMortgageAmount)),
            if (result.landRegistry != null) ...[
              _Section('토지 등기부 요약'),
              _InfoRow('토지 주소', result.landRegistry!.address ?? '인식 실패'),
              _InfoRow('토지 소유자',
                  result.landRegistry!.ownershipHistory.where((e) => !e.cancelled).lastOrNull?.ownerName ?? '인식 실패'),
              _InfoRow('토지 활성 근저당 합계', _won(result.landRegistry!.totalActiveMortgageAmount)),
              _InfoRow('말소되지 않은 압류·가압류', '${result.landRegistry!.seizures.where((s) => !s.cancelled).length}건'),
              const SizedBox(height: 4),
              Text(keepAll('건물과 토지에 같이 설정된 근저당(공동담보)은 위험 계산에서 한 번만 셌어요.'),
                  style: const TextStyle(fontSize: 12, color: AppColors.zinc500)),
            ],
            _Section('건축물대장 정보'),
            if (building == null)
              const _Empty('건축물대장에서 조회되지 않았습니다. 등록되지 않은 건물이거나 주소 조회에 실패했을 수 있어요.')
            else ...[
              _InfoRow('건물명', building.buildingName ?? '정보 없음'),
              _InfoRow('주용도', building.mainPurpose ?? '정보 없음'),
              _InfoRow('기타 용도', building.etcPurpose ?? '정보 없음'),
              if (building.familyCount != null) _InfoRow('가구수', '${building.familyCount}가구'),
              _InfoRow('구조', building.structureType ?? '정보 없음'),
              _InfoRow('사용승인일', building.useApprovalDate ?? '정보 없음'),
              _InfoRow('연면적',
                  building.totalFloorAreaSqm != null ? '${building.totalFloorAreaSqm}㎡' : '정보 없음'),
            ],
            _Section('소유권 (갑구)'),
            if (registry.ownershipHistory.isEmpty) const _Empty('인식된 소유권 항목이 없습니다.'),
            for (final e in registry.ownershipHistory)
              _EntryTile(
                label: '${e.rank}번 · ${_ownershipTypeLabel[e.type] ?? e.type}',
                detail: '소유자: ${e.ownerName ?? '정보 없음'} · 접수일: ${e.receivedDate ?? '정보 없음'}',
                cancelled: e.cancelled,
              ),
            _Section('근저당 (을구)'),
            if (registry.mortgages.isEmpty) const _Empty('인식된 근저당 항목이 없습니다.'),
            for (final e in registry.mortgages)
              _EntryTile(
                label: '${e.rank}번 · 채권최고액 ${_won(e.maxClaimAmount)}',
                detail: '채무자: ${e.debtorName ?? '정보 없음'} · 근저당권자: ${e.mortgageeName ?? '정보 없음'}'
                    ' · 접수일: ${e.receivedDate ?? '정보 없음'}',
                cancelled: e.cancelled,
              ),
            _Section('압류/가압류/경매'),
            if (registry.seizures.isEmpty) const _Empty('인식된 압류/가압류/경매 항목이 없습니다.'),
            for (final e in registry.seizures)
              _EntryTile(
                label: '${e.rank}번 · ${_seizureTypeLabel[e.type] ?? e.type}',
                detail: '접수일: ${e.receivedDate ?? '정보 없음'}',
                cancelled: e.cancelled,
              ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}

class _Verdict extends StatelessWidget {
  final bool hasHighRisk;
  final bool isMultiHousehold;

  const _Verdict({required this.hasHighRisk, required this.isMultiHousehold});

  @override
  Widget build(BuildContext context) {
    // 다가구주택은 입력값에만 의존하는 결과라, 위험 신호가 없어도 "안전"으로 보여주지 않는다.
    if (!hasHighRisk && isMultiHousehold) {
      return Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: AppColors.amber50,
          border: Border.all(color: AppColors.amber500, width: 2),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Column(children: [
          const Icon(Icons.warning_amber_rounded, size: 40, color: AppColors.amber500),
          const SizedBox(height: 8),
          const Text('조건부 결과예요. 안전하다는 뜻이 아니에요',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: AppColors.amber900)),
          const SizedBox(height: 6),
          Text(
              keepAll('다가구주택은 먼저 들어온 세입자 보증금이 등기부에 나오지 않아, 이 결과는 입력하신 선순위 보증금과 '
                  '건물 시세가 정확할 때만 의미가 있어요. 계약 전에 전입세대 열람내역서·확정일자 부여현황을 꼭 직접 확인하세요.'),
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 14, height: 1.5, color: AppColors.amber900)),
        ]),
      );
    }
    if (!hasHighRisk) {
      return Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: AppColors.emerald50,
          border: Border.all(color: AppColors.emerald300),
          borderRadius: BorderRadius.circular(16),
        ),
        child: const Column(children: [
          Icon(Icons.verified_outlined, size: 34, color: AppColors.emerald600),
          SizedBox(height: 8),
          Text('중대한 위험 신호는 발견되지 않았습니다',
              textAlign: TextAlign.center,
              style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16, color: AppColors.emerald900)),
        ]),
      );
    }
    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: AppColors.red50,
        border: Border.all(color: AppColors.red500, width: 2),
        borderRadius: BorderRadius.circular(16),
      ),
      child: const Column(children: [
        Icon(Icons.warning_amber_rounded, size: 48, color: AppColors.red600),
        SizedBox(height: 10),
        Text('위험 신호가 발견되었습니다',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800, color: AppColors.red700)),
        SizedBox(height: 6),
        Text('계약을 진행하기 전에, 아래 위험 신호를 꼭 확인해보세요.',
            textAlign: TextAlign.center, style: TextStyle(fontSize: 14, color: AppColors.red800)),
      ]),
    );
  }
}

/// 고위험일 때 변호사 상담으로 안내 (웹 분석 결과와 같은 문구).
class _LawyerCta extends StatelessWidget {
  final VoidCallback onTap;

  const _LawyerCta({required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: const Color(0xFFFCA5A5)),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text(keepAll('혹시 벌써 집을 계약하셨나요? 🏠\n혹시 보증금(전세금)을 돌려받지 못하고 계시나요? 😥'),
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, height: 1.5)),
        const SizedBox(height: 6),
        Text(keepAll('Project Guard에 등록된 변호사 중, 전세사기·부동산 사건 경험이 많은 변호사 한 분과 무작위로 매칭해드려요. ⚖️'),
            textAlign: TextAlign.center, style: const TextStyle(fontSize: 12, color: AppColors.zinc500, height: 1.5)),
        const SizedBox(height: 12),
        PillButton(label: '나에게 꼭 맞는 변호사와 무료로 상담하기 💬', onPressed: onTap, color: AppColors.red600, height: 46),
      ]),
    );
  }
}

class _SignalCard extends StatelessWidget {
  final RiskSignal signal;

  const _SignalCard({required this.signal});

  @override
  Widget build(BuildContext context) {
    final (label, color) = switch (signal.severity) {
      'HIGH' => ('위험', AppColors.red500),
      'CAUTION' => ('주의', AppColors.amber500),
      _ => ('참고', AppColors.zinc400),
    };
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        border: Border(left: BorderSide(color: color, width: 4)),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 2),
            decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(99)),
            child: Text(label,
                style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold)),
          ),
          const SizedBox(width: 8),
          Flexible(
            child: Text(_sourceLabel[signal.source] ?? signal.source,
                style: Theme.of(context).textTheme.bodySmall),
          ),
        ]),
        const SizedBox(height: 8),
        Text(signal.title, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
        const SizedBox(height: 4),
        Text(keepAll(signal.detail), style: const TextStyle(fontSize: 14, height: 1.55)),
        const SizedBox(height: 6),
        Text('출처: ${signal.sourceDescription}', style: Theme.of(context).textTheme.bodySmall),
      ]),
    );
  }
}

class _Section extends StatelessWidget {
  final String title;

  const _Section(this.title);

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 28, bottom: 10),
        child: Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
      );
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;

  const _InfoRow(this.label, this.value);

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          SizedBox(
              width: 110,
              child: Text(label, style: const TextStyle(fontSize: 14, color: AppColors.zinc500))),
          Expanded(child: Text(value, style: const TextStyle(fontSize: 14))),
        ]),
      );
}

class _EntryTile extends StatelessWidget {
  final String label;
  final String detail;
  final bool cancelled;

  const _EntryTile({required this.label, required this.detail, required this.cancelled});

  @override
  Widget build(BuildContext context) {
    final muted = cancelled ? AppColors.zinc400 : null;
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        border: Border.all(color: AppColors.zinc200),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Flexible(
            child: Text(label,
                style: TextStyle(
                  fontWeight: FontWeight.w600,
                  fontSize: 14,
                  color: muted,
                  decoration: cancelled ? TextDecoration.lineThrough : null,
                )),
          ),
          if (cancelled) ...[
            const SizedBox(width: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
              decoration: BoxDecoration(color: AppColors.zinc200, borderRadius: BorderRadius.circular(4)),
              child: const Text('말소', style: TextStyle(fontSize: 11, color: AppColors.zinc600)),
            ),
          ],
        ]),
        const SizedBox(height: 4),
        Text(keepAll(detail), style: TextStyle(fontSize: 13, color: muted ?? AppColors.zinc600)),
      ]),
    );
  }
}

class _Empty extends StatelessWidget {
  final String text;

  const _Empty(this.text);

  @override
  Widget build(BuildContext context) =>
      Text(keepAll(text), style: const TextStyle(fontSize: 14, color: AppColors.zinc500));
}
