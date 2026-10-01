import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../theme.dart';
import 'common.dart';

/// 웹 social-login-buttons와 같은 모양 (각 사 가이드 색상).
const _providers = {
  'google': (label: 'Google로 계속하기', background: Colors.white, foreground: AppColors.zinc700, border: true),
  'kakao': (label: '카카오로 계속하기', background: Color(0xFFFEE500), foreground: Color(0xD9000000), border: false),
  'naver': (label: '네이버로 계속하기', background: Color(0xFF03C75A), foreground: Colors.white, border: false),
};

/// 소셜 로그인 버튼 묶음. 웹에 키가 설정된 것만 보이고, 하나도 없으면(웹 연결 실패 포함) 구분선까지 통째로 숨긴다.
/// [dividerText]는 버튼 위([dividerAbove]=true, 로그인 화면) 또는 아래(회원가입 화면)에 붙는 "또는 ~" 구분선.
class SocialLoginSection extends StatefulWidget {
  final ApiClient api;
  final VoidCallback onLoggedIn;
  final ValueChanged<String> onError;
  final String dividerText;
  final bool dividerAbove;

  const SocialLoginSection({
    super.key,
    required this.api,
    required this.onLoggedIn,
    required this.onError,
    required this.dividerText,
    this.dividerAbove = true,
  });

  @override
  State<SocialLoginSection> createState() => _SocialLoginSectionState();
}

class _SocialLoginSectionState extends State<SocialLoginSection> {
  List<String> _enabled = const [];
  String? _pending;

  @override
  void initState() {
    super.initState();
    widget.api.socialProviders().then((list) {
      if (mounted) setState(() => _enabled = [for (final p in _providers.keys) if (list.contains(p)) p]);
    });
  }

  Future<void> _login(String provider) async {
    setState(() => _pending = provider);
    try {
      final session = await widget.api.socialLogin(provider);
      if (session != null) widget.onLoggedIn();
    } catch (e, stack) {
      widget.onError(describeError(e, stack));
    } finally {
      if (mounted) setState(() => _pending = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_enabled.isEmpty) return const SizedBox.shrink();
    final divider = Padding(padding: const EdgeInsets.symmetric(vertical: 24), child: OrDivider(widget.dividerText));
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      if (widget.dividerAbove) divider,
      for (final id in _enabled) ...[_button(id), const SizedBox(height: 8)],
      if (!widget.dividerAbove) divider,
    ]);
  }

  Widget _button(String id) {
    final p = _providers[id]!;
    final loading = _pending == id;
    return SizedBox(
      height: 48,
      child: FilledButton(
        onPressed: _pending == null ? () => _login(id) : null,
        style: FilledButton.styleFrom(
          backgroundColor: p.background,
          disabledBackgroundColor: p.background.withValues(alpha: 0.6),
          foregroundColor: p.foreground,
          disabledForegroundColor: p.foreground.withValues(alpha: 0.6),
          shape: StadiumBorder(side: p.border ? const BorderSide(color: AppColors.zinc300) : BorderSide.none),
        ),
        child: Stack(alignment: Alignment.center, children: [
          Align(alignment: Alignment.centerLeft, child: _logo(id)),
          loading
              ? SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: p.foreground))
              : Text(p.label, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
        ]),
      ),
    );
  }

  /// 로고는 SVG 패키지 없이 글자·아이콘으로 비슷하게 그린다.
  Widget _logo(String id) => switch (id) {
        'google' => const Text('G',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: Color(0xFF4285F4))),
        'kakao' => const Icon(Icons.chat_bubble, size: 18, color: Color(0xD9000000)),
        _ => const Text('N', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900, color: Colors.white)),
      };
}
