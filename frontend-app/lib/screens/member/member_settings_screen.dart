import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/member_api.dart';
import '../../theme.dart';
import '../../widgets/common.dart';
import '../legal_screen.dart';

/// 회원 설정 (웹 /settings): 프로필, 비밀번호 변경, 차단 관리, 회원 탈퇴.
class MemberSettingsScreen extends StatefulWidget {
  final ApiClient api;
  final MemberApi memberApi;
  final VoidCallback onLoggedOut;

  const MemberSettingsScreen({super.key, required this.api, required this.memberApi, required this.onLoggedOut});

  @override
  State<MemberSettingsScreen> createState() => _MemberSettingsScreenState();
}

class _MemberSettingsScreenState extends State<MemberSettingsScreen> {
  MemberProfile? _profile;
  List<BlockedEntry>? _blocks;
  String? _loadError;

  final _name = TextEditingController();
  final _currentPassword = TextEditingController();
  final _newPassword = TextEditingController();
  bool _savingName = false;
  bool _changingPassword = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _name.dispose();
    _currentPassword.dispose();
    _newPassword.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final results = await Future.wait([widget.memberApi.profile(), widget.memberApi.listBlocks()]);
      final profile = results[0] as MemberProfile;
      _name.text = profile.name ?? '';
      setState(() {
        _profile = profile;
        _blocks = results[1] as List<BlockedEntry>;
        _loadError = null;
      });
    } on ApiException catch (e) {
      if (e.loginRequired) return widget.onLoggedOut();
      setState(() => _loadError = e.message);
    } catch (e, stack) {
      setState(() => _loadError = describeError(e, stack));
    }
  }

  void _toast(String text) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _saveName() async {
    FocusScope.of(context).unfocus();
    setState(() => _savingName = true);
    try {
      final updated = await widget.memberApi.updateName(_name.text.trim().isEmpty ? null : _name.text.trim());
      setState(() => _profile = updated);
      _toast('저장되었습니다.');
    } catch (e, stack) {
      _toast(describeError(e, stack));
    } finally {
      if (mounted) setState(() => _savingName = false);
    }
  }

  Future<void> _changePassword() async {
    if (_currentPassword.text.isEmpty || _newPassword.text.length < 8) {
      _toast('현재 비밀번호와 8자 이상의 새 비밀번호를 입력해주세요.');
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() => _changingPassword = true);
    try {
      await widget.memberApi.changePassword(_currentPassword.text, _newPassword.text);
      _currentPassword.clear();
      _newPassword.clear();
      _toast('비밀번호를 변경했어요.');
    } catch (e, stack) {
      _toast(describeError(e, stack));
    } finally {
      if (mounted) setState(() => _changingPassword = false);
    }
  }

  Future<void> _unblock(BlockedEntry entry) async {
    try {
      await widget.memberApi.unblock(entry.id);
      setState(() => _blocks = _blocks!.where((b) => b.id != entry.id).toList());
      _toast('${entry.name} 차단을 해제했어요.');
    } catch (e, stack) {
      _toast(describeError(e, stack));
    }
  }

  Future<void> _withdraw() async {
    final withdrawn = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      showDragHandle: true,
      builder: (_) => _WithdrawSheet(memberApi: widget.memberApi, hasPassword: _profile!.hasPassword),
    );
    if (withdrawn != true) return;
    await widget.api.logout(); // 서버 세션은 이미 지워졌고, 기기에 남은 로그인 정보만 지운다.
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('탈퇴가 완료됐어요. 그동안 이용해주셔서 감사합니다.')));
    }
    widget.onLoggedOut();
  }

  Widget _card(String title, List<Widget> children, {String? subtitle}) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: AppColors.zinc200),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text(title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
        if (subtitle != null) ...[
          const SizedBox(height: 4),
          Text(keepAll(subtitle), style: const TextStyle(fontSize: 13, color: AppColors.zinc500, height: 1.5)),
        ],
        const SizedBox(height: 16),
        ...children,
      ]),
    );
  }

  @override
  Widget build(BuildContext context) {
    final profile = _profile;
    if (_loadError != null) {
      return Padding(padding: const EdgeInsets.all(20), child: NoticeBox(_loadError!));
    }
    if (profile == null) {
      return const Center(child: CircularProgressIndicator(color: AppColors.orange500));
    }
    return Container(
      color: AppColors.zinc50,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 28, 16, 32),
        children: [
          const Text('설정', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Text(profile.email, style: const TextStyle(fontSize: 14, color: AppColors.zinc500)),
          const SizedBox(height: 20),
          _card('프로필', [
            const FieldLabel('표시 이름', help: '변호사와 상담할 때 이메일 대신 보여드릴 이름이에요.'),
            TextField(controller: _name, decoration: const InputDecoration(hintText: '예: 홍길동')),
            const SizedBox(height: 16),
            PillButton(label: _savingName ? '저장 중...' : '프로필 저장', loading: _savingName, onPressed: _saveName),
          ]),
          _card('비밀번호 변경', [
            if (!profile.isLocal)
              Text(keepAll('${profile.provider} 계정으로 로그인 중이에요. 소셜 로그인 계정은 비밀번호가 없어 변경할 수 없어요.'),
                  style: const TextStyle(fontSize: 14, color: AppColors.zinc500))
            else ...[
              const FieldLabel('현재 비밀번호'),
              TextField(controller: _currentPassword, obscureText: true),
              const SizedBox(height: 16),
              const FieldLabel('새 비밀번호', help: '8자 이상으로 입력해주세요.'),
              TextField(controller: _newPassword, obscureText: true),
              const SizedBox(height: 20),
              PillButton(
                label: _changingPassword ? '변경 중...' : '비밀번호 변경',
                loading: _changingPassword,
                onPressed: _changePassword,
                color: AppColors.zinc900,
              ),
            ],
          ]),
          _card('차단 관리', subtitle: '차단한 변호사와는 메시지를 주고받을 수 없고, 새 문의에서도 연결되지 않아요.', [
            if (_blocks!.isEmpty)
              const Text('차단한 변호사가 없어요.', style: TextStyle(fontSize: 14, color: AppColors.zinc500))
            else
              for (final b in _blocks!)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Row(children: [
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(b.name, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
                        Text([if (b.detail != null) b.detail!, '${formatRelativeTime(b.blockedAt)} 차단'].join(' · '),
                            style: const TextStyle(fontSize: 12, color: AppColors.zinc500)),
                      ]),
                    ),
                    OutlinedButton(
                      onPressed: () => _unblock(b),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: AppColors.zinc700,
                        side: const BorderSide(color: AppColors.zinc300),
                        shape: const StadiumBorder(),
                      ),
                      child: const Text('해제'),
                    ),
                  ]),
                ),
          ]),
          const SizedBox(height: 8),
          Center(
            child: TextButton(
              onPressed: _withdraw,
              child: const Text('회원 탈퇴', style: TextStyle(color: AppColors.zinc400, fontSize: 13)),
            ),
          ),
          const LegalFooterLinks(),
        ],
      ),
    );
  }
}

