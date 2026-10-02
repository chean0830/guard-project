import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';

import '../api/api_client.dart';
import '../api/payment_api.dart';
import '../theme.dart';
import '../widgets/common.dart';
import 'payment/toss_payment_screen.dart';
import 'result_screen.dart';

const _propertyTypes = {
  'APARTMENT': '아파트',
  'OFFICETEL': '오피스텔',
  'VILLA': '빌라 (연립·다세대)',
  'MULTI_HOUSEHOLD': '원룸·다가구주택',
};

const _violationBuildingAnswers = {
  '': '아직 확인 안 했어요',
  'NOT_MARKED': '위반건축물 표시 없음',
  'MARKED': '위반건축물 표시 있음',
};

const _priorDepositSources = {
  'OFFICIAL_DOCUMENT': '서류(전입세대 열람·확정일자 부여현황)로 확인',
  'LANDLORD_CLAIM': '임대인·중개사 말만 들음',
  'UNKNOWN': '아직 모름',
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
  final List<UploadFile> _landFiles = [];

  String _propertyType = 'APARTMENT';
  String _contractType = 'JEONSE';
  final _deposit = TextEditingController();
  final _monthlyRent = TextEditingController();
  final _buildingName = TextEditingController();
  final _area = TextEditingController();
  final _landlord = TextEditingController();
  final _address = TextEditingController();
  final _priorDeposit = TextEditingController();
  final _buildingPrice = TextEditingController();
  final _roomCount = TextEditingController();
  String? _priorDepositSource;
  bool _multiHouseholdAcknowledged = false;
  String _violationBuilding = '';

  bool get _isMultiHousehold => _propertyType == 'MULTI_HOUSEHOLD';

  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    for (final c in [_deposit, _monthlyRent, _buildingName, _area, _landlord, _address, _priorDeposit, _buildingPrice, _roomCount]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _takePhoto() async {
    final photo = await _picker.pickImage(source: ImageSource.camera, imageQuality: 90);
    if (photo == null) return;
    await _addImages([photo], _files);
  }

  /// [target]에 고른 파일을 담는다 — 건물 등기부(_files) 또는 다가구 토지 등기부(_landFiles).
  Future<void> _chooseFiles([List<UploadFile>? target]) async {
    final into = target ?? _files;
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
      await _addImages(await _picker.pickMultiImage(imageQuality: 90), into);
    } else if (choice == 'pdf') {
      final file = await FilePicker.pickFile(type: FileType.custom, allowedExtensions: ['pdf']);
      if (file == null) return;
      final bytes = await file.readAsBytes();
      setState(() => into.add(UploadFile(name: file.name, bytes: bytes, contentType: 'application/pdf')));
    }
  }

  Future<void> _addImages(List<XFile> images, List<UploadFile> into) async {
    final added = <UploadFile>[];
    for (final img in images) {
      added.add(UploadFile(
        name: img.name,
        bytes: await img.readAsBytes(),
        contentType: img.mimeType ?? (img.name.toLowerCase().endsWith('.png') ? 'image/png' : 'image/jpeg'),
      ));
    }
    setState(() => into.addAll(added));
  }

  int? _parseInt(String text) => int.tryParse(text.replaceAll(',', '').trim());

  Future<void> _submit() async {
    setState(() => _error = null);
    if (_files.isEmpty) {
      setState(() => _error = '등기부등본을 1장 이상 올려주세요.');
      return;
    }
    if (!_formKey.currentState!.validate()) return;
    if (_isMultiHousehold && !_multiHouseholdAcknowledged) {
      setState(() => _error = '다가구주택 안내를 확인하고 체크해주세요.');
      return;
    }
    FocusScope.of(context).unfocus();

    setState(() => _loading = true);
    try {
      final result = await widget.api.analyze(AnalyzeRequest(
        files: List.of(_files),
        propertyType: _propertyType,
        contractType: _contractType,
        depositAmount: _parseInt(_deposit.text)!,
        monthlyRent: _contractType == 'WOLSE' ? _parseInt(_monthlyRent.text) : null,
        buildingName: _isMultiHousehold ? null : _buildingName.text,
        exclusiveAreaSqm: _isMultiHousehold ? null : double.tryParse(_area.text.trim()),
        declaredLandlordName: _landlord.text,
        declaredAddress: _address.text,
        priorDepositSource: _isMultiHousehold ? _priorDepositSource : null,
        priorDepositTotal:
            _isMultiHousehold && _priorDepositSource != 'UNKNOWN' ? _parseInt(_priorDeposit.text) : null,
        buildingPrice: _isMultiHousehold ? _parseInt(_buildingPrice.text) : null,
        violationBuilding: _violationBuilding.isEmpty ? null : _violationBuilding,
        landFiles: _isMultiHousehold ? List.of(_landFiles) : const [],
        roomCount: _isMultiHousehold ? _parseInt(_roomCount.text) : null,
      ));
      if (!mounted) return;
      Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => ResultScreen(result: result, onOpenConsult: widget.onOpenConsult)));
    } on ApiException catch (e) {
      if (e.loginRequired) {
        widget.onLoggedOut();
      } else if (e.paymentRequired) {
        _offerAnalysisPayment(e.message);
      } else {
        setState(() => _error = e.message);
      }
    } catch (e, stack) {
      setState(() => _error = describeError(e, stack));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  /// 무료 분석 횟수를 다 쓰면 990원 분석 이용권 결제를 권하고, 결제되면 같은 내용으로 바로 다시 분석한다.
  Future<void> _offerAnalysisPayment(String message) async {
    final pay = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: Colors.white,
        title: const Text('무료 분석 횟수를 모두 사용했어요', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
        content: Text(keepAll('$message\n\n분석 이용권(1회 990원)을 결제하면 바로 이어서 분석해드려요.'),
            style: const TextStyle(fontSize: 14, color: AppColors.zinc600, height: 1.5)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('나중에')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppColors.orange500),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('990원 결제하고 분석'),
          ),
        ],
      ),
    );
    if (pay != true || !mounted) return;
    final outcome = await TossPaymentScreen.open(context, PaymentApi(widget.api), 'ANALYSIS');
    if (outcome == PaymentOutcome.paid && mounted) _submit();
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
                  if (_isMultiHousehold) ...[const SizedBox(height: 12), const _MultiHouseholdWarning()],
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
                  if (_isMultiHousehold) ...[
                    const SizedBox(height: 18),
                    const FieldLabel('먼저 들어온 세입자 보증금, 어떻게 확인하셨나요?',
                        required: true, help: '서류로 확인하지 않았다면 계산 결과와 상관없이 위험으로 표시해드려요.'),
                    DropdownButtonFormField<String>(
                      initialValue: _priorDepositSource,
                      isExpanded: true,
                      hint: const Text('선택해주세요'),
                      items: [
                        for (final e in _priorDepositSources.entries)
                          DropdownMenuItem(value: e.key, child: Text(e.value, overflow: TextOverflow.ellipsis)),
                      ],
                      onChanged: (v) => setState(() => _priorDepositSource = v),
                      validator: (v) => v == null ? '확인 방법을 골라주세요.' : null,
                      borderRadius: BorderRadius.circular(12),
                      dropdownColor: Colors.white,
                    ),
                    if (_priorDepositSource != 'UNKNOWN') ...[
                      const SizedBox(height: 18),
                      const FieldLabel('먼저 들어온 세입자 보증금 합계 (원)',
                          required: true,
                          help: '나보다 먼저 전입·확정일자를 받은 세입자들의 보증금을 모두 더한 금액이에요. 없으면 0을 입력하세요.'),
                      TextFormField(
                        controller: _priorDeposit,
                        keyboardType: TextInputType.number,
                        inputFormatters: digitsOnly,
                        decoration: const InputDecoration(hintText: '예: 300000000'),
                        validator: (v) => _parseInt(v ?? '') != null ? null : '금액을 입력해주세요. 없으면 0을 입력하세요.',
                      ),
                    ],
                    const SizedBox(height: 18),
                    const FieldLabel('건물 전체 방(호실) 수',
                        required: false,
                        help: '비워두시면 건축물대장 가구수로 계산해요. 실제 방이 더 많아 보이면(방 쪼개기) 직접 세어서 입력해주세요. '
                            '나중에 들어올 소액임차인이 먼저 받아갈 수 있는 금액을 계산하는 데 써요.'),
                    TextFormField(
                      controller: _roomCount,
                      keyboardType: TextInputType.number,
                      inputFormatters: digitsOnly,
                      decoration: const InputDecoration(hintText: '예: 8'),
                    ),
                    const SizedBox(height: 18),
                    const FieldLabel('토지 등기부등본',
                        required: false,
                        help: '다가구주택은 건물과 토지 등기부가 따로 있어요. 토지 등기부도 올리시면 토지에만 걸린 근저당·압류와 '
                            '토지 소유자가 건물 소유자와 같은지까지 확인해드려요.'),
                    PillButton(
                        label: '토지 등기부 선택',
                        onPressed: _loading ? null : () => _chooseFiles(_landFiles),
                        filled: false,
                        height: 44),
                    for (final (i, f) in _landFiles.indexed)
                      Container(
                        margin: const EdgeInsets.only(top: 6),
                        padding: const EdgeInsets.only(left: 12),
                        decoration: BoxDecoration(
                          color: AppColors.zinc50,
                          border: Border.all(color: AppColors.zinc200),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Row(children: [
                          Expanded(
                            child: Text('${i + 1}. ${f.name}',
                                overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13)),
                          ),
                          TextButton(
                            onPressed: _loading ? null : () => setState(() => _landFiles.removeAt(i)),
                            child: const Text('삭제', style: TextStyle(fontSize: 13, color: AppColors.zinc500)),
                          ),
                        ]),
                      ),
                    const SizedBox(height: 18),
                    const FieldLabel('건물 전체 시세 (원)',
                        required: false,
                        help: '다가구는 실거래가로 이 건물 시세를 찾을 수 없어 직접 입력받아요. 비워두시면 공시가격으로 계산하는데, '
                            '공시가격은 보통 실제 시세보다 낮아 보수적인 결과가 나와요.'),
                    TextFormField(
                      controller: _buildingPrice,
                      keyboardType: TextInputType.number,
                      inputFormatters: digitsOnly,
                      decoration: const InputDecoration(hintText: '예: 1500000000'),
                    ),
                    const SizedBox(height: 12),
                    CheckboxListTile(
                      value: _multiHouseholdAcknowledged,
                      onChanged: (v) => setState(() => _multiHouseholdAcknowledged = v ?? false),
                      controlAffinity: ListTileControlAffinity.leading,
                      contentPadding: EdgeInsets.zero,
                      activeColor: AppColors.orange500,
                      title: Text(keepAll('결과가 입력한 값에 따라 달라지고, 서비스가 입력값을 확인하지 않는다는 점을 이해했어요.'),
                          style: const TextStyle(fontSize: 13, height: 1.5)),
                    ),
                  ],
                  const SizedBox(height: 24),
                  const Divider(),
                  const SizedBox(height: 20),
                  Text(keepAll('여기부터는 몰라도 괜찮아요. 다만 알려주시면 훨씬 더 정확하게 확인해드릴 수 있어요.'),
                      style: TextStyle(fontSize: 14, height: 1.5, color: AppColors.zinc500)),
                  if (!_isMultiHousehold) ...[
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
                  ],
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
                  const SizedBox(height: 18),
                  const FieldLabel('건축물대장 위반건축물 표시',
                      required: false,
                      help: "정부24에서 건축물대장을 무료로 열람하면 첫 장 위쪽에 '위반건축물' 표시가 있는지 볼 수 있어요. "
                          '이 정보는 공공 API로 받을 수 없어 직접 확인해주셔야 해요.'),
                  _dropdown(_violationBuilding, _violationBuildingAnswers, (v) => setState(() => _violationBuilding = v)),
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

