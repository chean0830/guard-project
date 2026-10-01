import 'package:flutter/material.dart';

import '../theme.dart';

/// 웹의 둥근(rounded-full) 버튼. [filled]가 false면 테두리만 있는 버튼.
class PillButton extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;
  final bool loading;
  final bool filled;
  final Color color;
  final Color? textColor;
  final IconData? icon;
  final bool expand;
  final double height;

  const PillButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.loading = false,
    this.filled = true,
    this.color = AppColors.orange500,
    this.textColor,
    this.icon,
    this.expand = true,
    this.height = 50,
  });

  @override
  Widget build(BuildContext context) {
    final fg = textColor ?? (filled ? Colors.white : color);
    final child = loading
        ? SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2.2, color: fg))
        : Row(mainAxisSize: MainAxisSize.min, children: [
            if (icon != null) ...[Icon(icon, size: 18, color: fg), const SizedBox(width: 6)],
            Text(label, style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15, color: fg)),
          ]);
    final shape = const StadiumBorder();
    final size = Size(expand ? double.infinity : 0, height);
    final disabled = onPressed == null || loading;

    final button = filled
        ? FilledButton(
            onPressed: loading ? () {} : onPressed,
            style: FilledButton.styleFrom(
              backgroundColor: color,
              disabledBackgroundColor: color.withValues(alpha: 0.5),
              foregroundColor: fg,
              shape: shape,
              minimumSize: size,
              padding: const EdgeInsets.symmetric(horizontal: 24),
            ),
            child: child,
          )
        : OutlinedButton(
            onPressed: loading ? () {} : onPressed,
            style: OutlinedButton.styleFrom(
              side: BorderSide(color: disabled ? AppColors.zinc200 : color),
              foregroundColor: fg,
              shape: shape,
              minimumSize: size,
              padding: const EdgeInsets.symmetric(horizontal: 18),
            ),
            child: child,
          );
    return button;
  }
}

/// 필수/선택 표시 뱃지.
class FieldBadge extends StatelessWidget {
  final bool required;

  const FieldBadge({super.key, required this.required});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: required ? AppColors.orange100 : AppColors.zinc100,
        borderRadius: BorderRadius.circular(99),
      ),
      child: Text(
        required ? '필수' : '선택',
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: required ? AppColors.orange600 : AppColors.zinc500,
        ),
      ),
    );
  }
}

/// 입력칸 위의 라벨 (+ 필수/선택 뱃지, 보조 설명).
class FieldLabel extends StatelessWidget {
  final String text;
  final bool? required;
  final String? help;

  const FieldLabel(this.text, {super.key, this.required, this.help});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Text(text, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
          if (required != null) ...[const SizedBox(width: 6), FieldBadge(required: required!)],
        ]),
        if (help != null) ...[
          const SizedBox(height: 2),
          Text(keepAll(help!), style: const TextStyle(fontSize: 12, color: AppColors.zinc500, height: 1.4)),
        ],
      ]),
    );
  }
}

/// 빨간/초록 안내 박스.
class NoticeBox extends StatelessWidget {
  final String text;
  final bool error;

  const NoticeBox(this.text, {super.key, this.error = true});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: error ? AppColors.red50 : AppColors.emerald50,
        border: Border.all(color: error ? const Color(0xFFFCA5A5) : AppColors.emerald300),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(keepAll(text),
          style: TextStyle(fontSize: 14, height: 1.4, color: error ? AppColors.red800 : AppColors.emerald900)),
    );
  }
}

/// "또는 ~" 구분선.
class OrDivider extends StatelessWidget {
  final String text;

  const OrDivider(this.text, {super.key});

  @override
  Widget build(BuildContext context) {
    return Row(children: [
      const Expanded(child: Divider()),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12),
        child: Text(text, style: const TextStyle(fontSize: 12, color: AppColors.zinc400)),
      ),
      const Expanded(child: Divider()),
    ]);
  }
}

/// 상단 머리글: 왼쪽 "Project Guard", 오른쪽 동작.
PreferredSizeWidget brandAppBar({List<Widget>? actions, bool showBack = false}) {
  return AppBar(
    automaticallyImplyLeading: showBack,
    titleSpacing: showBack ? 0 : 16,
    title: const Text('Project Guard', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
    actions: actions,
    bottom: const PreferredSize(preferredSize: Size.fromHeight(1), child: Divider()),
  );
}

/// 한글이 단어 중간에서 줄바뀌지 않게 한다 (웹의 word-break: keep-all).
/// 글자 사이에 줄바꿈 금지 문자(U+2060)를 넣어, 띄어쓰기에서만 줄이 바뀌게 한다.
String keepAll(String text) =>
    text.split(' ').map((word) => word.characters.join('⁠')).join(' ');

/// "● 실시간 연결됨 / 연결 중..." 표시.
class LiveIndicator extends StatelessWidget {
  final bool connected;
  final double fontSize;

  const LiveIndicator({super.key, required this.connected, this.fontSize = 12});

  @override
  Widget build(BuildContext context) {
    return Row(mainAxisSize: MainAxisSize.min, children: [
      Container(
        width: 7,
        height: 7,
        decoration: BoxDecoration(
          color: connected ? AppColors.emerald600 : AppColors.zinc400,
          shape: BoxShape.circle,
        ),
      ),
      const SizedBox(width: 4),
      Text(connected ? '실시간 연결됨' : '연결 중...',
          style: TextStyle(fontSize: fontSize, color: AppColors.zinc500, fontWeight: FontWeight.w400)),
    ]);
  }
}
