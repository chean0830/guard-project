/// 이용약관·개인정보처리방침 문서 구조 (웹 LegalDocument와 같은 모양).
class LegalDoc {
  final String title;
  final String effectiveDate;
  final String intro;
  final List<LegalSection> sections;

  const LegalDoc({required this.title, required this.effectiveDate, required this.intro, required this.sections});
}

class LegalSection {
  final String title;
  final List<LegalBlock> body;

  const LegalSection(this.title, this.body);
}

/// 문단 하나 또는 글머리표 목록 하나.
class LegalBlock {
  final String? text;
  final List<String>? items;

  const LegalBlock.text(String this.text) : items = null;

  const LegalBlock.list(List<String> this.items) : text = null;
}
