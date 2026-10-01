import 'package:flutter/material.dart';

import '../theme.dart';
import '../widgets/common.dart';
import 'legal_screen.dart';

const _painPoints = [
  '등기부등본을 받아도 무슨 말인지 하나도 모르겠어요',
  '선순위 근저당이 얼마나 있는지 계산이 안 돼요',
  '이 보증금이 시세보다 비싼 건 아닌지 불안해요',
];

const _riskScenarios = [
  ('선순위 근저당이 보증금보다 많은 집', '경매로 넘어가면 순위에서 밀려 보증금을 다 돌려받지 못할 수 있어요.'),
  ('등기부 소유자와 계약서 임대인이 다른 집', '실제 소유자가 아닌 사람과 계약하면 나중에 계약 자체가 문제 될 수 있어요.'),
  ('시세보다 보증금이 지나치게 높은 집', '집이 팔려도 보증금을 돌려주기에 부족한 상황이 생길 수 있어요.'),
  ('말소되지 않은 압류·가압류가 남은 집', '소유권이 넘어가거나 경매에 부쳐질 위험이 이미 걸려 있는 집이에요.'),
];

const _valueProps = [
  (Icons.account_balance_outlined, '실거래가 비교', '국토교통부 실거래가와 보증금을 비교해 전세가율이 안전한 수준인지 확인해요.'),
  (Icons.shield_outlined, '근저당·압류 확인', '등기부에 남아있는 근저당권, 압류, 가압류, 경매개시결정을 자동으로 찾아드려요.'),
  (Icons.balance_outlined, '법적 보호 기준 안내', '주택임대차보호법상 소액임차인 최우선변제 대상인지, 지역 기준과 함께 알려드려요.'),
];

const _evidenceTiers = [
  ('사실 확인', AppColors.zinc800, '등기부에 기록된 압류·근저당은 판단 없이 있는 그대로 알려드려요.'),
  ('법적 기준', AppColors.blue600, '주택임대차보호법 시행령 등 실제 법령을 근거로 확인해요.'),
  ('정부 권고 기준', AppColors.zinc500, '국토교통부·HUG가 제시하는 기준을 참고해요 (법적 구속력은 없어요).'),
];

const _steps = [
  (Icons.upload_outlined, '등기부등본 업로드', 'PDF 또는 촬영한 사진을 올려주세요. 여러 장으로 나눠 찍었어도 괜찮아요.'),
  (Icons.search, '자동 분석', '등기부 내용과 실거래가를 대조해 위험 신호가 있는지 확인해요.'),
  (Icons.verified_outlined, '결과 확인', '위험 등급과 그 이유를 쉬운 말로 정리해서 보여드려요.'),
];

/// 로그인 전 첫 화면. 웹 메인 페이지처럼 서비스 설명을 먼저 보여주고 로그인으로 유도한다.
class LandingScreen extends StatelessWidget {
  final VoidCallback onLogin;
  final VoidCallback onSignup;

