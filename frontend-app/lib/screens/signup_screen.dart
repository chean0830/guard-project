import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/common.dart';
import '../widgets/social_login_buttons.dart';
import 'legal_screen.dart';

/// 이메일 회원가입. 웹과 같이 이메일 인증(6자리 인증번호)을 마쳐야 비밀번호 입력과 가입 버튼이 열린다.
class SignupScreen extends StatefulWidget {
  final ApiClient api;
  final VoidCallback onSignedUp;
  final VoidCallback onLogin;

  const SignupScreen({super.key, required this.api, required this.onSignedUp, required this.onLogin});

  @override
  State<SignupScreen> createState() => _SignupScreenState();
}

class _SignupScreenState extends State<SignupScreen> {
  final _email = TextEditingController();
  final _code = TextEditingController();
  final _password = TextEditingController();

  bool _codeSent = false;
  String? _verificationToken;
  ({bool ok, String text})? _verifyMessage;
  bool _busy = false;
  bool _submitting = false;
  String? _error;

  bool get _verified => _verificationToken != null;

  @override
  void dispose() {
    _email.dispose();
    _code.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _sendCode() async {
    setState(() {
      _busy = true;
      _verifyMessage = null;
    });
    try {
      await widget.api.requestSignupCode(_email.text.trim());
      setState(() {
        _codeSent = true;
        _code.clear();
        _verifyMessage = (ok: true, text: '인증번호를 보냈어요. 메일함(스팸함 포함)을 확인해 10분 안에 입력해주세요.');
      });
    } catch (e, stack) {
      setState(() => _verifyMessage = (ok: false, text: describeError(e, stack)));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _verify() async {
    setState(() => _busy = true);
    try {
      final token = await widget.api.verifySignupCode(_email.text.trim(), _code.text);
      setState(() {
        _verificationToken = token;
        _verifyMessage = (ok: true, text: '✓ 이메일 인증이 완료됐어요.');
      });
    } catch (e, stack) {
      setState(() => _verifyMessage = (ok: false, text: describeError(e, stack)));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _submit() async {
    if (_password.text.length < 8) {
      setState(() => _error = '비밀번호는 8자 이상으로 입력해주세요.');
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await widget.api.signup(_email.text.trim(), _password.text, _verificationToken!);
      widget.onSignedUp();
    } catch (e, stack) {
      setState(() => _error = describeError(e, stack));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Widget _smallButton(String label, VoidCallback? onPressed) {
    return SizedBox(
      height: 46,
      child: OutlinedButton(
        onPressed: onPressed,
        style: OutlinedButton.styleFrom(
          foregroundColor: AppColors.zinc700,
          side: const BorderSide(color: AppColors.zinc300),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          padding: const EdgeInsets.symmetric(horizontal: 12),
        ),
        child: Text(label, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: brandAppBar(showBack: true),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 40, 20, 24),
          children: [
            const Text('회원가입', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            Text(keepAll('이메일 인증 후 바로 이용할 수 있어요. 아이디당 5회까지 무료로 분석해드려요.'),
                style: TextStyle(fontSize: 14, color: AppColors.zinc500, height: 1.5)),
            const SizedBox(height: 4),
            SocialLoginSection(
              api: widget.api,
              dividerText: '또는 이메일로 가입',
              dividerAbove: false,
              onLoggedIn: widget.onSignedUp,
              // 오류 상자는 화면 아래 가입 버튼 옆에 있어, 위쪽 소셜 버튼의 오류는 알림줄로 보여준다.
              onError: (message) =>
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message))),
            ),
            const SizedBox(height: 24),
            const FieldLabel('이메일'),
            Row(children: [
              Expanded(
                child: TextField(
                  controller: _email,
                  readOnly: _verified,
                  keyboardType: TextInputType.emailAddress,
                  autofillHints: const [AutofillHints.email],
                  decoration: InputDecoration(fillColor: _verified ? AppColors.zinc50 : Colors.white),
                  style: TextStyle(color: _verified ? AppColors.zinc500 : null),
                  onChanged: (_) => setState(() {
                    _codeSent = false;
                    _verifyMessage = null;
                  }),
                ),
              ),
              if (!_verified) ...[
                const SizedBox(width: 8),
                _smallButton(_codeSent ? '다시 받기' : '인증번호 받기',
                    _busy || !_email.text.contains('@') ? null : _sendCode),
              ],
            ]),
            if (_codeSent && !_verified) ...[
              const SizedBox(height: 16),
              const FieldLabel('인증번호'),
              Row(children: [
                Expanded(
                  child: TextField(
                    controller: _code,
                    keyboardType: TextInputType.number,
                    maxLength: 6,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    decoration: const InputDecoration(hintText: '6자리 숫자', counterText: ''),
                    onChanged: (_) => setState(() {}),
                  ),
                ),
                const SizedBox(width: 8),
                _smallButton('확인', _busy || _code.text.length != 6 ? null : _verify),
              ]),
            ],
            if (_verifyMessage != null) ...[
              const SizedBox(height: 6),
              Text(_verifyMessage!.text,
                  style: TextStyle(
                      fontSize: 12, color: _verifyMessage!.ok ? AppColors.emerald600 : AppColors.red600)),
            ],
            const SizedBox(height: 16),
            const FieldLabel('비밀번호'),
            TextField(
              controller: _password,
              obscureText: true,
              enabled: _verified,
              autofillHints: const [AutofillHints.newPassword],
              decoration: InputDecoration(fillColor: _verified ? Colors.white : AppColors.zinc50),
              onSubmitted: (_) => _submit(),
            ),
            const SizedBox(height: 4),
            Text(_verified ? '8자 이상으로 입력해주세요.' : '이메일 인증을 마치면 입력할 수 있어요.',
                style: const TextStyle(fontSize: 12, color: AppColors.zinc500)),
            if (_error != null) ...[const SizedBox(height: 16), NoticeBox(_error!)],
            const SizedBox(height: 24),
            PillButton(
              label: _submitting ? '가입 중...' : '회원가입',
              loading: _submitting,
              onPressed: _verified ? _submit : null,
            ),
            const SizedBox(height: 14),
            const LegalConsentText(),
            const SizedBox(height: 24),
            Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              const Text('이미 계정이 있으신가요? ', style: TextStyle(fontSize: 14, color: AppColors.zinc500)),
              GestureDetector(
                onTap: widget.onLogin,
                child: const Text('로그인',
                    style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: AppColors.orange600)),
              ),
            ]),
          ],
        ),
      ),
    );
  }
}
