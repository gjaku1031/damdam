#!/usr/bin/env python3
"""Java 주석 컨벤션(docs/convention/COMMENTS.md) 기계 검사.

검사 항목
- 타입·enum 항목·필드·메서드·생성자 앞의 여러 줄 Javadoc 존재
- 한 줄 Javadoc 금지, 여는 /** 다음 빈 줄 금지
- 앞선 선언과 Javadoc 사이 빈 줄 한 줄(여는 괄호 바로 뒤 제외), Javadoc과 선언 사이 빈 줄 금지
- record 구성요소마다 타입 Javadoc의 @param

사용: python3 scripts/check-javadoc.py [소스 디렉터리...]
위반이 있으면 목록을 출력하고 종료 코드 1
"""
import pathlib
import re
import sys

DEFAULT_ROOTS = ['apps/api/src/main/java', 'apps/api/src/test/java']
TYPE_DECL = re.compile(r'^\s*(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed|strictfp)\s+)*'
                       r'(class|interface|record|enum|@interface)\s+(\w+)')
MODIFIERS = r'(?:(?:public|protected|private|static|final|abstract|default|synchronized|native|transient|volatile)\s+)*'
METHOD_DECL = re.compile(r'^\s*' + MODIFIERS + r'(?:<[^>]+>\s+)?(?:[\w.$]+(?:<[^;=]*>)?(?:\[\])*\s+)?(\w+)\s*\(')
FIELD_DECL = re.compile(r'^\s*' + MODIFIERS + r'[\w.$]+(?:<.*>)?(?:\[\])*\s+(\w+)\s*(=.*)?;?\s*$')
ENUM_CONSTANT = re.compile(r'^\s*([A-Z][A-Z0-9_]*)\s*(\(.*\))?\s*[,;]?\s*(\{)?\s*$')
KEYWORDS = {'if', 'for', 'while', 'switch', 'catch', 'return', 'new', 'throw', 'else', 'try', 'synchronized', 'super',
            'this', 'yield'}


def strip_strings(line):
    """문자열·문자 리터럴과 줄 주석을 지워 괄호 계산에서 제외."""
    line = re.sub(r'"(?:\\.|[^"\\])*"', '""', line)
    line = re.sub(r"'(?:\\.|[^'\\])'", "''", line)
    return line.split('//', 1)[0]


def javadoc_before(lines, index):
    """선언 위의 어노테이션(여러 줄 인자 포함)을 건너뛴 Javadoc 블록(시작·끝 줄 번호) 또는 None."""
    i = index - 1
    # 어노테이션과 그 연속 줄은 빈 줄·문장 끝·블록 경계 없이 이어짐
    while i >= 0 and lines[i].strip() and not lines[i].strip().endswith(('*/', ';', '{', '}')):
        i -= 1
    if i < 0 or not lines[i].strip().endswith('*/'):
        return None
    end = i
    while i >= 0 and not lines[i].strip().startswith('/**'):
        if lines[i].strip().startswith('/*') and not lines[i].strip().startswith('/**'):
            return None
        i -= 1
    return (i, end) if i >= 0 else None


def record_components(lines, index):
    """record 헤더의 구성요소 이름 목록."""
    text = ''
    for line in lines[index:index + 40]:
        text += ' ' + strip_strings(line)
        if re.search(r'\)\s*(implements[^{]*)?\{', text):
            break
    match = re.search(r'record\s+\w+\s*(?:<[^>]*>)?\s*\((.*?)\)\s*(?:implements[^{]*)?\{', text)
    if not match or not match.group(1).strip():
        return []
    params, depth, current = [], 0, ''
    for ch in match.group(1):
        if ch in '<(':
            depth += 1
        elif ch in '>)':
            depth -= 1
        if ch == ',' and depth == 0:
            params.append(current)
            current = ''
        else:
            current += ch
    params.append(current)
    return [re.findall(r'(\w+)\s*$', p.strip())[0] for p in params if re.findall(r'(\w+)\s*$', p.strip())]


