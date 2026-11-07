# -*- coding: utf-8 -*-
"""按提交要求组织并打包：报告 + 演示 + 源代码 + 说明。

排除编译产物 out/ 与 .idea/workspace.xml（本地状态）。
"""
import os
import shutil
import sys
import zipfile

STAGE = '_submit'
NAME = '项目一-NPuzzle-提交件'
OUT = NAME + '.zip'

FILES = [
    ('N-Puzzle问题求解研究报告.docx', '01-研究报告/N-Puzzle问题求解研究报告.docx'),
    ('N-Puzzle项目演示.pptx', '02-项目演示/N-Puzzle项目演示.pptx'),
    ('N-Puzzle项目演示.pdf', '02-项目演示/N-Puzzle项目演示.pdf'),
    ('运行说明.md', '03-源代码/运行说明.md'),
    ('提交清单.md', '提交清单.md'),
]

DIRS = [
    ('WattAi/src', '03-源代码/WattAi/src'),
    ('WattAi/resources', '03-源代码/WattAi/resources'),
]

SINGLE = [
    ('WattAi/WattAi.iml', '03-源代码/WattAi/WattAi.iml'),
]


def main():
    for f, _ in FILES + SINGLE:
        if not os.path.exists(f):
            print('!! 缺少文件:', f)
            return 1

    if os.path.isdir(STAGE):
        shutil.rmtree(STAGE)
    base = os.path.join(STAGE, NAME)
    os.makedirs(base)

    for src, rel in FILES + SINGLE:
        dst = os.path.join(base, rel)
        os.makedirs(os.path.dirname(dst) or '.', exist_ok=True)
        shutil.copy2(src, dst)

    for src, rel in DIRS:
        dst = os.path.join(base, rel)
        os.makedirs(os.path.dirname(dst) or '.', exist_ok=True)
        shutil.copytree(src, dst, ignore=shutil.ignore_patterns('out', '*.class', '.DS_Store'))

    # .idea 里只留工程骨架，workspace.xml 是本地窗口状态，不带走
    src_idea = 'WattAi/.idea'
    dst_idea = os.path.join(base, '03-源代码/WattAi/.idea')
    if os.path.isdir(src_idea):
        os.makedirs(dst_idea, exist_ok=True)
        for f in sorted(os.listdir(src_idea)):
            if f == 'workspace.xml':
                continue
            p = os.path.join(src_idea, f)
            if os.path.isfile(p):
                shutil.copy2(p, os.path.join(dst_idea, f))

    if os.path.exists(OUT):
        os.remove(OUT)
    n = 0
    with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for dirpath, _, files in os.walk(base):
            for f in sorted(files):
                full = os.path.join(dirpath, f)
                arc = os.path.relpath(full, STAGE).replace(os.sep, '/')
                z.write(full, arc)
                n += 1

    size = os.path.getsize(OUT)
    print('打包完成: %s' % OUT)
    print('条目 %d 个, 压缩后 %.1f MB' % (n, size / 1024 / 1024))

    print('\n--- 包内结构 ---')
    for dirpath, dirnames, files in os.walk(base):
        dirnames.sort()
        rel = os.path.relpath(dirpath, STAGE)
        depth = 0 if rel == NAME else rel.count(os.sep) + 1
        if rel != NAME:
            print('  ' * depth + os.path.basename(dirpath) + '/')
        for f in sorted(files):
            full = os.path.join(dirpath, f)
            print('  ' * (depth + 1) + '%-56s %7.1f KB' % (f, os.path.getsize(full) / 1024))
    return 0


if __name__ == '__main__':
    sys.exit(main())
