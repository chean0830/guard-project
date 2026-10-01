import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/common.dart';
import '../widgets/social_login_buttons.dart';
import 'forgot_password_screen.dart';

class LoginScreen extends StatefulWidget {
  final ApiClient api;
  final VoidCallback onLoggedIn;
  final VoidCallback onSignup;
  final VoidCallback onLawyerLogin;

  const LoginScreen({
    super.key,
    required this.api,
    required this.onLoggedIn,
    required this.onSignup,
    required this.onLawyerLogin,
  });

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
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
      await widget.api.login(_email.text.trim(), _password.text);
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
            const Text('로그인', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            const Text('등기부 분석을 이용하려면 로그인해주세요.',
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
            PillButton(label: _loading ? '로그인 중...' : '로그인', loading: _loading, onPressed: _submit),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: () => Navigator.of(context).push(MaterialPageRoute<void>(
                    builder: (_) => ForgotPasswordScreen(api: widget.api, initialAccountType: 'USER'))),
                child: const Text('비밀번호를 잊으셨나요?', style: TextStyle(color: AppColors.zinc500, fontSize: 13)),
              ),
            ),
            SocialLoginSection(
              api: widget.api,
              dividerText: '또는 SNS로 로그인',
              onLoggedIn: widget.onLoggedIn,
              onError: (message) => setState(() => _error = message),
            ),
            const SizedBox(height: 24),
            Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              const Text('아직 계정이 없으신가요? ', style: TextStyle(fontSize: 14, color: AppColors.zinc500)),
              GestureDetector(
                onTap: widget.onSignup,
                child: const Text('회원가입',
                    style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: AppColors.orange600)),
              ),
            ]),
            const SizedBox(height: 32),
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                border: Border.all(color: AppColors.zinc200),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Column(children: [
                const Text('변호사이신가요?',
                    style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.zinc700)),
                const SizedBox(height: 4),
                const Text('승인된 변호사 계정으로 로그인해주세요.',
                    style: TextStyle(fontSize: 12, color: AppColors.zinc500)),
                const SizedBox(height: 14),
                PillButton(
                  label: '변호사 로그인',
                  onPressed: widget.onLawyerLogin,
                  filled: false,
                  color: AppColors.zinc300,
                  textColor: AppColors.zinc700,
                  height: 44,
                ),
              ]),
            ),
          ],
        ),
      ),
    );
  }
}
