#!/usr/bin/env python3
"""Compare Leaf config option paths between a git ref and the working tree (read-only).

Usage (from the repository root):
    python3 plans/leaf-config-refactor/tools/config_paths.py [--base REF]

REF defaults to ver/26.2. Use a commit taken before a phase (e.g. HEAD) to check
that a rewrite phase did not change user-facing paths, scopes, types or defaults.

Parses both module styles:
  - annotation style: @ConfigClassInfo(category, name) + @ConfigInfo(name) fields;
    classes implementing WorldConfigModule are reported as WORLD (leaf-world-defaults.yml);
  - legacy style: basePath()/getBasePath() + globalConfig.getXxx(basePath() + "...", default).
Keys read manually (e.g. ThrottleNaturalMobSpawning dynamic keys) are not covered.
Defaults are compared as source text and only when both sides use annotation style.
"""
import argparse
import re
import subprocess
from pathlib import Path

ROOT = 'leaf-server/src/main/java/org/dreeam/leaf/config/'
MODULES = ROOT + 'modules/'


def git(*args):
    return subprocess.run(['git', *args], capture_output=True, text=True, check=True).stdout


def read_ref(ref):
    names = [n for n in git('ls-tree', '-r', '--name-only', ref, MODULES).split() if n.endswith('.java')]
    return {n: git('show', f'{ref}:{n}') for n in names}, git('show', f'{ref}:{ROOT}ConfigCategory.java')


def read_worktree():
    files = {str(p): p.read_text(encoding='utf-8') for p in Path(MODULES).rglob('*.java')}
    return files, Path(ROOT + 'ConfigCategory.java').read_text(encoding='utf-8')


NEW_CLASS = re.compile(r'@ConfigClassInfo\(\s*category\s*=\s*ConfigCategory\.(\w+)(?:\s*,\s*name\s*=\s*"([^"]*)")?')
NEW_FIELD = re.compile(
    r'@ConfigInfo\(\s*name\s*=\s*"([^"]+)"(?:[^@]|@(?!ConfigInfo))*?'
    r'(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?(?:@\w+\s+)*([\w.<>]+)\s+(\w+)\s*=\s*([^;]+);',
    re.S)
OLD_BASE = re.compile(
    r'(?:basePath|getBasePath)\(\)\s*\{\s*return\s+ConfigCategory\.(\w+)\.(?:basePath|getBaseKeyName)\(\)\s*(?:\+\s*"([^"]*)")?\s*;')
OLD_READ = re.compile(
    r'\.get(Boolean|Int|Integer|Long|Double|String|List)\(\s*(?:\w+\s*=\s*)?(?:basePath|getBasePath)\(\)\s*(?:\+\s*"([^"]*)")?\s*,\s*([^,;]+?)\s*[,)]')
OLD_LOCAL = re.compile(r'String\s+(\w+)\s*=\s*(?:basePath|getBasePath)\(\)\s*\+\s*"([^"]*)"\s*;')


def resolve(src, expression):
    """Legacy reads pass the field as default; resolve it to the field initializer."""
    expression = expression.strip()
    if re.fullmatch(r'\w+', expression):
        init = re.search(r'\b' + expression + r'\s*=\s*([^;]+);', src)
        if init and not re.search(r'\b' + expression + r'\s*=\s*\w+\.get', src[:init.start() + 1]):
            return ' '.join(init.group(1).split())
    return ' '.join(expression.split())


def normalize(value):
    value = re.sub(r'(?<=\d)[dDfFlL]\b', '', value.strip())
    return re.sub(r'\.0\b', '', value)


def parse(files, category_source):
    categories = dict(re.findall(r'(\w+)\("([^"]+)"\)', category_source))
    options, unparsed = {}, []
    for name, src in files.items():
        short = name.split('modules/')[1]
        cls = NEW_CLASS.search(src)
        if cls:
            base = categories[cls.group(1)] + ('.' + cls.group(2) if cls.group(2) else '')
            scope = 'WORLD' if 'WorldConfigModule' in src else 'GLOBAL'
            for field in NEW_FIELD.finditer(src):
                options[base + '.' + field.group(1)] = (short, scope, field.group(2), ' '.join(field.group(4).split()), True)
            continue
        old = OLD_BASE.search(src)
        if old:
            base = categories.get(old.group(1), old.group(1)) + (old.group(2) or '')
            for read in OLD_READ.finditer(src):
                options[base + (read.group(2) or '')] = (short, 'GLOBAL', read.group(1), resolve(src, read.group(3)), False)
            for var, suffix in OLD_LOCAL.findall(src):
                local_read = re.compile(
                    r'\.get(Boolean|Int|Integer|Long|Double|String|List)\(\s*' + var
                    + r'\s*(?:\+\s*"([^"]*)")?\s*,\s*([^,;]+?)\s*[,)]')
                for read in local_read.finditer(src):
                    options[base + suffix + (read.group(2) or '')] = (short, 'GLOBAL', read.group(1), resolve(src, read.group(3)), False)
        elif re.search(r'(?:implements|extends)\s+(?:\w+\s*,\s*)*(?:ConfigModule|WorldConfigModule)', src):
            unparsed.append(short)
    return options, unparsed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default='ver/26.2')
    args = parser.parse_args()
    base, base_unparsed = parse(*read_ref(args.base))
    work, work_unparsed = parse(*read_worktree())

    def section(title, rows):
        print(f'== {title} ({len(rows)})')
        for row in rows:
            print('  ' + row)

    section(f'only in {args.base}', [f'{k}  {base[k][0]}' for k in sorted(base.keys() - work.keys())])
    section('only in working tree', [f'{k}  {work[k][0]} [{work[k][1]}]' for k in sorted(work.keys() - base.keys())])
    section('scope changed', [f'{k}  {base[k][1]} -> {work[k][1]}' for k in sorted(base.keys() & work.keys()) if base[k][1] != work[k][1]])
    section('type changed (annotation style on both sides)', [
        f'{k}  {base[k][2]} -> {work[k][2]}'
        for k in sorted(base.keys() & work.keys())
        if base[k][4] and work[k][4] and base[k][2] != work[k][2]])
    section('default changed (source text; legacy defaults resolved from field initializers, verify manually)', [
        f'{k}  {base[k][3]!r} -> {work[k][3]!r}'
        for k in sorted(base.keys() & work.keys())
        if normalize(base[k][3]) != normalize(work[k][3])])
    section(f'unparsed modules in {args.base} (check manually)', sorted(base_unparsed))
    section('unparsed modules in working tree (check manually)', sorted(work_unparsed))


if __name__ == '__main__':
    main()
