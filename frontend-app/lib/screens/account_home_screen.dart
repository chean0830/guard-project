import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/common.dart';

/// 변호사·관리자로 로그인했을 때의 첫 화면.
/// 상담 관리(변호사)와 관리자 콘솔은 아직 앱에 없어서, 로그인 상태와 웹 안내만 보여준다.
class AccountHomeScreen extends StatelessWidget {
  final ApiClient api;
  final Session session;
  final VoidCallback onLoggedOut;

  const AccountHomeScreen({super.key, required this.api, required this.session, required this.onLoggedOut});

  Future<void> _logout() async {
    await api.logout();
    onLoggedOut();
  }

  @override
  Widget build(BuildContext context) {
    final isLawyer = session.role == AccountRole.lawyer;
    final title = isLawyer ? '${session.name ?? '변호사'}님, 반가워요' : '관리자로 로그인했어요';
    final badge = isLawyer ? '변호사' : '관리자';
    final features = isLawyer
        ? const ['배정된 상담 문의 확인과 답변', '상담 대화 신고·차단', '프로필(강점·경력·수임료) 관리']
        : const ['변호사 가입 승인·거절', '신고 처리와 이용 정지', '회원·결제·환불 관리'];

    return Scaffold(
      appBar: brandAppBar(actions: [
        TextButton(
          onPressed: _logout,
          child: const Text('로그아웃', style: TextStyle(color: AppColors.zinc600, fontSize: 14)),
        ),
        const SizedBox(width: 4),
      ]),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 40, 20, 24),
          children: [
            Align(
              alignment: Alignment.centerLeft,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: isLawyer ? AppColors.zinc900 : AppColors.orange100,
                  borderRadius: BorderRadius.circular(99),
                ),
                child: Text(badge,
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: isLawyer ? Colors.white : AppColors.orange700,
                    )),
              ),
            ),
            const SizedBox(height: 12),
            Text(title, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w700)),
            const SizedBox(height: 6),
            Text(session.email, style: const TextStyle(fontSize: 14, color: AppColors.zinc500)),
            const SizedBox(height: 28),
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.zinc50,
                border: Border.all(color: AppColors.zinc200),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('앱에서는 준비 중이에요', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                const SizedBox(height: 6),
                Text(keepAll('아래 기능은 지금은 웹에서 이용해주세요.'),
                    style: const TextStyle(fontSize: 14, color: AppColors.zinc500)),
                const SizedBox(height: 14),
                for (final f in features)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Row(children: [
                      const Icon(Icons.check_circle_outline, size: 18, color: AppColors.orange500),
                      const SizedBox(width: 8),
                      Expanded(child: Text(f, style: const TextStyle(fontSize: 14))),
                    ]),
                  ),
              ]),
            ),
          ],
        ),
      ),
    );
  }
}
