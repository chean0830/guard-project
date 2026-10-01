import 'package:flutter/material.dart';

import '../../theme.dart';
import '../../widgets/common.dart';

/// 웹 관리자 화면의 상태 필터 (주황 알약 버튼 줄).
class FilterChips<T> extends StatelessWidget {
  final List<(T, String)> options;
  final T selected;
  final ValueChanged<T> onSelected;

  const FilterChips({super.key, required this.options, required this.selected, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(children: [
        for (final (value, label) in options)
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: ChoiceChip(
              label: Text(label),
              selected: value == selected,
              showCheckmark: false,
              onSelected: (_) => onSelected(value),
              backgroundColor: Colors.white,
              selectedColor: AppColors.orange500,
              side: BorderSide(color: value == selected ? AppColors.orange500 : AppColors.zinc300),
              shape: const StadiumBorder(),
              labelStyle: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: value == selected ? Colors.white : AppColors.zinc600,
              ),
            ),
          ),
      ]),
    );
  }
}

/// 목록 한 칸 (흰 바탕 둥근 카드).
class AdminCard extends StatelessWidget {
  final Widget child;

  const AdminCard({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: AppColors.zinc200),
        borderRadius: BorderRadius.circular(16),
      ),
      child: child,
    );
  }
}

/// 작은 상태 뱃지.
class StatusBadge extends StatelessWidget {
  final String text;
  final Color background;
  final Color foreground;

  const StatusBadge(this.text, {super.key, required this.background, required this.foreground});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(99)),
      child: Text(text, style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: foreground)),
    );
  }
}

/// "라벨  값" 한 줄.
class InfoRow extends StatelessWidget {
  final String label;
  final String value;

  const InfoRow(this.label, this.value, {super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        SizedBox(width: 72, child: Text(label, style: const TextStyle(fontSize: 13, color: AppColors.zinc500))),
        Expanded(child: Text(value, style: const TextStyle(fontSize: 13))),
      ]),
    );
  }
}

/// 작은 알약 버튼 (승인·거절 등).
Widget smallPill(String label, VoidCallback? onPressed, {Color color = AppColors.zinc700, bool filled = false}) {
  final style = filled
      ? FilledButton.styleFrom(
          backgroundColor: color,
          shape: const StadiumBorder(),
          minimumSize: const Size(0, 36),
          padding: const EdgeInsets.symmetric(horizontal: 16),
          textStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
        )
      : OutlinedButton.styleFrom(
          foregroundColor: color,
          side: BorderSide(color: color.withValues(alpha: 0.5)),
          shape: const StadiumBorder(),
          minimumSize: const Size(0, 36),
          padding: const EdgeInsets.symmetric(horizontal: 16),
          textStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
        );
  return filled
      ? FilledButton(onPressed: onPressed, style: style, child: Text(label))
      : OutlinedButton(onPressed: onPressed, style: style, child: Text(label));
}

/// 확인 대화상자. 확인을 누르면 true.
Future<bool> confirmDialog(BuildContext context,
    {required String title, required String message, required String confirmLabel, Color color = AppColors.red600}) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      backgroundColor: Colors.white,
      title: Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
      content: Text(keepAll(message), style: const TextStyle(fontSize: 14, color: AppColors.zinc600, height: 1.5)),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('취소')),
        FilledButton(
          style: FilledButton.styleFrom(backgroundColor: color),
          onPressed: () => Navigator.pop(context, true),
          child: Text(confirmLabel),
        ),
      ],
    ),
  );
  return ok == true;
}

/// 사유 입력 대화상자. 취소하면 null, 확인하면 입력한 글(비어 있을 수 있음).
Future<String?> reasonDialog(BuildContext context,
        {required String title, required String hint, required String confirmLabel, Color color = AppColors.red600}) =>
    showTextInputDialog(context, title: title, hint: hint, confirmLabel: confirmLabel, color: color);

/// 목록 화면 공통 뼈대: 제목·설명·필터 + 불러오는 중/오류/빈 목록 처리 + 당겨서 새로고침.
class AdminListView extends StatelessWidget {
  final String title;
  final String? description;
  final Widget? filters;
  final String? error;
  final bool loading;
  final bool empty;
  final String emptyText;
  final List<Widget> children;
  final Future<void> Function() onRefresh;

  const AdminListView({
    super.key,
    required this.title,
    this.description,
    this.filters,
    this.error,
    required this.loading,
    required this.empty,
    required this.emptyText,
    required this.children,
    required this.onRefresh,
  });

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      color: AppColors.orange500,
      onRefresh: onRefresh,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 28, 16, 32),
        children: [
          Text(title, style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
          if (description != null) ...[
            const SizedBox(height: 6),
            Text(keepAll(description!), style: const TextStyle(fontSize: 13, color: AppColors.zinc500, height: 1.5)),
          ],
          if (filters != null) ...[const SizedBox(height: 16), filters!],
          const SizedBox(height: 16),
          if (error != null) ...[NoticeBox(error!), const SizedBox(height: 12)],
          if (loading)
            const Padding(
              padding: EdgeInsets.only(top: 40),
              child: Center(child: CircularProgressIndicator(color: AppColors.orange500)),
            )
          else if (empty)
            Padding(
              padding: const EdgeInsets.only(top: 40),
              child: Text(emptyText,
                  textAlign: TextAlign.center, style: const TextStyle(fontSize: 14, color: AppColors.zinc500)),
            )
          else
            ...children,
        ],
      ),
    );
  }
}

String formatDateTime(DateTime? time) {
  if (time == null) return '-';
  final t = time.toLocal();
  String two(int n) => n.toString().padLeft(2, '0');
  return '${t.year}.${two(t.month)}.${two(t.day)} ${two(t.hour)}:${two(t.minute)}';
}

const partyLabel = {'USER': '회원', 'LAWYER': '변호사'};