  const LandingScreen({super.key, required this.onLogin, required this.onSignup});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: brandAppBar(actions: [
        TextButton(
          onPressed: onLogin,
          child: const Text('로그인',
              style: TextStyle(color: AppColors.orange600, fontWeight: FontWeight.w600, fontSize: 14)),
        ),
        const SizedBox(width: 4),
      ]),
      body: ListView(
        children: [
          _hero(),
          _stat(),
          _riskSection(),
          _valueSection(),
          _evidenceSection(),
          _stepsSection(),
          _finalCta(),
          const Padding(
            padding: EdgeInsets.fromLTRB(16, 24, 16, 0),
            child: Text(
              'Project Guard는 법률 자문이 아닌 참고용 정보를 제공하는 개인 프로젝트입니다.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12, color: AppColors.zinc400),
            ),
          ),
          const LegalFooterLinks(),
          const SizedBox(height: 24),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Container(
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
          decoration: const BoxDecoration(
            color: Colors.white,
            border: Border(top: BorderSide(color: AppColors.zinc200)),
          ),
          child: PillButton(label: '무료로 확인하기', onPressed: onLogin),
        ),
      ),
    );
  }

  Widget _hero() {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [AppColors.orange50, Colors.white],
        ),
      ),
      padding: const EdgeInsets.fromLTRB(20, 32, 20, 48),
      child: Column(children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
          decoration: BoxDecoration(color: AppColors.orange100, borderRadius: BorderRadius.circular(99)),
          child: const Text('전/월세 계약 전 필수 체크',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.orange700)),
        ),
        const SizedBox(height: 18),
        const Text(
          '계약하기 전,\n등기부등본부터\n확인하세요',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 34, height: 1.25, fontWeight: FontWeight.w800, letterSpacing: -0.8),
        ),
        const SizedBox(height: 18),
        const Text(
          '복잡한 등기부등본을 업로드하면\n위험 요소를 쉬운 말로 알려드립니다.',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 16, height: 1.5, color: AppColors.zinc600),
        ),
        const SizedBox(height: 36),
        for (final point in _painPoints)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              const Text('·  ', style: TextStyle(color: AppColors.orange400, fontWeight: FontWeight.w900)),
              Flexible(
                child: Text(point, style: const TextStyle(fontSize: 13, color: AppColors.zinc500)),
              ),
            ]),
          ),
      ]),
    );
  }

  Widget _stat() {
    return Container(
      color: AppColors.zinc950,
      padding: const EdgeInsets.fromLTRB(20, 52, 20, 52),
      child: Column(children: [
        Text('전세사기, 이제 남의 일이 아니에요',
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.orange400)),
        SizedBox(height: 14),
        Text('40,936명',
            style: TextStyle(fontSize: 48, fontWeight: FontWeight.w800, color: Colors.white, letterSpacing: -1)),
        SizedBox(height: 10),
        Text('전세사기 피해자 누적 (국토교통부 발표, 2026년 9월 기준)',
            textAlign: TextAlign.center, style: TextStyle(fontSize: 13, color: AppColors.zinc400)),
        const SizedBox(height: 20),
        Text(
          keepAll('이 중 보증금 3억원 이하 피해가 97.6%를 차지해요. 특별히 비싸지 않은, 평범한 전셋집도 예외가 아니라는 뜻이에요. '
              '‘나는 괜찮겠지’라는 생각이 가장 위험할 수 있어요.'),
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 14, height: 1.6, color: AppColors.zinc300),
        ),
      ]),
    );
  }

  Widget _sectionTitle(String title, [String? subtitle]) {
    return Column(children: [
      Text(title,
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 23, fontWeight: FontWeight.w700, letterSpacing: -0.4, height: 1.35)),
      if (subtitle != null) ...[
        const SizedBox(height: 8),
        Text(keepAll(subtitle),
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 14, color: AppColors.zinc500, height: 1.5)),
      ],
    ]);
  }

  Widget _riskSection() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 64, 16, 24),
      child: Column(children: [
        _sectionTitle('이런 집은 특히 조심하세요', '겉으로는 멀쩡해 보여도, 등기부등본을 확인해봐야 알 수 있어요.'),
        const SizedBox(height: 28),
        for (final (title, description) in _riskScenarios)
          Container(
            margin: const EdgeInsets.only(bottom: 12),
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: AppColors.red50,
              border: Border.all(color: AppColors.red200),
              borderRadius: BorderRadius.circular(16),
            ),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Icon(Icons.warning_amber_rounded, size: 20, color: AppColors.red500),
              const SizedBox(width: 10),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(title,
                      style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15, color: AppColors.red950)),
                  const SizedBox(height: 4),
                  Text(keepAll(description),
                      style: TextStyle(fontSize: 13, height: 1.5, color: AppColors.red800.withValues(alpha: 0.85))),
                ]),
              ),
            ]),
          ),
        const SizedBox(height: 16),
        PillButton(
          label: '내 계약도 확인해보기',
          onPressed: onLogin,
          color: AppColors.zinc950,
          expand: false,
          height: 46,
        ),
      ]),
    );
  }

  Widget _valueSection() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 56, 16, 24),
      child: Column(children: [
        _sectionTitle('그래서, Project Guard가\n이렇게 확인해드려요'),
        const SizedBox(height: 28),
        for (final (icon, title, description) in _valueProps)
          Container(
            width: double.infinity,
            margin: const EdgeInsets.only(bottom: 14),
            padding: const EdgeInsets.all(22),
            decoration: BoxDecoration(
              color: Colors.white,
              border: Border.all(color: AppColors.zinc200),
              borderRadius: BorderRadius.circular(16),
              boxShadow: const [BoxShadow(color: Color(0x0D000000), blurRadius: 3, offset: Offset(0, 1))],
            ),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Container(
                width: 44,
                height: 44,
                decoration: const BoxDecoration(color: AppColors.orange100, shape: BoxShape.circle),
                child: Icon(icon, color: AppColors.orange600, size: 22),
              ),
              const SizedBox(height: 14),
              Text(title, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
              const SizedBox(height: 6),
              Text(keepAll(description), style: const TextStyle(fontSize: 14, height: 1.55, color: AppColors.zinc600)),
            ]),
          ),
      ]),
    );
  }

  Widget _evidenceSection() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 56),
      child: Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          color: AppColors.zinc50,
          border: Border.all(color: AppColors.zinc200),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Column(children: [
          const Text('감이 아니라, 근거로 판단해요', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
          const SizedBox(height: 6),
          const Text('위험 신호마다 어떤 근거로 나온 판단인지\n함께 보여드려요.',
              textAlign: TextAlign.center, style: TextStyle(fontSize: 14, color: AppColors.zinc500, height: 1.5)),
          const SizedBox(height: 20),
          for (final (badge, color, description) in _evidenceTiers)
            Padding(
              padding: const EdgeInsets.only(bottom: 16),
              child: Column(children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(99)),
                  child: Text(badge,
                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: Colors.white)),
                ),
                const SizedBox(height: 8),
                Text(keepAll(description),
                    textAlign: TextAlign.center,
                    style: const TextStyle(fontSize: 14, height: 1.5, color: AppColors.zinc600)),
              ]),
            ),
        ]),
      ),
    );
  }

  Widget _stepsSection() {
    return Container(
      color: AppColors.zinc50,
      padding: const EdgeInsets.fromLTRB(16, 56, 16, 40),
      child: Column(children: [
        _sectionTitle('이용 방법은 간단해요'),
        const SizedBox(height: 32),
        for (final (i, (icon, title, description)) in _steps.indexed)
          Padding(
            padding: const EdgeInsets.only(bottom: 28),
            child: Column(children: [
              SizedBox(
                width: 72,
                height: 72,
                child: Stack(clipBehavior: Clip.none, children: [
                  Container(
                    width: 64,
                    height: 64,
                    margin: const EdgeInsets.all(4),
                    decoration: const BoxDecoration(
                      color: Colors.white,
                      shape: BoxShape.circle,
                      boxShadow: [BoxShadow(color: Color(0x1A000000), blurRadius: 8, offset: Offset(0, 3))],
                    ),
                    child: Icon(icon, size: 28, color: AppColors.orange600),
                  ),
                  Positioned(
                    top: 0,
                    right: 0,
                    child: Container(
                      width: 24,
                      height: 24,
                      alignment: Alignment.center,
                      decoration: const BoxDecoration(color: AppColors.orange500, shape: BoxShape.circle),
                      child: Text('${i + 1}',
                          style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w700)),
                    ),
                  ),
                ]),
              ),
              const SizedBox(height: 12),
              Text(title, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
              const SizedBox(height: 6),
              Text(keepAll(description),
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 14, height: 1.5, color: AppColors.zinc600)),
            ]),
          ),
      ]),
    );
  }

  Widget _finalCta() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 56, 20, 24),
      child: Column(children: [
        _sectionTitle('지금 바로 확인해보세요'),
        const SizedBox(height: 8),
        const Text('로그인하면 아이디당 5회까지 무료로 분석할 수 있고,\n이후에는 1회 990원이에요.',
            textAlign: TextAlign.center, style: TextStyle(fontSize: 13, color: AppColors.zinc500, height: 1.5)),
        const SizedBox(height: 24),
        PillButton(label: '로그인하고 시작하기', onPressed: onLogin),
        const SizedBox(height: 14),
        Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          const Text('아직 계정이 없으신가요? ', style: TextStyle(fontSize: 14, color: AppColors.zinc500)),
          GestureDetector(
            onTap: onSignup,
            child: const Text('회원가입',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: AppColors.orange600)),
          ),
        ]),
      ]),
    );
  }
}
