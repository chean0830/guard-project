import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/common.dart';

/// 비밀번호 찾기 (웹 /forgot-password와 같은 구성). 메일의 재설정 링크는 웹 화면으로 열린다.
class ForgotPasswordScreen extends StatefulWidget {
  final ApiClient api;

  /// USER(회원) 또는 LAWYER(변호사). 들어온 로그인 화면에 맞춰 미리 골라 둔다.
  final String initialAccountType;

  const ForgotPasswordScreen({super.key, required this.api, this.initialAccountType = 'USER'});

  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  final _email = TextEditingController();
  late String _accountType = widget.initialAccountType;
  bool _sending = false;
  ({bool ok, String text})? _result;

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_email.text.contains('@')) {
      setState(() => _result = (ok: false, text: '가입한 이메일을 입력해주세요.'));
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _sending = true;
      _result = null;
    });
    try {
      final message = await widget.api.requestPasswordReset(_email.text.trim(), _accountType);
      setState(() => _result = (ok: true, text: message));
    } catch (e, stack) {
      setState(() => _result = (ok: false, text: describeError(e, stack)));
    } finally {
      if (mounted) setState(() => _sending = false);
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
            const Text('비밀번호 찾기', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            Text(keepAll('가입한 이메일로 비밀번호 재설정 링크를 보내드려요.'),
                style: const TextStyle(fontSize: 14, color: AppColors.zinc500, height: 1.5)),
            const SizedBox(height: 4),
            Text(keepAll('비밀번호를 5회 틀려 로그인이 잠긴 경우에도 여기서 새 비밀번호를 설정하면 잠금이 풀려요.'),
                style: const TextStyle(fontSize: 13, color: AppColors.zinc400, height: 1.5)),
            const SizedBox(height: 28),
            const FieldLabel('계정 종류'),
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(value: 'USER', label: Text('회원')),
                ButtonSegment(value: 'LAWYER', label: Text('변호사')),
              ],
              selected: {_accountType},
              onSelectionChanged: (s) => setState(() => _accountType = s.first),
            ),
            const SizedBox(height: 18),
            const FieldLabel('가입한 이메일'),
            TextField(
              controller: _email,
              keyboardType: TextInputType.emailAddress,
              autofillHints: const [AutofillHints.email],
              onSubmitted: (_) => _submit(),
            ),
            if (_result != null) ...[const SizedBox(height: 16), NoticeBox(_result!.text, error: !_result!.ok)],
            const SizedBox(height: 24),
            PillButton(label: _sending ? '보내는 중...' : '재설정 링크 받기', loading: _sending, onPressed: _submit),
            const SizedBox(height: 16),
            Center(
              child: TextButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text('로그인으로 돌아가기', style: TextStyle(color: AppColors.zinc500)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