/// 회원 탈퇴 확인 (웹과 같은 안내). 이메일 가입은 비밀번호, 소셜 전용 계정은 "탈퇴" 입력으로 확인한다.
class _WithdrawSheet extends StatefulWidget {
  final MemberApi memberApi;
  final bool hasPassword;

  const _WithdrawSheet({required this.memberApi, required this.hasPassword});

  @override
  State<_WithdrawSheet> createState() => _WithdrawSheetState();
}

class _WithdrawSheetState extends State<_WithdrawSheet> {
  final _input = TextEditingController();
  bool _pending = false;
  String? _error;

  @override
  void dispose() {
    _input.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _pending = true;
      _error = null;
    });
    try {
      await widget.memberApi.withdraw(
        password: widget.hasPassword ? _input.text : null,
        confirmText: widget.hasPassword ? null : _input.text.trim(),
      );
      if (mounted) Navigator.pop(context, true);
    } catch (e, stack) {
      setState(() => _error = describeError(e, stack));
    } finally {
      if (mounted) setState(() => _pending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 0, 20, 20 + MediaQuery.of(context).viewInsets.bottom),
      child: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Text('회원 탈퇴', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
          const SizedBox(height: 8),
          Text(keepAll('정말 탈퇴할까요? 상담 대화와 계정 정보가 삭제되고 되돌릴 수 없어요.'),
              style: const TextStyle(fontSize: 14, color: AppColors.zinc700, height: 1.5)),
          const SizedBox(height: 12),
          for (final line in const [
            '· 계정 정보와 상담 대화, 차단 목록이 삭제되며 되돌릴 수 없어요.',
            '· 결제 기록은 전자상거래법에 따라 5년 동안 보관돼요.',
            '· 쓰지 않은 이용권이나 검토 중인 환불이 있으면 먼저 정리해야 탈퇴할 수 있어요.',
          ])
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Text(keepAll(line), style: const TextStyle(fontSize: 13, color: AppColors.zinc500, height: 1.5)),
            ),
          const SizedBox(height: 16),
          FieldLabel(widget.hasPassword ? '현재 비밀번호' : "확인을 위해 '탈퇴'를 입력하세요"),
          TextField(controller: _input, obscureText: widget.hasPassword, onChanged: (_) => setState(() {})),
          if (_error != null) ...[const SizedBox(height: 12), NoticeBox(_error!)],
          const SizedBox(height: 20),
          PillButton(
            label: _pending ? '처리 중...' : '탈퇴하기',
            loading: _pending,
            onPressed: _input.text.trim().isEmpty ? null : _submit,
            color: AppColors.red600,
          ),
          const SizedBox(height: 8),
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('취소')),
        ]),
      ),
    );
  }
}
