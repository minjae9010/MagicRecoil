"""Regenerate document TOCs, check links, and export GitHub Wiki pages."""
import argparse
import html
import re
import unicodedata
from pathlib import Path
from urllib.parse import quote, unquote, urlsplit

ROOT = Path(__file__).resolve().parent.parent
REPO = 'https://github.com/minjae9010/MagicRecoil'
TOC = re.compile(r'<!-- toc:start -->\n.*?<!-- toc:end -->\n*', re.S)
LINK = re.compile(r'(!?\[[^\]\n]+\]\()([^\)\n]+)(\))')
PAGES = [
    ('README', 'Home', '문서 안내', 'Documentation'),
    ('getting-started', 'Getting-Started', '설치와 첫 발사', 'Getting started'),
    ('compatibility', 'Compatibility', '버전 호환 안내', 'Version compatibility'),
    ('recoil-logic', 'Recoil-Logic', '반동 계산과 수평 규칙', 'Recoil logic'),
    ('configuration', 'Configuration', '설정 전체 표', 'Configuration'),
    ('integration', 'Integration', '기존 총기 연결', 'Gun integration'),
    ('hit-detection', 'Hit-Detection', '명중 판정과 적중 스펠', 'Hit detection and callbacks'),
    ('aim-and-input', 'Aiming', '조준과 입력 한계', 'Aiming and input limits'),
    ('input-comparison', 'Input-Comparison', '기존 총기 구현 비교', 'Existing gun plugin comparison'),
    ('troubleshooting', 'Troubleshooting', '증상별 문제 해결', 'Troubleshooting'),
    ('testing', 'Testing', '검증 결과와 테스트', 'Testing'),
    ('architecture', 'Architecture', '내부 구조와 패킷', 'Architecture'),
    ('development', 'Development', '개발과 배포', 'Development'),
]


def headings(text):
    result, seen, fence = [], {}, None
    for line in TOC.sub('', text).splitlines():
        marker = re.match(r'^\s*(`{3,}|~{3,})', line)
        if marker:
            if fence is None:
                fence = (marker[1][0], len(marker[1]))
            elif marker[1][0] == fence[0] and len(marker[1]) >= fence[1]:
                fence = None
            continue
        match = re.match(r'^(#{1,6})\s+(.+?)\s*#*\s*$', line)
        if fence or not match:
            continue
        label = match[2]
        plain = html.unescape(re.sub(r'<[^>]*>', '', label)).replace('`', '').lower()
        plain = re.sub(r'\[([^]]+)\]\([^)]*\)', r'\1', plain)
        slug = ''.join(c for c in plain if c.isspace() or c in '-_' or unicodedata.category(c)[0] in 'LNM')
        slug = re.sub(r'\s', '-', slug)
        index = seen.get(slug, 0)
        seen[slug] = index + 1
        result.append((len(match[1]), label, slug + (f'-{index}' if index else '')))
    return result


def with_toc(text, korean):
    text = TOC.sub('', text)
    entries = [(n, title, slug) for n, title, slug in headings(text) if 2 <= n <= 3]
    if not entries:
        return text
    title = '목차' if korean else 'On this page'
    block = f'<!-- toc:start -->\n## {title}\n\n'
    block += '\n'.join('  ' * (n - 2) + f'- [{title}](#{slug})' for n, title, slug in entries)
    block += '\n<!-- toc:end -->\n\n'
    start = re.search(r'^## ', text, re.M).start()
    return text[:start] + block + text[start:]


def validate(paths, wiki=False):
    errors = []
    for path in paths:
        for match in LINK.finditer(path.read_text(encoding='utf-8')):
            target = match[2].strip('<>')
            if urlsplit(target).scheme or target.startswith('//'):
                continue
            destination, _, fragment = target.partition('#')
            resolved = (path.parent / unquote(destination)).resolve() if destination else path
            if wiki and destination:
                resolved = resolved.with_suffix('.md')
            if not resolved.exists():
                errors.append(f'{path}: missing {target}')
            elif fragment and resolved.suffix == '.md':
                anchors = {slug for _, _, slug in headings(resolved.read_text(encoding='utf-8'))}
                if unquote(fragment) not in anchors:
                    errors.append(f'{path}: missing anchor {target}')
    if errors:
        raise SystemExit('\n'.join(errors))


