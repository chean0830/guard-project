import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/member_api.dart';
import '../../theme.dart';
import '../../widgets/common.dart';
import '../analyze_screen.dart';
import 'consult_screen.dart';

/// 회원 로그인 후 첫 화면: 아래 탭으로 등기부 분석 / 변호사 상담을 오간다.
class MemberHomeScreen extends StatefulWidget {
  final ApiClient api;
  final Session session;
  final VoidCallback onLoggedOut;

  const MemberHomeScreen({super.key, required this.api, required this.session, required this.onLoggedOut});

  @override
  State<MemberHomeScreen> createState() => _MemberHomeScreenState();
}

class _MemberHomeScreenState extends State<MemberHomeScreen> {
  late final MemberApi _memberApi = MemberApi(widget.api);
  int _tab = 0;

  Future<void> _logout() async {
    await widget.api.logout();
    widget.onLoggedOut();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: brandAppBar(actions: [
        PopupMenuButton<String>(
          tooltip: '내 계정',
          color: Colors.white,
          icon: const Icon(Icons.account_circle_outlined, color: AppColors.zinc600),
          itemBuilder: (_) => [
            PopupMenuItem(enabled: false, child: Text(widget.session.email, style: const TextStyle(fontSize: 13))),
            const PopupMenuItem(value: 'logout', child: Text('로그아웃')),
          ],
          onSelected: (v) => v == 'logout' ? _logout() : null,
        ),
      ]),
      body: SafeArea(
        // 탭을 오가도 입력 중인 분석 폼과 상담 목록 연결이 유지되도록 둘 다 살려 둔다.
        child: IndexedStack(index: _tab, children: [
          AnalyzeScreen(
            api: widget.api,
            onLoggedOut: widget.onLoggedOut,
            onOpenConsult: () => setState(() => _tab = 1),
          ),
          ConsultScreen(memberApi: _memberApi, onLoggedOut: widget.onLoggedOut),
        ]),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) => setState(() => _tab = i),
        backgroundColor: Colors.white,
        indicatorColor: AppColors.orange100,
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.description_outlined),
            selectedIcon: Icon(Icons.description, color: AppColors.orange600),
            label: '등기부 분석',
          ),
          NavigationDestination(
            icon: Icon(Icons.forum_outlined),
            selectedIcon: Icon(Icons.forum, color: AppColors.orange600),
            label: '변호사 상담',
          ),
        ],
      ),
    );
  }
}
