import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/common.dart';
import 'legal_screen.dart';

/// 변호사 회원가입 (웹 /lawyer/signup과 같은 구성). 자격 서류를 내면 관리자 승인 뒤 로그인할 수 있다.
class LawyerSignupScreen extends StatefulWidget {
  final ApiClient api;

  const LawyerSignupScreen({super.key, required this.api});

  @override
  State<LawyerSignupScreen> createState() => _LawyerSignupScreenState();
}

class _LawyerSignupScreenState extends State<LawyerSignupScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _name = TextEditingController();
  final _lawFirm = TextEditingController();
  final _barNumber = TextEditingController();
  final List<UploadFile> _documents = [];
  bool _submitting = false;
  String? _error;
  String? _doneMessage;

  @override
  void dispose() {
    for (final c in [_email, _password, _name, _lawFirm, _barNumber]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _addDocument() async {
    final choice = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      backgroundColor: Colors.white,
      builder: (context) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(
            leading: const Icon(Icons.picture_as_pdf_outlined, color: AppColors.orange600),
            title: const Text('PDF 파일 선택'),
            onTap: () => Navigator.pop(context, 'pdf'),
          ),
          ListTile(
            leading: const Icon(Icons.photo_library_outlined, color: AppColors.orange600),
            title: const Text('사진 앨범에서 선택 (JPG)'),
            onTap: () => Navigator.pop(context, 'photo'),
          ),
          ListTile(
            leading: const Icon(Icons.photo_camera_outlined, color: AppColors.orange600),
            title: const Text('카메라로 촬영'),
            onTap: () => Navigator.pop(context, 'camera'),
          ),
          const SizedBox(height: 8),
        ]),
      ),
    );
    if (choice == 'pdf') {
      final file = await FilePicker.pickFile(type: FileType.custom, allowedExtensions: ['pdf']);
      if (file == null) return;
      final bytes = await file.readAsBytes();
      setState(() => _documents.add(UploadFile(name: file.name, bytes: bytes, contentType: 'application/pdf')));
    } else if (choice == 'photo' || choice == 'camera') {
      // 서버는 PDF·JPG만 받으므로, 사진은 JPEG로 다시 저장해서 올린다.
      final image = await ImagePicker().pickImage(
        source: choice == 'camera' ? ImageSource.camera : ImageSource.gallery,
        imageQuality: 90,
      );
      if (image == null) return;
      final bytes = await image.readAsBytes();
      setState(() => _documents.add(UploadFile(name: image.name, bytes: bytes, contentType: 'image/jpeg')));
    }
  }

  Future<void> _submit() async {
    setState(() => _error = null);
    if (!_formKey.currentState!.validate()) return;
    if (_documents.isEmpty) {
      setState(() => _error = '자격 증명 서류를 1개 이상 첨부해주세요.');
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() => _submitting = true);
    try {
      final message = await widget.api.lawyerSignup(
        email: _email.text.trim(),
        password: _password.text,
        name: _name.text.trim(),
        lawFirm: _lawFirm.text,
        barNumber: _barNumber.text.trim(),
        documents: List.of(_documents),
      );
      setState(() => _doneMessage = message);
    } catch (e, stack) {
      setState(() => _error = describeError(e, stack));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Widget _field(String label, TextEditingController c,
      {String? hint, String? help, bool required = true, bool obscure = false, TextInputType? keyboard,
      String? Function(String)? extraCheck}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        FieldLabel(label, help: help),
        TextFormField(
          controller: c,
          obscureText: obscure,
          keyboardType: keyboard,
          decoration: InputDecoration(hintText: hint),
          validator: (v) {
            final value = (v ?? '').trim();
            if (required && value.isEmpty) return '$label을(를) 입력해주세요.';
            return extraCheck?.call(value);
          },
        ),
      ]),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_doneMessage != null) {
      return Scaffold(
        appBar: brandAppBar(showBack: true),
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
              const Icon(Icons.verified_outlined, size: 56, color: AppColors.emerald600),
              const SizedBox(height: 16),
              const Text('제출 완료', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              Text(keepAll(_doneMessage!),
                  textAlign: TextAlign.center, style: const TextStyle(fontSize: 14, color: AppColors.zinc600, height: 1.5)),
              const SizedBox(height: 28),
              PillButton(label: '로그인 화면으로', onPressed: () => Navigator.of(context).pop(), color: AppColors.zinc900),
            ]),
          ),
        ),
      );
    }

    return Scaffold(
      appBar: brandAppBar(showBack: true),
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 32, 20, 32),
            children: [
              const Text('변호사 회원가입', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              Text(keepAll('변호사 자격을 확인할 수 있는 서류를 제출해주세요. 관리자 검수 후 승인되면 로그인하실 수 있어요.'),
                  style: const TextStyle(fontSize: 14, color: AppColors.zinc500, height: 1.5)),
              const SizedBox(height: 24),
              _field('이메일', _email,
                  keyboard: TextInputType.emailAddress,
                  extraCheck: (v) => v.contains('@') ? null : '이메일 형식을 확인해주세요.'),
              _field('비밀번호', _password,
                  obscure: true,
                  help: '8자 이상으로 입력해주세요.',
                  extraCheck: (v) => v.length >= 8 ? null : '8자 이상으로 입력해주세요.'),
              _field('이름', _name, hint: '예: 김변호'),
              _field('소속 (선택)', _lawFirm, hint: '예: 법무법인 테스트', required: false),
              _field('변호사 등록번호', _barNumber, hint: '대한변호사협회 등록번호'),
              FieldLabel('자격 증명 서류',
                  help: '변호사 자격증 사본, 신분증 등 자격을 확인할 수 있는 서류를 첨부해주세요. (PDF·JPG)'),
              for (final (i, d) in _documents.indexed)
                Container(
                  margin: const EdgeInsets.only(bottom: 6),
                  padding: const EdgeInsets.only(left: 12),
                  decoration: BoxDecoration(
                    color: AppColors.zinc50,
                    border: Border.all(color: AppColors.zinc200),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(children: [
                    Icon(d.contentType == 'application/pdf' ? Icons.picture_as_pdf_outlined : Icons.image_outlined,
                        size: 18, color: AppColors.zinc500),
                    const SizedBox(width: 8),
                    Expanded(child: Text(d.name, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13))),
                    TextButton(
                      onPressed: _submitting ? null : () => setState(() => _documents.removeAt(i)),
                      child: const Text('삭제', style: TextStyle(fontSize: 13, color: AppColors.zinc500)),
                    ),
                  ]),
                ),
              PillButton(
                label: '서류 첨부',
                icon: Icons.attach_file,
                onPressed: _submitting ? null : _addDocument,
                filled: false,
                height: 44,
              ),
              if (_error != null) ...[const SizedBox(height: 16), NoticeBox(_error!)],
              const SizedBox(height: 24),
              PillButton(
                label: _submitting ? '제출 중...' : '가입 신청',
                loading: _submitting,
                onPressed: _submit,
                color: AppColors.zinc900,
              ),
              const SizedBox(height: 14),
              const LegalConsentText(prefix: '가입 신청하면 '),
              const SizedBox(height: 20),
              Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                const Text('이미 승인된 계정이 있으신가요? ', style: TextStyle(fontSize: 14, color: AppColors.zinc500)),
                GestureDetector(
                  onTap: () => Navigator.of(context).pop(),
                  child: const Text('변호사 로그인',
                      style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: AppColors.zinc900)),
                ),
              ]),
            ],
          ),
        ),
      ),
    );
  }
}
