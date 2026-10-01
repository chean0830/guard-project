import 'package:flutter/material.dart';

import '../../api/admin_api.dart';
import '../../api/api_client.dart';
import '../../theme.dart';
import 'admin_common.dart';
import 'admin_lawyer_message_screen.dart';

/// 회원 관리 (웹 관리자 > 회원 관리): 회원·변호사 검색, 이용 정지·해제. 20명씩 페이지로 본다.
class AdminMemberScreen extends StatefulWidget {
  final AdminApi adminApi;
  final VoidCallback onLoggedOut;

  const AdminMemberScreen({super.key, required this.adminApi, required this.onLoggedOut});

  @override
  State<AdminMemberScreen> createState() => _AdminMemberScreenState();
}

class _AdminMemberScreenState extends State<AdminMemberScreen> {
  final _search = TextEditingController();
  String _type = 'USER';
  int _page = 0;
  MemberPage? _data;
  String? _error;
  bool _loading = true;
  int? _busyId;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final type = _type;
    setState(() => _loading = true);
    try {
      final data = await widget.adminApi.members(type, page: _page, query: _search.text);
      if (mounted && type == _type) {
        setState(() {
          _data = data;
          _error = null;
        });
      }
    } catch (e, stack) {
      _handleError(e, stack);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _handleError(Object e, StackTrace stack) {
    if (e is ApiException && e.loginRequired) return widget.onLoggedOut();
    if (mounted) setState(() => _error = describeError(e, stack));
  }

  void _selectType(String type) {
    _search.clear();
    setState(() {
      _type = type;
      _page = 0;
      _data = null;
    });
    _load();
  }

  void _goTo(int page) {
    setState(() => _page = page);
    _load();
  }

  void _toast(String text) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _setBlocked(AdminMember member, bool blocked) async {
    final who = member.name?.isNotEmpty == true ? member.name! : member.email;
    String? reason;
    if (blocked) {
      reason = await reasonDialog(context,
          title: '$who 이용 정지', hint: '정지 사유 (선택)', confirmLabel: '이용 정지');
      if (reason == null) return;
    } else {
      final ok = await confirmDialog(context,
          title: '정지를 해제할까요?', message: '$who의 이용 정지를 해제할까요?', confirmLabel: '해제', color: AppColors.zinc900);
      if (!ok) return;
    }
    setState(() => _busyId = member.id);
    try {
      await widget.adminApi.setMemberBlocked(_type, member.id, blocked, reason: reason);
      _toast(blocked ? '이용을 정지했어요.' : '정지를 해제했어요.');
      await _load();
    } catch (e, stack) {
      _handleError(e, stack);
    } finally {
      if (mounted) setState(() => _busyId = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final data = _data;
    return AdminListView(
      title: '회원 관리',
      description: data == null ? null : '${_type == 'USER' ? '회원' : '변호사'} ${data.totalElements}명',
      filters: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        FilterChips<String>(
          options: const [('USER', '회원'), ('LAWYER', '변호사')],
          selected: _type,
          onSelected: _selectType,
        ),
        const SizedBox(height: 10),
        TextField(
          controller: _search,
          textInputAction: TextInputAction.search,
          onSubmitted: (_) => _goTo(0),
          decoration: InputDecoration(
            hintText: '이메일 또는 이름으로 검색',
            prefixIcon: const Icon(Icons.search, size: 20),
            suffixIcon: IconButton(
              icon: const Icon(Icons.close, size: 18),
              onPressed: () {
                _search.clear();
                _goTo(0);
              },
            ),
          ),
        ),
      ]),
      error: _error,
      loading: _loading && data == null,
      empty: data != null && data.items.isEmpty,
      emptyText: '검색 결과가 없어요.',
      onRefresh: _load,
      children: [
        for (final m in data?.items ?? const <AdminMember>[]) _tile(m),
        if (data != null && data.totalPages > 1)
          Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            IconButton(
              onPressed: _page > 0 && !_loading ? () => _goTo(_page - 1) : null,
              icon: const Icon(Icons.chevron_left),
            ),
            Text('${_page + 1} / ${data.totalPages}', style: const TextStyle(fontSize: 14, color: AppColors.zinc600)),
            IconButton(
              onPressed: _page + 1 < data.totalPages && !_loading ? () => _goTo(_page + 1) : null,
              icon: const Icon(Icons.chevron_right),
            ),
          ]),
      ],
    );
  }

  void _openMessages(AdminMember member) {
    Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => AdminLawyerMessageScreen(
        adminApi: widget.adminApi,
        lawyerId: member.id,
        lawyerName: member.name?.isNotEmpty == true ? member.name! : member.email,
        onLoggedOut: widget.onLoggedOut,
      ),
    ));
  }

  Widget _tile(AdminMember member) {
    final busy = _busyId == member.id;
    return AdminCard(
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Wrap(spacing: 6, crossAxisAlignment: WrapCrossAlignment.center, children: [
              Text(member.name?.isNotEmpty == true ? member.name! : '(이름 없음)',
                  style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
              if (member.blocked) const StatusBadge('정지됨', background: AppColors.zinc900, foreground: Colors.white),
            ]),
            const SizedBox(height: 2),
            Text(member.email, style: const TextStyle(fontSize: 13, color: AppColors.zinc500)),
            const SizedBox(height: 4),
            Text(
              [if (member.subtitle != null) member.subtitle!, '받은 신고 ${member.reportCount}건'].join(' · '),
              style: TextStyle(
                  fontSize: 12, color: member.reportCount > 0 ? AppColors.red600 : AppColors.zinc500),
            ),
            if (member.blocked && member.blockedReason?.isNotEmpty == true)
              Text('정지 사유: ${member.blockedReason}', style: const TextStyle(fontSize: 12, color: AppColors.zinc500)),
          ]),
        ),
        const SizedBox(width: 8),
        Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
          member.blocked
              ? smallPill(busy ? '...' : '정지 해제', busy ? null : () => _setBlocked(member, false))
              : smallPill(busy ? '...' : '이용 정지', busy ? null : () => _setBlocked(member, true), color: AppColors.red600),
          if (_type == 'LAWYER') ...[
            const SizedBox(height: 6),
            smallPill('메시지', () => _openMessages(member), color: AppColors.orange600),
          ],
        ]),
      ]),
    );
  }
}
