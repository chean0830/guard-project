"""웹의 이용약관·개인정보처리방침(frontend-web/src/app/{terms,privacy}/page.tsx)을 앱용 Dart 코드로 옮긴다.

문서 내용은 웹 파일 하나만 고치고, 이 스크립트로 앱에 반영한다 (두 곳을 따로 고치다 내용이 어긋나지 않게).

    python frontend-app/tool/sync_legal_docs.py
"""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
WEB = ROOT / 'frontend-web' / 'src' / 'app'
OUT = ROOT / 'frontend-app' / 'lib' / 'legal' / 'legal_docs.dart'


def ts_literal_to_json(src: str) -> str:
    """SECTIONS 배열(작은따옴표 문자열·따옴표 없는 키·끝 쉼표)을 JSON으로 바꾼다."""
    out, i = [], 0
    while i < len(src):
        c = src[i]
        if c == "'":
            j, buf = i + 1, []
            while src[j] != "'":
                if src[j] == '\\':
                    buf.append(src[j + 1])
                    j += 2
                else:
                    buf.append(src[j])
                    j += 1
            out.append(json.dumps(''.join(buf), ensure_ascii=False))
            i = j + 1
        elif src.startswith('//', i):
            i = src.index('\n', i)
        else:
            out.append(c)
            i += 1
    text = ''.join(out)
    text = re.sub(r'(\b[a-zA-Z_]+)\s*:', r'"\1":', text)
    text = re.sub(r',(\s*[\]}])', r'\1', text)
    return text


def read_doc(page: str) -> dict:
    src = (WEB / page / 'page.tsx').read_text(encoding='utf-8').replace('\r\n', '\n')
    body = src[src.index('const SECTIONS'):]
    body = body[body.index('= [') + 2:body.index('\n]\n') + 2]
    props = dict(re.findall(r'(title|effectiveDate|intro)="([^"]*)"', src))
    return {**props, 'sections': json.loads(ts_literal_to_json(body))}


def dart_str(s: str) -> str:
    return "'" + s.replace('\\', '\\\\').replace("'", "\\'").replace('$', '\\$') + "'"


def dart_doc(name: str, doc: dict) -> str:
    lines = [f'const {name} = LegalDoc(',
             f"  title: {dart_str(doc['title'])},",
             f"  effectiveDate: {dart_str(doc['effectiveDate'])},",
             f"  intro: {dart_str(doc['intro'])},",
             '  sections: [']
    for section in doc['sections']:
        lines.append(f"    LegalSection({dart_str(section['title'])}, [")
        for block in section['body']:
            if isinstance(block, list):
                lines.append('      LegalBlock.list([' + ', '.join(dart_str(x) for x in block) + ']),')
            else:
                lines.append(f'      LegalBlock.text({dart_str(block)}),')
        lines.append('    ]),')
    lines += ['  ],', ');']
    return '\n'.join(lines)


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    header = '''// 자동 생성 파일 — 직접 고치지 마세요.
// 원본: frontend-web/src/app/{terms,privacy}/page.tsx, 갱신: python frontend-app/tool/sync_legal_docs.py
// ignore_for_file: lines_longer_than_80_chars

import 'legal_doc.dart';
'''
    OUT.write_text(header + '\n' + dart_doc('termsDoc', read_doc('terms')) + '\n\n'
                   + dart_doc('privacyDoc', read_doc('privacy')) + '\n', encoding='utf-8', newline='\n')
    print(f'wrote {OUT.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