def export_wiki(output):
    mapping = {ROOT / 'README.md': 'EN-Overview', ROOT / 'README.ko.md': 'KO-Overview'}
    for lang in ('ko', 'en'):
        for source, name, _, _ in PAGES:
            mapping[ROOT / f'docs/{lang}/{source}.md'] = f'{lang.upper()}-{name}'
    output.mkdir(parents=True, exist_ok=True)
    for source, name in mapping.items():
        def replace(match):
            target = match[2]
            if urlsplit(target).scheme or target.startswith(('#', '//')):
                return match[0]
            destination, separator, fragment = target.partition('#')
            resolved = (source.parent / unquote(destination)).resolve()
            if resolved in mapping:
                converted = mapping[resolved]
            else:
                relative = quote(resolved.relative_to(ROOT).as_posix())
                base = 'https://raw.githubusercontent.com/minjae9010/MagicRecoil/main/' if match[1].startswith('![') else REPO + '/blob/main/'
                converted = base + relative
            return match[1] + converted + (separator + fragment if separator else '') + match[3]
        text = LINK.sub(replace, source.read_text(encoding='utf-8'))
        (output / f'{name}.md').write_text(text, encoding='utf-8', newline='\n')
    home = '''# MagicRecoil Wiki

Source build v0.3.0-alpha.2 · Paper 1.21.4+ · Java 21+ · MagicSpells 4.0-Beta-17+

기존 총기에 시점 반동을 연결하는 안내입니다. **설치 → 반동 규칙 → 총기 연결 → 실제 테스트** 순서로 읽으세요.

Relative camera recoil for existing MagicSpells guns. Follow **installation → recoil rules → integration → acceptance testing**.

이번 릴리스는 BeamSpell에서 헤드샷·방향·거리 피해를 지원합니다. HitSpell에는 데이터 팩으로 등록한 피해 유형을 지정할 수 있습니다.

This release connects BeamSpell to headshots, directional damage, and distance falloff. HitSpell can use damage types registered by a data pack.

## 바로 시작 / Start here

- **처음 설치:** [설치와 첫 발사](KO-Getting-Started)
- **수평이 안 움직임:** [수평 반동 진단](KO-Troubleshooting#수평-반동이-없음)
- **반동 숫자 이해:** [계산식과 패턴](KO-Recoil-Logic)
- **우클릭 연사·좌클릭 줌:** [설정과 파이먼 연결](KO-Aiming#우클릭-자동-사격)
- **Right-click fire / left-click zoom:** [Controls and setup](EN-Aiming#right-click-automatic-fire)
- **English:** [Documentation home](EN-Home)

## 문서 지도 / Documentation map

| 목적 / Goal | 한국어 | English |
| --- | --- | --- |
'''
    for _, name, ko, en in PAGES:
        home += f'| {ko} / {en} | [{ko}](KO-{name}) | [{en}](EN-{name}) |\n'
    home += f'''

## 사용 전에 / Before deployment

실험 버전입니다. 서버 패킷만으로 프레임 단위 카메라 보간을 보장하지 않습니다. 탄약·재장전 조건은 기존 총기에서 유지하고 [검증 결과와 체크리스트](KO-Testing)를 확인하세요.

Experimental: server packets do not guarantee frame-level camera interpolation. Preserve ammunition/reload checks and complete [acceptance testing](EN-Testing).

[프로젝트 소개](KO-Overview) · [Project overview](EN-Overview) · [GitHub repository]({REPO}) · [Releases]({REPO}/releases)
'''
    (output / 'Home.md').write_text(with_toc(home, True), encoding='utf-8', newline='\n')
    sidebar = '[**MagicRecoil Wiki**](Home)\n\n### 한국어\n\n'
    sidebar += '\n'.join(f'- [{ko}](KO-{name})' for _, name, ko, _ in PAGES)
    sidebar += '\n\n### English\n\n'
    sidebar += '\n'.join(f'- [{en}](EN-{name})' for _, name, _, en in PAGES)
    sidebar += f'\n\n[GitHub]({REPO}) · [Releases]({REPO}/releases)\n'
    (output / '_Sidebar.md').write_text(sidebar, encoding='utf-8', newline='\n')
    footer = f'[Wiki Home](Home) · [한국어](KO-Home) · [English](EN-Home) · [Source]({REPO})\n\nPaper 1.21.4+ / MagicSpells 4.0-Beta-17+ · Experimental / 실험 버전\n'
    (output / '_Footer.md').write_text(footer, encoding='utf-8', newline='\n')
    validate(list(output.glob('*.md')), wiki=True)
    print(f'PASS: {len(mapping) + 3} exported Wiki pages; page links and heading anchors verified.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Validate without rewriting tables of contents')
    parser.add_argument('--export-wiki', type=Path, help='Export bilingual pages, Home, sidebar and footer')
    args = parser.parse_args()
    paths = [ROOT / 'README.md', ROOT / 'README.ko.md', *sorted((ROOT / 'docs').glob('**/*.md')),
             ROOT / 'CONTRIBUTING.md', ROOT / 'tests/runtime/README.md']
    for path in paths:
        text = path.read_text(encoding='utf-8')
        expected = with_toc(text, 'ko' in path.parts or path.name == 'README.ko.md')
        if args.check and expected != text:
            raise SystemExit(f'Outdated TOC: {path.relative_to(ROOT)}; run python scripts/docs.py')
        if not args.check:
            path.write_text(expected, encoding='utf-8', newline='\n')
    validate(paths)
    print(f'PASS: {len(paths)} documents; tables of contents, local links, and heading anchors verified.')
    if args.export_wiki:
        export_wiki(args.export_wiki.resolve())


if __name__ == '__main__':
    main()
