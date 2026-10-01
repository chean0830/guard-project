import 'package:flutter/material.dart';

import '../../api/admin_api.dart';
import '../../api/api_client.dart';
import '../../theme.dart';
import '../../widgets/common.dart';
import 'admin_lawyer_screen.dart';
import 'admin_member_screen.dart';
import 'admin_payment_screen.dart';
import 'admin_report_screen.dart';

/// 관리자 첫 화면 (웹 /admin): 아래 탭으로 변호사 승인 / 신고 관리 / 회원 관리 / 결제 관리.
class AdminHomeScreen extends StatefulWidget {
  final ApiClient api;
  final Session session;
  final VoidCallback onLoggedOut;

  const AdminHomeScreen({super.key, required this.api, required this.session, required this.onLoggedOut});

  @override
  State<AdminHomeScreen> createState() => _AdminHomeScreenState();
}

class _AdminHomeScreenState extends State<AdminHomeScreen> {
  late final AdminApi _adminApi = AdminApi(widget.api);
  int _tab = 0;

  Future<void> _logout() async {
    await widget.api.logout();
    widget.onLoggedOut();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.zinc50,
      appBar: brandAppBar(actions: [
        PopupMenuButton<String>(
          tooltip: '내 계정',
          color: Colors.white,
          icon: const Icon(Icons.admin_panel_settings_outlined, color: AppColors.zinc600),
          itemBuilder: (_) => [
            PopupMenuItem(enabled: false, child: Text('관리자 · ${widget.session.email}', style: const TextStyle(fontSize: 13))),
            const PopupMenuItem(value: 'logout', child: Text('로그아웃')),
          ],
          onSelected: (v) => v == 'logout' ? _logout() : null,
        ),
      ]),
      body: SafeArea(
        child: IndexedStack(index: _tab, children: [
          AdminLawyerScreen(adminApi: _adminApi, onLoggedOut: widget.onLoggedOut),
          AdminReportScreen(adminApi: _adminApi, onLoggedOut: widget.onLoggedOut),
          AdminMemberScreen(adminApi: _adminApi, onLoggedOut: widget.onLoggedOut),
          AdminPaymentScreen(adminApi: _adminApi, onLoggedOut: widget.onLoggedOut),
        ]),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) => setState(() => _tab = i),
        backgroundColor: Colors.white,
        indicatorColor: AppColors.orange100,
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.how_to_reg_outlined),
            selectedIcon: Icon(Icons.how_to_reg, color: AppColors.orange600),
            label: '변호사 승인',
          ),
          NavigationDestination(
            icon: Icon(Icons.flag_outlined),
            selectedIcon: Icon(Icons.flag, color: AppColors.orange600),
            label: '신고 관리',
          ),
          NavigationDestination(
            icon: Icon(Icons.people_outline),
            selectedIcon: Icon(Icons.people, color: AppColors.orange600),
            label: '회원 관리',
          ),
          NavigationDestination(
            icon: Icon(Icons.receipt_long_outlined),
            selectedIcon: Icon(Icons.receipt_long, color: AppColors.orange600),
            label: '결제 관리',
          ),
        ],
      ),
    );
  }
}
