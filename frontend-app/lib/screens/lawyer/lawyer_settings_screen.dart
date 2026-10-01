import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/api_client.dart';
import '../../api/lawyer_api.dart';
import '../../theme.dart';
import '../../widgets/common.dart';

/// 변호사 설정: 프로필·강점, 이메일 알림, 비밀번호 변경, 차단 관리 (웹 /lawyer/settings와 같은 구성).
class LawyerSettingsScreen extends StatefulWidget {
  final LawyerApi lawyerApi;
  final VoidCallback onLoggedOut;

  const LawyerSettingsScreen({super.key, required this.lawyerApi, required this.onLoggedOut});

  @override
  State<LawyerSettingsScreen> createState() => _LawyerSettingsScreenState();
}

class _LawyerSettingsScreenState extends State<LawyerSettingsScreen> {
  LawyerProfile? _profile;
  List<BlockedEntry>? _blocks;
  String? _loadError;

  final _name = TextEditingController();
  final _lawFirm = TextEditingController();
  final _specialties = TextEditingController();
  final _introduction = TextEditingController();
  final _headline = TextEditingController();
  final _careerYears = TextEditingController();
  final _feeInfo = TextEditingController();
  final _achievements = TextEditingController();
  final _currentPassword = TextEditingController();
  final _newPassword = TextEditingController();

  bool _savingProfile = false;
  bool _changingPassword = false;
  bool _notifications = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    for (final c in [
      _name, _lawFirm, _specialties, _introduction, _headline, _careerYears, _feeInfo, _achievements,
      _currentPassword, _newPassword,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final results = await Future.wait([widget.lawyerApi.profile(), widget.lawyerApi.listBlocks()]);
      final profile = results[0] as LawyerProfile;
      _fill(profile);
      setState(() {
        _profile = profile;
        _notifications = profile.emailNotificationsEnabled;
        _blocks = results[1] as List<BlockedEntry>;
      });
    } on ApiException catch (e) {
      if (e.loginRequired) return _loggedOut();
      setState(() => _loadError = e.message);
    } catch (e, stack) {
      setState(() => _loadError = describeError(e, stack));
    }
  }

  void _fill(LawyerProfile p) {
    _name.text = p.name ?? '';
    _lawFirm.text = p.lawFirm ?? '';
    _specialties.text = p.specialties ?? '';
    _introduction.text = p.introduction ?? '';
    _headline.text = p.headline ?? '';
    _careerYears.text = p.careerYears?.toString() ?? '';
    _feeInfo.text = p.feeInfo ?? '';
    _achievements.text = p.achievements ?? '';
  }

  void _loggedOut() {
    Navigator.of(context).popUntil((r) => r.isFirst);
    widget.onLoggedOut();
  }

  void _toast(String text) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  String? _orNull(TextEditingController c) => c.text.trim().isEmpty ? null : c.text.trim();

  Future<void> _saveProfile() async {
    FocusScope.of(context).unfocus();
    setState(() => _savingProfile = true);
    try {
      final updated = await widget.lawyerApi.updateProfile(LawyerProfile(
        email: _profile!.email,
        emailNotificationsEnabled: _notifications,
        name: _orNull(_name),
        lawFirm: _orNull(_lawFirm),
        specialties: _orNull(_specialties),
        introduction: _orNull(_introduction),
        headline: _orNull(_headline),
        careerYears: int.tryParse(_careerYears.text.trim()),
        feeInfo: _orNull(_feeInfo),
        achievements: _orNull(_achievements),
      ));
      _fill(updated);
      setState(() => _profile = updated);
      _toast('프로필을 저장했어요.');
    } catch (e, stack) {
      _toast(describeError(e, stack));
    } finally {
      if (mounted) setState(() => _savingProfile = false);
    }
  }

  Future<void> _toggleNotifications(bool enabled) async {
    setState(() => _notifications = enabled);
    try {
      await widget.lawyerApi.setEmailNotifications(enabled);
    } catch (e, stack) {
      setState(() => _notifications = !enabled);
      _toast(describeError(e, stack));
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
      await widget.lawyerApi.changePassword(_currentPassword.text, _newPassword.text);
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
      await widget.lawyerApi.unblock(entry.id);
      setState(() => _blocks = _blocks!.where((b) => b.id != entry.id).toList());
      _toast('${entry.name}님 차단을 해제했어요.');
    } catch (e, stack) {
      _toast(describeError(e, stack));
    }
  }

