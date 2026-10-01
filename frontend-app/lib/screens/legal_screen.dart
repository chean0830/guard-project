import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

import '../legal/legal_doc.dart';
import '../legal/legal_docs.dart';
import '../theme.dart';
import '../widgets/common.dart';

/// 이용약관·개인정보처리방침 (웹 /terms, /privacy와 같은 내용·모양).
class LegalScreen extends StatelessWidget {
  final LegalDoc doc;

  const LegalScreen({super.key, required this.doc});

  static void open(BuildContext context, LegalDoc doc) =>
      Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => LegalScreen(doc: doc)));

  @override
  Widget build(BuildContext context) {
    const body = TextStyle(fontSize: 14, height: 1.65, color: AppColors.zinc600);
    return Scaffold(
      appBar: brandAppBar(showBack: true),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 32, 20, 40),
          children: [
            Text(doc.title, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w700)),
            const SizedBox(height: 6),
            Text('시행일: ${doc.effectiveDate}', style: const TextStyle(fontSize: 13, color: AppColors.zinc400)),
            const SizedBox(height: 20),
            Text(keepAll(doc.intro), style: body),
            for (final (i, section) in doc.sections.indexed) ...[
              const SizedBox(height: 28),
              Text('제${i + 1}조 ${section.title}', style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
              const SizedBox(height: 8),
              for (final block in section.body)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: block.items == null
                      ? Text(keepAll(block.text!), style: body)
                      : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          for (final item in block.items!)
                            Padding(
                              padding: const EdgeInsets.only(left: 4, bottom: 4),
                              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                const Text('•  ', style: body),
                                Expanded(child: Text(keepAll(item), style: body)),
                              ]),
                            ),
                        ]),
                ),
            ],
          ],
        ),
      ),
    );
  }
}

/// "가입하면 이용약관과 개인정보처리방침에 동의하는 것으로 봅니다." — 두 문서 이름을 눌러 볼 수 있게.
class LegalConsentText extends StatelessWidget {
  final String prefix;
  final String suffix;

  const LegalConsentText({super.key, this.prefix = '가입하면 ', this.suffix = '에 동의하는 것으로 봅니다.'});

  @override
  Widget build(BuildContext context) {
    const base = TextStyle(fontSize: 12, color: AppColors.zinc400, height: 1.5);
    const link = TextStyle(fontSize: 12, color: AppColors.zinc500, decoration: TextDecoration.underline);
    return Text.rich(
      TextSpan(style: base, children: [
        TextSpan(text: prefix),
        TextSpan(
            text: '이용약관',
            style: link,
            recognizer: TapGestureRecognizer()..onTap = () => LegalScreen.open(context, termsDoc)),
        const TextSpan(text: '과 '),
        TextSpan(
            text: '개인정보처리방침',
            style: link,
            recognizer: TapGestureRecognizer()..onTap = () => LegalScreen.open(context, privacyDoc)),
        TextSpan(text: suffix),
      ]),
      textAlign: TextAlign.center,
    );
  }
}

/// 화면 아래 "이용약관 · 개인정보처리방침" 링크 줄.
class LegalFooterLinks extends StatelessWidget {
  const LegalFooterLinks({super.key});

  @override
  Widget build(BuildContext context) {
    const style = TextStyle(fontSize: 12, color: AppColors.zinc400);
    return Row(mainAxisAlignment: MainAxisAlignment.center, children: [
      TextButton(onPressed: () => LegalScreen.open(context, termsDoc), child: const Text('이용약관', style: style)),
      const Text('·', style: style),
      TextButton(
        onPressed: () => LegalScreen.open(context, privacyDoc),
        child: const Text('개인정보처리방침', style: TextStyle(fontSize: 12, color: AppColors.zinc500, fontWeight: FontWeight.w600)),
      ),
    ]);
  }
}
