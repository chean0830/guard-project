import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/common.dart';
import 'result_screen.dart';

const _propertyTypes = {
  'APARTMENT': '아파트',
  'OFFICETEL': '오피스텔',
  'VILLA': '빌라 (연립·다세대)',
};

const _contractTypes = {
  'JEONSE': '전세',
  'WOLSE': '월세',
};

/// 회원 홈의 "등기부 분석" 탭. 웹의 "지금 바로 확인해보세요" 분석 폼과 같은 구성.
class AnalyzeScreen extends StatefulWidget {
  final ApiClient api;
  final VoidCallback onLoggedOut;

  /// 고위험 결과에서 "변호사와 상담하기"를 누르면 상담 탭으로 보낸다.
  final VoidCallback onOpenConsult;

  const AnalyzeScreen({super.key, required this.api, required this.onLoggedOut, required this.onOpenConsult});

  @override
  State<AnalyzeScreen> createState() => _AnalyzeScreenState();
}

class _AnalyzeScreenState extends State<AnalyzeScreen> {
  final _formKey = GlobalKey<FormState>();
  final _picker = ImagePicker();
  final List<UploadFile> _files = [];

  String _propertyType = 'APARTMENT';
  String _contractType = 'JEONSE';
  final _deposit = TextEditingController();
  final _monthlyRent = TextEditingController();
  final _buildingName = TextEditingController();
  final _area = TextEditingController();
  final _landlord = TextEditingController();
  final _address = TextEditingController();

  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    for (final c in [_deposit, _monthlyRent, _buildingName, _area, _landlord, _address]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _takePhoto() async {
    final photo = await _picker.pickImage(source: ImageSource.camera, imageQuality: 90);
    if (photo == null) return;
    await _addImages([photo]);
  }

  Future<void> _chooseFiles() async {
    final choice = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      backgroundColor: Colors.white,
      builder: (context) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(
            leading: const Icon(Icons.photo_library_outlined, color: AppColors.orange600),
            title: const Text('사진 앨범에서 선택'),
            subtitle: const Text('여러 장을 한 번에 고를 수 있어요'),
            onTap: () => Navigator.pop(context, 'photos'),
          ),
          ListTile(
            leading: const Icon(Icons.picture_as_pdf_outlined, color: AppColors.orange600),
            title: const Text('PDF 파일 선택'),
            subtitle: const Text('인터넷등기소에서 받은 등기부등본'),
            onTap: () => Navigator.pop(context, 'pdf'),
          ),
          const SizedBox(height: 8),
        ]),
      ),
    );
    if (choice == 'photos') {
      await _addImages(await _picker.pickMultiImage(imageQuality: 90));
    } else if (choice == 'pdf') {
      final file = await FilePicker.pickFile(type: FileType.custom, allowedExtensions: ['pdf']);
      if (file == null) return;
      final bytes = await file.readAsBytes();
      setState(() => _files.add(UploadFile(name: file.name, bytes: bytes, contentType: 'application/pdf')));
    }
  }

  Future<void> _addImages(List<XFile> images) async {
    final added = <UploadFile>[];
    for (final img in images) {
      added.add(UploadFile(
        name: img.name,
        bytes: await img.readAsBytes(),
        contentType: img.mimeType ?? (img.name.toLowerCase().endsWith('.png') ? 'image/png' : 'image/jpeg'),
      ));
    }
    setState(() => _files.addAll(added));
  }

  int? _parseInt(String text) => int.tryParse(text.replaceAll(',', '').trim());

  Future<void> _submit() async {
    setState(() => _error = null);
    if (_files.isEmpty) {
      setState(() => _error = '등기부등본을 1장 이상 올려주세요.');
      return;
    }
    if (!_formKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();

    setState(() => _loading = true);
    try {
      final result = await widget.api.analyze(AnalyzeRequest(
        files: List.of(_files),
        propertyType: _propertyType,
        contractType: _contractType,
        depositAmount: _parseInt(_deposit.text)!,
        monthlyRent: _contractType == 'WOLSE' ? _parseInt(_monthlyRent.text) : null,
        buildingName: _buildingName.text,
        exclusiveAreaSqm: double.tryParse(_area.text.trim()),
        declaredLandlordName: _landlord.text,
        declaredAddress: _address.text,
      ));
      if (!mounted) return;
      Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => ResultScreen(result: result, onOpenConsult: widget.onOpenConsult)));
    } on ApiException catch (e) {
      if (e.loginRequired) {
        widget.onLoggedOut();
      } else if (e.paymentRequired) {
        setState(() => _error = '${e.message}\n앱 결제는 준비 중이에요. 웹에서 분석 이용권을 구매한 뒤 다시 시도해주세요.');
      } else {
        setState(() => _error = e.message);
      }
    } catch (e, stack) {
      setState(() => _error = describeError(e, stack));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Widget _dropdown(String value, Map<String, String> options, ValueChanged<String> onChanged) {
    return DropdownButtonFormField<String>(
      initialValue: value,
      items: [for (final e in options.entries) DropdownMenuItem(value: e.key, child: Text(e.value))],
      onChanged: (v) => onChanged(v!),
      borderRadius: BorderRadius.circular(12),
      dropdownColor: Colors.white,
    );
  }

  @override
  Widget build(BuildContext context) {
    final digitsOnly = [FilteringTextInputFormatter.digitsOnly];

    return Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 28, 16, 32),
            children: [
              const Text('지금 바로 확인해보세요',
                  textAlign: TextAlign.center, style: TextStyle(fontSize: 23, fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              const Text('업로드한 파일은 분석 후 즉시 삭제되며\n서버에 저장되지 않습니다.',
                  textAlign: TextAlign.center, style: TextStyle(fontSize: 14, color: AppColors.zinc500, height: 1.5)),
              const SizedBox(height: 4),
              const Text('아이디당 5회까지 무료, 이후에는 1회 990원이에요.',
                  textAlign: TextAlign.center, style: TextStyle(fontSize: 12, color: AppColors.zinc400)),
              const SizedBox(height: 24),
              Container(
                padding: const EdgeInsets.fromLTRB(20, 24, 20, 24),
                decoration: BoxDecoration(
                  color: Colors.white,
                  border: Border.all(color: AppColors.zinc200),
                  borderRadius: BorderRadius.circular(24),
                  boxShadow: const [BoxShadow(color: Color(0x14000000), blurRadius: 24, offset: Offset(0, 10))],
                ),
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: AppColors.amber50,
                      border: Border.all(color: AppColors.amber200),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(keepAll('본인이 계약 당사자이거나 본인 명의로 열람 가능한 부동산에 한해 사용해주세요.'),
                        style: TextStyle(fontSize: 13, height: 1.5, color: AppColors.amber900)),
                  ),
                  const SizedBox(height: 22),
                  const FieldLabel('등기부등본',
                      required: true,
                      help: 'PDF는 그대로 올려주시고, 사진으로 찍으셨다면 표제부·갑구·을구가 나온 페이지를 순서대로 담아주세요.'),
                  const SizedBox(height: 4),
                  Row(children: [
                    Expanded(
                      child: PillButton(
                          label: '파일에서 선택', onPressed: _loading ? null : _chooseFiles, height: 44),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: PillButton(
                          label: '카메라로 촬영',
                          onPressed: _loading ? null : _takePhoto,
                          filled: false,
                          height: 44),
                    ),
                  ]),
                  if (_files.isNotEmpty) ...[
                    const SizedBox(height: 10),
                    for (final (i, f) in _files.indexed)
                      Container(
                        margin: const EdgeInsets.only(bottom: 6),
                        padding: const EdgeInsets.only(left: 12),
                        decoration: BoxDecoration(
                          color: AppColors.zinc50,
                          border: Border.all(color: AppColors.zinc200),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Row(children: [
                          Icon(f.contentType == 'application/pdf' ? Icons.picture_as_pdf_outlined : Icons.image_outlined,
                              size: 18, color: AppColors.zinc500),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text('${i + 1}. ${f.name}',
                                overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13)),
                          ),
                          TextButton(
                            onPressed: _loading ? null : () => setState(() => _files.removeAt(i)),
                            child: const Text('삭제', style: TextStyle(fontSize: 13, color: AppColors.zinc500)),
                          ),
                        ]),
                      ),
                  ],
                  const SizedBox(height: 22),
                  const FieldLabel('부동산 유형', required: true),
                  _dropdown(_propertyType, _propertyTypes, (v) => setState(() => _propertyType = v)),
                  const SizedBox(height: 18),
                  const FieldLabel('계약 형태', required: true),
                  _dropdown(_contractType, _contractTypes, (v) => setState(() => _contractType = v)),
                  const SizedBox(height: 18),
                  const FieldLabel('보증금 (원)', required: true),
                  TextFormField(
                    controller: _deposit,
                    keyboardType: TextInputType.number,
                    inputFormatters: digitsOnly,
                    decoration: const InputDecoration(hintText: '예: 200000000'),
                    validator: (v) => (_parseInt(v ?? '') ?? 0) > 0 ? null : '보증금을 입력해주세요.',
                  ),
                  if (_contractType == 'WOLSE') ...[
                    const SizedBox(height: 18),
                    const FieldLabel('월세 (원)',
                        required: true, help: '월세를 법정 전환율로 보증금에 환산해서, 전세와 같은 기준으로 비교해드려요.'),
                    TextFormField(
                      controller: _monthlyRent,
                      keyboardType: TextInputType.number,
                      inputFormatters: digitsOnly,
                      decoration: const InputDecoration(hintText: '예: 500000'),
                      validator: (v) => (_parseInt(v ?? '') ?? 0) > 0 ? null : '월세를 입력해주세요.',
                    ),
                  ],
                  const SizedBox(height: 24),
                  const Divider(),
                  const SizedBox(height: 20),
                  Text(keepAll('여기부터는 몰라도 괜찮아요. 다만 알려주시면 훨씬 더 정확하게 확인해드릴 수 있어요.'),
                      style: TextStyle(fontSize: 14, height: 1.5, color: AppColors.zinc500)),
                  const SizedBox(height: 18),
                  const FieldLabel('단지/건물명', required: false, help: '단지명까지 알려주시면 시세를 더 정확하게 찾아드릴 수 있어요.'),
                  TextFormField(controller: _buildingName, decoration: const InputDecoration(hintText: '예: 반포자이')),
                  const SizedBox(height: 18),
                  const FieldLabel('전용면적 (㎡)',
                      required: false, help: '전용면적까지 알려주시면 같은 평수 거래만 골라서 비교해드려요.'),
                  TextFormField(
                    controller: _area,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: const InputDecoration(hintText: '예: 84.99'),
                  ),
                  const SizedBox(height: 18),
                  const FieldLabel('계약서상 임대인 이름',
                      required: false, help: '임대인 이름까지 적어주시면 등기부상 소유자와 같은 사람인지 확인해드려요.'),
                  TextFormField(controller: _landlord, decoration: const InputDecoration(hintText: '예: 홍길동')),
                  const SizedBox(height: 18),
                  const FieldLabel('계약서상 주소',
                      required: false, help: '계약서에 적힌 주소까지 알려주시면 등기부 주소와 같은 곳인지 확인해드려요.'),
                  TextFormField(
                    controller: _address,
                    decoration: const InputDecoration(hintText: '예: 서울특별시 강남구 테스트로 123 101동 501호'),
                  ),
                  if (_error != null) ...[const SizedBox(height: 18), NoticeBox(_error!)],
                  const SizedBox(height: 24),
                  PillButton(label: _loading ? '분석 중...' : '분석하기', loading: _loading, onPressed: _submit),
                ]),
              ),
              const SizedBox(height: 24),
              Text(keepAll('Project Guard는 법률 자문이 아닌 참고용 정보를 제공하는 개인 프로젝트입니다.'),
                  textAlign: TextAlign.center, style: TextStyle(fontSize: 12, color: AppColors.zinc400)),
            ],
          ),
    );
  }
}