  Widget _field(String label, TextEditingController c,
      {String? hint, int maxLines = 1, TextInputType? keyboard, List<TextInputFormatter>? formatters}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        FieldLabel(label),
        TextField(
          controller: c,
          minLines: maxLines > 1 ? 3 : 1,
          maxLines: maxLines,
          keyboardType: keyboard,
          inputFormatters: formatters,
          decoration: InputDecoration(hintText: hint),
        ),
      ]),
    );
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
    return Scaffold(
      backgroundColor: AppColors.zinc50,
      appBar: brandAppBar(showBack: true),
      body: SafeArea(
        child: _loadError != null
            ? Padding(padding: const EdgeInsets.all(20), child: NoticeBox(_loadError!))
            : profile == null
                ? const Center(child: CircularProgressIndicator(color: AppColors.orange500))
                : ListView(
                    padding: const EdgeInsets.fromLTRB(16, 28, 16, 32),
                    children: [
                      const Text('설정', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
                      const SizedBox(height: 4),
                      Text(profile.email, style: const TextStyle(fontSize: 14, color: AppColors.zinc500)),
                      const SizedBox(height: 20),
                      _card('프로필', [
                        _field('이름', _name),
                        _field('소속', _lawFirm),
                        if (profile.barNumber != null) ...[
                          const FieldLabel('변호사 등록번호'),
                          Text(profile.barNumber!, style: const TextStyle(fontSize: 14, color: AppColors.zinc500)),
                          const SizedBox(height: 16),
                        ],
                        _field('전문 분야', _specialties, hint: '예: 전세사기, 임대차분쟁'),
                        _field('소개', _introduction, maxLines: 6),
                        const Divider(),
                        const SizedBox(height: 16),
                        const Text('회원에게 보여줄 강점', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
                        const SizedBox(height: 4),
                        Text(
                            keepAll('회원이 변호사를 직접 고를 때 이름 아래에 그대로 보여요. 사실에 근거해 작성해주세요 — '
                                '확인되지 않은 승소율이나 ‘최고’ 같은 과장 표현은 변호사 광고 규정에 어긋날 수 있어요.'),
                            style: const TextStyle(fontSize: 12, color: AppColors.zinc500, height: 1.5)),
                        const SizedBox(height: 16),
                        _field('한 줄 강점', _headline, hint: '예: 전세보증금 반환 사건 전문'),
                        _field('경력 (년)', _careerYears,
                            hint: '예: 8',
                            keyboard: TextInputType.number,
                            formatters: [FilteringTextInputFormatter.digitsOnly]),
                        _field('수임료 안내', _feeInfo, hint: '예: 첫 상담 무료 · 착수금 100만원부터'),
                        _field('주요 실적 (한 줄에 하나씩)', _achievements,
                            hint: '예:\n전세보증금 반환 소송 30건 승소\n임대인 파산 사건 배당 참여', maxLines: 6),
                        PillButton(
                          label: _savingProfile ? '저장 중...' : '프로필 저장',
                          loading: _savingProfile,
                          onPressed: _saveProfile,
                        ),
                      ]),
                      _card('알림', [
                        SwitchListTile(
                          contentPadding: EdgeInsets.zero,
                          activeTrackColor: AppColors.orange500,
                          value: _notifications,
                          onChanged: _toggleNotifications,
                          title: Text(keepAll('새 문의가 오면 등록 이메일로 알림 받기'),
                              style: const TextStyle(fontSize: 14)),
                        ),
                      ]),
                      _card('비밀번호 변경', [
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
                      ]),
                      _card('차단 관리', subtitle: '차단한 상대와는 메시지를 주고받을 수 없고, 새 문의에서도 연결되지 않아요.', [
                        if (_blocks!.isEmpty)
                          const Text('차단한 회원이 없어요.', style: TextStyle(fontSize: 14, color: AppColors.zinc500))
                        else
                          for (final b in _blocks!)
                            Padding(
                              padding: const EdgeInsets.only(bottom: 8),
                              child: Row(children: [
                                Expanded(
                                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                    Text(b.name, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
                                    Text('${b.detail ?? ''} · ${formatRelativeTime(b.blockedAt)} 차단',
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
                    ],
                  ),
      ),
    );
  }
}