def check_file(path):
    lines = path.read_text(encoding='utf-8').splitlines()
    problems = []
    stack = []  # 블록 종류: 'type', 'enum', 'code'
    pending_kind = None
    in_block_comment = in_text_block = False
    enum_constants_open = False

    def report(number, message):
        problems.append(f'{path}:{number + 1}: {message}')

    def require_doc(index, what):
        block = javadoc_before(lines, index)
        if block is None:
            report(index, f'{what} Javadoc 없음')
            return None
        start, end = block
        if start == end:
            report(start, '한 줄 Javadoc 금지')
        elif lines[start].strip() != '/**' or lines[end].strip() != '*/':
            report(start, '/** 와 */ 는 각각 독립된 줄')
        elif start + 1 < end and lines[start + 1].strip() == '*':
            report(start, '/** 다음 빈 줄 금지')
        previous = lines[start - 1].strip() if start > 0 else ''
        if start > 1 and not previous and lines[start - 2].strip().endswith(('{', '(')):
            report(start, '여는 괄호 바로 뒤 첫 Javadoc 앞 빈 줄 금지')
        if start > 0 and previous and not previous.endswith(('{', '(')) and not previous.startswith('package') \
                and not previous.startswith('import'):
            report(start, 'Javadoc 앞에 빈 줄 한 줄 필요')
        between = lines[end + 1:index]
        if any(not line.strip() for line in between):
            report(end, 'Javadoc과 선언 사이 빈 줄 금지')
        return lines[start:end + 1]

    for index, raw in enumerate(lines):
        stripped = raw.strip()
        # 블록 주석·텍스트 블록 건너뛰기
        if in_text_block:
            if '"""' in raw:
                in_text_block = False
            continue
        if in_block_comment:
            if '*/' in raw:
                in_block_comment = False
            continue
        if stripped.startswith('/*'):
            if '*/' not in stripped[2:]:
                in_block_comment = True
            continue
        if raw.count('"""') % 2 == 1:
            in_text_block = True
        line = strip_strings(raw)
        top = stack[-1] if stack else None

        # 선언 검사: 타입 본문 바로 안쪽에서만
        if top in ('type', 'enum') or top is None:
            type_match = TYPE_DECL.match(line)
            if stripped and not stripped.startswith('@') and not stripped.startswith(('package', 'import')):
                if type_match:
                    doc = require_doc(index, f'{type_match.group(1)} {type_match.group(2)}')
                    if type_match.group(1) == 'record' and doc is not None:
                        documented = set(re.findall(r'@param\s+(\w+)', '\n'.join(doc)))
                        for name in record_components(lines, index):
                            if name not in documented:
                                report(index, f'record 구성요소 {name} @param 없음')
                    pending_kind = 'enum' if type_match.group(1) == 'enum' else 'type'
                elif top == 'enum' and enum_constants_open and ENUM_CONSTANT.match(line):
                    require_doc(index, f'enum 항목 {ENUM_CONSTANT.match(line).group(1)}')
                    if line.rstrip().endswith(';'):
                        enum_constants_open = False
                elif top in ('type', 'enum') and (raw.startswith('    ') and not raw.startswith(' ' * (4 * len(stack) + 1))):
                    method = METHOD_DECL.match(line)
                    field = FIELD_DECL.match(line)
                    if method and method.group(1) not in KEYWORDS:
                        require_doc(index, f'메서드 {method.group(1)}')
                    elif field and not line.strip().startswith(('return', 'throw')):
                        require_doc(index, f'필드 {field.group(1)}')
            if top == 'enum' and enum_constants_open and stripped.endswith(';'):
                enum_constants_open = False

        # 괄호로 블록 깊이 추적
        for ch in line:
            if ch == '{':
                if pending_kind:
                    stack.append(pending_kind)
                    if pending_kind == 'enum':
                        enum_constants_open = True
                    pending_kind = None
                else:
                    stack.append('code')
            elif ch == '}':
                if stack:
                    stack.pop()
    return problems


def main():
    roots = sys.argv[1:] or DEFAULT_ROOTS
    problems = []
    for root in roots:
        base = pathlib.Path(root)
        for path in [base] if base.is_file() else sorted(base.rglob('*.java')):
            if 'generated' in path.parts:
                continue
            problems.extend(check_file(path))
    print('\n'.join(problems))
    print(f'Javadoc 위반 {len(problems)}건', file=sys.stderr)
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