/// 다가구주택은 먼저 들어온 세입자 보증금이 등기부에 안 나와서, 입력값에만 의존한다는 걸 분명히 알린다 (웹과 같은 문구).
class _MultiHouseholdWarning extends StatelessWidget {
  const _MultiHouseholdWarning();

  @override
  Widget build(BuildContext context) {
    const bodyStyle = TextStyle(fontSize: 13, height: 1.5, color: AppColors.red950);
    Widget bullet(String text) => Padding(
          padding: const EdgeInsets.only(top: 6),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('•  ', style: bodyStyle),
            Expanded(child: Text(keepAll(text), style: bodyStyle)),
          ]),
        );

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.red50,
        border: Border.all(color: AppColors.red500, width: 1.5),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(keepAll('⚠️ 다가구주택은 등기부만으로 안전한지 알 수 없어요'),
            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: AppColors.red800)),
        bullet('건물 전체에 등기부가 하나뿐이라, 나보다 먼저 들어온 세입자들의 보증금이 등기부에 나오지 않아요. '
            '경매로 넘어가면 이 보증금이 내 보증금보다 먼저 배당돼요.'),
        bullet('그래서 이 결과는 직접 입력하신 선순위 보증금과 건물 시세가 정확하다는 전제에서만 의미가 있어요. '
            "서비스는 입력값을 확인하지 않고, 결과가 좋아도 '안전'으로 판정하지 않아요."),
        bullet('다가구 전세사기는 임대인이 선순위 보증금을 줄여 말하는 방식으로 자주 일어나요. 임대인 동의를 받아 '
            '주민센터에서 전입세대 열람내역서·확정일자 부여현황을 꼭 직접 확인하세요.'),
        const SizedBox(height: 8),
        Text(keepAll('원룸이라도 등기부 첫 줄이 [집합건물]로 시작하면 다가구가 아니라 빌라나 오피스텔이에요. 그 유형으로 골라주세요.'),
            style: const TextStyle(fontSize: 12, height: 1.5, color: AppColors.red800)),
      ]),
    );
  }
}
