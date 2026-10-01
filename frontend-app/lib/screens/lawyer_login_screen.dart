import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/common.dart';
import 'forgot_password_screen.dart';

/// 변호사 전용 로그인. 관리자 승인이 끝난 계정만 로그인할 수 있다.
class LawyerLoginScreen extends StatefulWidget {
  final ApiClient api;
  final VoidCallback onLoggedIn;

  const LawyerLoginScreen({super.key, required this.api, required this.onLoggedIn});

  @override
  State<LawyerLoginScreen> createState() => _LawyerLoginScreenState();
}

class _LawyerLoginScreenState extends State<LawyerLoginScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_email.text.trim().isEmpty || _password.text.isEmpty) {
      setState(() => _error = '이메일과 비밀번호를 입력해주세요.');
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await widget.api.lawyerLogin(_email.text.trim(), _password.text);
      widget.onLoggedIn();
    } catch (e, stack) {
      setState(() => _error = describeError(e, stack));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: brandAppBar(showBack: true),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 40, 20, 24),
          children: [
            Container(
              alignment: Alignment.centerLeft,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(color: AppColors.zinc100, borderRadius: BorderRadius.circular(99)),
                child: const Row(mainAxisSize: MainAxisSize.min, children: [
                  Icon(Icons.balance_outlined, size: 14, color: AppColors.zinc600),
                  SizedBox(width: 4),
                  Text('변호사 전용',
                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.zinc600)),
                ]),
              ),
            ),
            const SizedBox(height: 12),
            const Text('변호사 로그인', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            const Text('관리자 승인이 완료된 계정만 로그인할 수 있어요.',
                style: TextStyle(fontSize: 14, color: AppColors.zinc500)),
            const SizedBox(height: 28),
            const FieldLabel('이메일'),
            TextField(
              controller: _email,
              keyboardType: TextInputType.emailAddress,
              autofillHints: const [AutofillHints.email],
              textInputAction: TextInputAction.next,
            ),
            const SizedBox(height: 16),
            const FieldLabel('비밀번호'),
            TextField(
              controller: _password,
              obscureText: true,
              autofillHints: const [AutofillHints.password],
              onSubmitted: (_) => _submit(),
            ),
            if (_error != null) ...[const SizedBox(height: 16), NoticeBox(_error!)],
            const SizedBox(height: 24),
            PillButton(
              label: _loading ? '로그인 중...' : '로그인',
              loading: _loading,
              onPressed: _submit,
              color: AppColors.zinc900,
            ),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: () => Navigator.of(context).push(MaterialPageRoute<void>(
                    builder: (_) => ForgotPasswordScreen(api: widget.api, initialAccountType: 'LAWYER'))),
                child: const Text('비밀번호를 잊으셨나요?', style: TextStyle(color: AppColors.zinc500, fontSize: 13)),
              ),
            ),
            const SizedBox(height: 24),
            Text(keepAll('변호사 회원가입은 자격 증명 서류 제출이 필요해서 웹에서 진행해주세요.'),
                textAlign: TextAlign.center, style: const TextStyle(fontSize: 13, color: AppColors.zinc500)),
          ],
        ),
      ),
    );
  }
}
