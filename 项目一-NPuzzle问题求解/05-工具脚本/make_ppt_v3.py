# -*- coding: utf-8 -*-
"""《N-Puzzle 问题求解》四人分工演示（12 页 / 300 秒）。

分工：周以撒（数据结构 + 基础启发函数）→ 李行（A*/IDA*）→ 林子航（模式数据库）→ 张文璟（可视化 + 结论）

运行：py -3 make_ppt_v3.py [输出文件名]
"""
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from pptx.oxml.ns import qn
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BG_LIGHT = os.path.join(HERE, 'assets', 'bg_light.png')
BG_DARK = os.path.join(HERE, 'assets', 'bg_dark2.png')

YH = '\u5fae\u8f6f\u96c5\u9ed1'

# ---- 配色 ----
INK    = RGBColor(0x0B, 0x1B, 0x33)
BLUE   = RGBColor(0x25, 0x63, 0xEB)
BLUE_D = RGBColor(0x1D, 0x4E, 0xDB)
CYAN   = RGBColor(0x08, 0x91, 0xB2)
TEAL   = RGBColor(0x0D, 0x94, 0x88)
PURPLE = RGBColor(0x7C, 0x3A, 0xED)
AMBER  = RGBColor(0xD9, 0x77, 0x06)
RED    = RGBColor(0xDC, 0x26, 0x26)
GREEN  = RGBColor(0x16, 0xA3, 0x4A)
MUTED  = RGBColor(0x64, 0x74, 0x8B)
LINE   = RGBColor(0xDF, 0xE6, 0xF0)
SURF   = RGBColor(0xF7, 0xFA, 0xFD)
WHITE  = RGBColor(0xFF, 0xFF, 0xFF)
GHOST  = RGBColor(0xE6, 0xEC, 0xF6)
SKY    = RGBColor(0x9C, 0xC4, 0xF0)
DIM    = RGBColor(0x6C, 0x86, 0xA8)

SW, SH = 13.333, 7.5
M = 0.72
CW = SW - 2 * M
NO_STYLE = '{2D5ABB26-0587-4C30-8999-92F81FD0307C}'


def set_run(run, text, size=12, bold=False, color=INK, font=YH, italic=False, spc=None):
    run.text = text
    f = run.font
    f.size = Pt(size)
    f.bold = bold
    f.italic = italic
    if color is not None:
        f.color.rgb = color
    f.name = font
    rPr = run._r.get_or_add_rPr()
    if spc:
        rPr.set('spc', str(spc))
    prev = rPr.find(qn('a:latin'))
    for tag in ('a:ea', 'a:cs'):
        el = rPr.find(qn(tag))
        if el is None:
            el = rPr.makeelement(qn(tag), {})
            prev.addnext(el)
        el.set('typeface', font)
        prev = el


def para(tf, first, text, size=12, bold=False, color=INK, font=YH,
         before=0, after=0, align=PP_ALIGN.LEFT, line=1.15, spc=None):
    p = tf.paragraphs[0] if first else tf.add_paragraph()
    p.alignment = align
    p.space_before = Pt(before)
    p.space_after = Pt(after)
    p.line_spacing = line
    set_run(p.add_run(), text, size=size, bold=bold, color=color, font=font, spc=spc)
    return p


def textbox(slide, l, t, w, h, anchor=MSO_ANCHOR.TOP):
    tb = slide.shapes.add_textbox(Inches(l), Inches(t), Inches(w), Inches(h))
    tf = tb.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    return tf


def add_rect(slide, l, t, w, h, fill=None, line=None, shape=MSO_SHAPE.RECTANGLE,
             radius=None, line_w=1.0):
    sh = slide.shapes.add_shape(shape, Inches(l), Inches(t), Inches(w), Inches(h))
    if fill is None:
        sh.fill.background()
    else:
        sh.fill.solid()
        sh.fill.fore_color.rgb = fill
    if line is None:
        sh.line.fill.background()
    else:
        sh.line.color.rgb = line
        sh.line.width = Pt(line_w)
    sh.shadow.inherit = False
    if radius is not None:
        try:
            sh.adjustments[0] = radius
        except Exception:
            pass
    sh.text_frame.word_wrap = True
    return sh


def shape_text(sh, text, size=12, bold=False, color=INK, font=YH,
               align=PP_ALIGN.CENTER, anchor=MSO_ANCHOR.MIDDLE, pad=0.06):
    tf = sh.text_frame
    tf.margin_left = tf.margin_right = Inches(pad)
    tf.margin_top = tf.margin_bottom = 0
    tf.vertical_anchor = anchor
    para(tf, True, text, size=size, bold=bold, color=color, font=font, align=align)
    return tf


def chip(slide, l, t, text, color=BLUE, size=10.5, h=0.28, pad=0.24):
    w = len(text) * size * 0.0142 + pad
    sh = add_rect(slide, l, t, w, h, fill=WHITE, line=color, line_w=1.0,
                  shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.5)
    shape_text(sh, text, size=size, bold=True, color=color, pad=0.04)
    return sh


PAGE = {'n': 1}


def content(prs, title, who, accent=BLUE):
    s = prs.slides.add_slide(prs.slide_layouts[6])
    s.shapes.add_picture(BG_LIGHT, 0, 0, width=prs.slide_width, height=prs.slide_height)
    add_rect(s, 0, 0, SW, 0.07, fill=accent)
    add_rect(s, M, 0.66, 0.055, 0.50, fill=accent)
    tf = textbox(s, M + 0.24, 0.60, 9.3, 0.62, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, title, size=26, bold=True, color=INK)
    PAGE['n'] += 1
    if who:
        tf = textbox(s, 8.55, 0.40, 2.05, 0.26, anchor=MSO_ANCHOR.MIDDLE)
        para(tf, True, who, size=11, bold=True, color=accent, align=PP_ALIGN.RIGHT)
    tf = textbox(s, 10.75, 0.28, 1.86, 0.90, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, '%02d' % PAGE['n'], size=42, bold=True, color=GHOST, align=PP_ALIGN.RIGHT)
    add_rect(s, M, 1.28, CW, 0.010, fill=LINE)
    tf = textbox(s, M, 6.98, 7.0, 0.26, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, 'N-Puzzle 问题求解　·　SearchFramework 2.1', size=9, color=MUTED)
    return s


def notes(slide, secs, text):
    slide.notes_slide.notes_text_frame.text = '【约 %d 秒】%s' % (secs, text)


def kpi(slide, l, t, w, h, value, label, accent=BLUE, vsize=30, lsize=10):
    add_rect(slide, l, t, w, h, fill=WHITE, line=LINE, line_w=1.0,
             shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.08)
    add_rect(slide, l, t, w, 0.05, fill=accent)
    tf = textbox(slide, l + 0.24, t + 0.22, w - 0.44, h - 0.32)
    para(tf, True, value, size=vsize, bold=True, color=accent, line=1.0)
    para(tf, False, label, size=lsize, color=MUTED, before=5, line=1.15)


def kpi_row(slide, items, t, h=1.10, gap=0.26):
    n = len(items)
    w = (CW - gap * (n - 1)) / n
    for i, it in enumerate(items):
        kpi(slide, M + i * (w + gap), t, w, h, it[0], it[1],
            it[2] if len(it) > 2 else BLUE)


def card(slide, l, t, w, h, title, lines, accent=BLUE, tsize=13.5, bsize=10.5,
         gap=5, title_color=INK):
    add_rect(slide, l, t, w, h, fill=WHITE, line=LINE, line_w=1.0,
             shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.06)
    add_rect(slide, l, t, 0.05, h, fill=accent)
    tf = textbox(slide, l + 0.26, t + 0.18, w - 0.46, h - 0.30)
    para(tf, True, title, size=tsize, bold=True, color=title_color, line=1.05)
    for ln in lines:
        para(tf, False, ln, size=bsize, color=INK, before=gap, line=1.15)


def band(slide, t, text, h=0.72, accent=PURPLE, fill=None, tag=None, size=11):
    """底部一条强调带。tag 为 None 时不画标签块。"""
    fill = fill if fill is not None else WHITE
    add_rect(slide, M, t, CW, h, fill=fill, line=LINE, line_w=1.0,
             shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.07)
    add_rect(slide, M, t + 0.09, 0.05, h - 0.18, fill=accent)
    left = M + 0.26
    if tag:
        tg = add_rect(slide, M + 0.24, t + (h - 0.28) / 2, 0.62, 0.28, fill=accent,
                      shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.4)
        shape_text(tg, tag, size=10, bold=True, color=WHITE, pad=0.04)
        left = M + 0.98
    tf = textbox(slide, left, t, SW - left - M - 0.20, h, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, text, size=size, color=INK, line=1.2)


def add_table(slide, data, left, top, col_w, row_h=0.44, head_h=0.42, size=11,
              head_size=10.5, highlight=(), align_first=PP_ALIGN.LEFT):
    rows, cols = len(data), len(data[0])
    gf = slide.shapes.add_table(rows, cols, Inches(left), Inches(top),
                                Inches(sum(col_w)), Inches(head_h + row_h * (rows - 1)))
    tbl = gf.table
    tblPr = tbl._tbl.find(qn('a:tblPr'))
    for el in tblPr.findall(qn('a:tableStyleId')):
        tblPr.remove(el)
    el = tblPr.makeelement(qn('a:tableStyleId'), {})
    el.text = NO_STYLE
    tblPr.append(el)
    tblPr.set('firstRow', '0')
    tblPr.set('bandRow', '0')
    for j, w in enumerate(col_w):
        tbl.columns[j].width = Inches(w)
    tbl.rows[0].height = Inches(head_h)
    for i in range(1, rows):
        tbl.rows[i].height = Inches(row_h)
    for i, row in enumerate(data):
        for j, raw in enumerate(row):
            color, bold = None, False
            if isinstance(raw, tuple):
                if len(raw) == 2:
                    text, color = raw
                else:
                    text, color, bold = raw
            else:
                text = raw
            cell = tbl.cell(i, j)
            cell.margin_left = Inches(0.12)
            cell.margin_right = Inches(0.08)
            cell.margin_top = Inches(0.02)
            cell.margin_bottom = Inches(0.02)
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
            cell.fill.solid()
            if i == 0:
                cell.fill.fore_color.rgb = INK
            else:
                cell.fill.fore_color.rgb = WHITE if i % 2 else SURF
            tf = cell.text_frame
            tf.word_wrap = True
            p = tf.paragraphs[0]
            p.alignment = align_first if j == 0 else PP_ALIGN.CENTER
            p.line_spacing = 1.0
            if i == 0:
                set_run(p.add_run(), text, size=head_size, bold=True, color=WHITE)
            else:
                base = INK if j == 0 else RGBColor(0x33, 0x41, 0x55)
                set_run(p.add_run(), text, size=size, bold=bold,
                        color=color if color is not None else base)
    return tbl


def board(slide, left, top, size, cells, n=4, fsize=14):
    goal = list(range(1, n * n)) + [0]
    cw = size / n
    for i, v in enumerate(cells):
        r, c = divmod(i, n)
        l = left + c * cw + cw * 0.06
        t = top + r * cw + cw * 0.06
        sz = cw * 0.88
        if v == 0:
            fill = RGBColor(0x1B, 0x2A, 0x40)
        elif v == goal[i]:
            fill = GREEN
        else:
            fill = RGBColor(0x4A, 0x7C, 0xD8)
        sh = add_rect(slide, l, t, sz, sz, fill=fill,
                      shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.14)
        shape_text(sh, '' if v == 0 else str(v), size=fsize, bold=True, color=WHITE)


# ================================================================
prs = Presentation()
prs.slide_width = Inches(SW)
prs.slide_height = Inches(SH)

# ===== 01 封面 =====
GOAL_CELLS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0]
START_CELLS = [15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 1, 2, 0]

s = prs.slides.add_slide(prs.slide_layouts[6])
s.shapes.add_picture(BG_DARK, 0, 0, width=prs.slide_width, height=prs.slide_height)
board(s, 9.55, 1.62, 3.00, [7, 2, 4, 5, 0, 6, 3, 8, 9, 1, 11, 12, 13, 10, 14, 15], fsize=14)
tf = textbox(s, 1.00, 1.74, 8.3, 1.10)
para(tf, True, 'N-Puzzle 问题求解', size=52, bold=True, color=WHITE, line=1.0)
add_rect(s, 1.05, 3.02, 1.05, 0.055, fill=RGBColor(0x22, 0xD3, 0xEE))
tf = textbox(s, 1.00, 3.30, 7.9, 0.8)
para(tf, True, '从跑通到 2.62 秒：四个阶段的实现与优化', size=19, color=SKY, line=1.2)
for i, (val, lab) in enumerate([('4', '阶段全部达标'), ('2.62 s', '倒序盘面用时'),
                                ('3.0×', 'IDA* 比参考快')]):
    l = 1.05 + i * 2.40
    if i:
        add_rect(s, l - 0.30, 4.62, 0.010, 0.68, fill=RGBColor(0x2C, 0x4A, 0x78))
    tf = textbox(s, l, 4.58, 2.15, 0.74)
    para(tf, True, val, size=26, bold=True, color=WHITE, line=1.0)
    para(tf, False, lab, size=10, color=SKY, before=3)
tf = textbox(s, 1.00, 5.92, 8.6, 1.0)
para(tf, True, '人工智能　项目一　|　汇报 5 分钟', size=11.5, color=RGBColor(0x8F, 0xA8, 0xC8), line=1.5)
para(tf, False, '周以撒　李行　林子航　张文璟', size=12.5, color=WHITE, line=1.5, before=4)
notes(s, 12, '老师好，我们四个人各讲一块。我先讲数据结构和启发函数，李行讲 A* 和 IDA*，'
             '林子航讲模式数据库，张文璟讲可视化和结论。')

# ===== 02 四个阶段 + 分工 =====
s = content(prs, '题目和四个阶段', '周以撒', accent=BLUE)
stages = [
    ('一', '3 阶 1 秒内', '填框架、跑通', '0.156 s'),
    ('二', '4 阶同参考级别', '数据结构提速', '2.4 倍'),
    ('三', '倒序盘面 1 分钟内', '换启发函数', '2.62 s'),
    ('四', '解路径可视化', '窗口动画', '批量连播'),
]
w = (CW - 3 * 0.24) / 4
for i, (num, req, tag, ret) in enumerate(stages):
    l = M + i * (w + 0.24)
    add_rect(s, l, 1.48, w, 2.12, fill=WHITE, line=LINE,
             shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.06)
    add_rect(s, l, 1.48, w, 0.05, fill=BLUE)
    circ = add_rect(s, l + 0.26, 1.70, 0.46, 0.46, fill=BLUE, shape=MSO_SHAPE.OVAL)
    shape_text(circ, num, size=15, bold=True, color=WHITE)
    tf = textbox(s, l + 0.26, 2.30, w - 0.48, 1.1)
    para(tf, True, req, size=12.5, bold=True, color=INK, line=1.2)
    para(tf, False, tag, size=10, color=MUTED, before=6)
    add_rect(s, l + 0.26, 2.92, w - 0.52, 0.010, fill=LINE)
    tf = textbox(s, l + 0.26, 3.02, w - 0.48, 0.44)
    para(tf, True, ret, size=15, bold=True, color=GREEN)
add_rect(s, M, 3.86, CW, 1.72, fill=WHITE, line=LINE,
         shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.05)
rows = [('周以撒', '数据结构 + 三种基础启发函数', BLUE),
        ('李　行', 'A* 与 IDA* 两个搜索算法', CYAN),
        ('林子航', '模式数据库 + 阶段三达标', PURPLE),
        ('张文璟', '可视化 + 结论', AMBER)]
for i, (nm, what, col) in enumerate(rows):
    t = 3.98 + i * 0.38
    add_rect(s, M + 0.26, t + 0.07, 0.045, 0.22, fill=col)
    tf = textbox(s, M + 0.40, t, 1.05, 0.36, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, nm, size=10.5, bold=True, color=col)
    tf = textbox(s, M + 1.55, t, CW - 1.9, 0.36, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, what, size=11, color=INK)
tf = textbox(s, M, 5.72, CW, 0.5)
para(tf, True, '四个人四块内容，互不重复。四块拼起来就是从跑通到 2.62 秒的全过程。',
     size=10.5, color=MUTED, line=1.2)
notes(s, 18, '四个阶段一层比一层难。我们四个人按这个顺序讲：我讲数据结构和启发函数，'
             '李行讲搜索算法，林子航讲模式数据库，张文璟收尾。')

# ===== 03 数据结构 =====
s = content(prs, '数据结构：把盘面压成一个数', '周以撒', accent=BLUE)
add_rect(s, M, 1.46, 5.80, 1.32, fill=WHITE, line=LINE,
         shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.06)
tf = textbox(s, M + 0.26, 1.60, 5.35, 1.06)
para(tf, True, '原来：16 个数字的数组', size=12.5, bold=True, color=MUTED)
para(tf, False, '比较一次要挨个比 16 个数　|　一个状态约 128 字节', size=10.5, before=7, line=1.2)
add_rect(s, M + 6.10, 1.46, 5.80, 1.32, fill=WHITE, line=BLUE, line_w=1.5,
         shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.06)
tf = textbox(s, M + 6.36, 1.60, 5.35, 1.06)
para(tf, True, '现在：压成 1 个 64 位整数', size=12.5, bold=True, color=BLUE)
para(tf, False, '每格占 4 位　|　比较一次只比 1 个数　|　一个状态约 48 字节', size=10.5, before=7, line=1.2)
data = [
    ['4 阶实例', '原来要多久', '压成一个数之后', '提速'],
    ['1（52 步）', '0.728 s', '0.475 s', ('1.5 倍', GREEN, True)],
    ['2（51 步）', '15.502 s', '6.476 s', ('2.4 倍', GREEN, True)],
    ['3（56 步）', '49.989 s', '21.506 s', ('2.3 倍', GREEN, True)],
]
add_table(s, data, M, 2.98, [3.30, 3.10, 3.10, 2.39], row_h=0.46, head_h=0.44, size=11.5)
add_rect(s, M, 4.92, CW, 0.98, fill=WHITE, line=LINE,
         shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.05)
tf = textbox(s, M + 0.28, 5.04, CW - 0.56, 0.74)
para(tf, True, '还有一处改了但没用：Open 表把"删旧结点"换成"标作废，取出来再丢"', size=11.5,
     bold=True, color=AMBER)
para(tf, False, '我们原以为这是主要瓶颈，实测只快了 4%~14%。真正管用的是把盘面压小。',
     size=10.5, color=INK, before=6, line=1.2)
tf = textbox(s, M, 6.12, CW, 0.42)
para(tf, True, '压小之后，比较和哈希都从 16 步变成 1 步；这一步在搜索里要重复几千万次。',
     size=10, color=MUTED, line=1.2)
notes(s, 30, '我负责数据结构和启发函数。原来盘面用数组存，比较一次要挨个比 16 个数，'
             '一个状态一百多字节。把整个盘面压成一个 64 位整数，每格 4 位，比一次只比一个数，'
             '内存也少了一半多。4 阶三个实例从 0.73、15.5、50 秒降到 0.48、6.5、21.5 秒，快了 2.4 倍。'
             '另外我们还改了一处 Open 表，把删旧结点换成标作废，原以为这是瓶颈，实测只快了百分之四到十四。')

# ===== 04 三种估步数的办法 =====
s = content(prs, '三种估步数的办法', '周以撒', accent=BLUE)
cards = [
    ('错位将牌数', '数有几块牌不在自己的位置上', '猜得最少', '3 阶要 0.156 秒', AMBER),
    ('曼哈顿距离', '每块牌要横竖走几步，全部加起来', '猜得准多了', '3 阶到毫秒级', TEAL),
    ('线性冲突', '同行同列两块牌顺序反了，至少多绕 2 步', '不用建表', '纯算术，零内存', CYAN),
]
w = (CW - 2 * 0.26) / 3
for i, (nm, how, tag, note, col) in enumerate(cards):
    l = M + i * (w + 0.26)
    add_rect(s, l, 1.46, w, 1.92, fill=WHITE, line=LINE,
             shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.06)
    add_rect(s, l, 1.46, w, 0.05, fill=col)
    tf = textbox(s, l + 0.26, 1.64, w - 0.50, 1.60)
    para(tf, True, nm, size=14, bold=True, color=INK)
    para(tf, False, how, size=10.5, color=RGBColor(0x33, 0x41, 0x55), before=8, line=1.2)
    para(tf, False, tag, size=10.5, bold=True, color=col, before=10)
    para(tf, False, note, size=9.5, color=MUTED, before=3)
data = [
    ['3 阶实例', '解长', '错位将牌数', '曼哈顿距离'],
    ['1', '21', '0.016 s', ('0.000 s', GREEN, True)],
    ['2', '31', '0.156 s', ('0.016 s', GREEN, True)],
    ['3', '31', '0.141 s', ('0.016 s', GREEN, True)],
]
add_table(s, data, M, 3.60, [3.20, 2.60, 3.00, 3.09], row_h=0.44, head_h=0.42, size=11.5)
band(s, 5.52, '三种方法解出来的步数必须一模一样。哪天不一样，就说明代码里出了 bug，'
              '这是我们最省事的自查开关。', h=0.72, accent=TEAL, tag=None)
tf = textbox(s, M, 6.38, CW, 0.42)
para(tf, True, '错位将牌数永远不如曼哈顿：每块不在位的牌，曼哈顿距离至少是 1，所以前者一定更松。',
     size=10, color=MUTED, line=1.2)
notes(s, 37, '启发函数我做了三个。错位将牌数就是数几块牌不在位，猜得最少，3 阶要 0.156 秒。'
             '曼哈顿距离是把每块牌横竖要走几步加起来，猜得准多了，直接到毫秒级。'
             '线性冲突是同行同列两块牌顺序反了、至少多绕两步，它一张表都不用建。'
             '三种方法解出来的步数必须一样，这是我们的自查开关。')

# ===== 05 A* 与 IDA* 的四个性质 =====
s = content(prs, 'A* 和 IDA* 的四个性质', '李行', accent=CYAN)
data = [
    ['性质', '是什么意思', 'A*', 'IDA*'],
    ['可采纳性', '估算不多猜，永远不超过真实步数', ('必须有', RED), ('必须有', RED)],
    ['单调性（一致性）', '沿路径往前走，估算不会突然变松', ('必须有，否则要重开结点', AMBER),
     ('不用，只要可采纳', GREEN)],
    ['完备性', '有解就一定能找到', ('有', GREEN), ('有', GREEN)],
    ['最优性', '找到的那个一定是最短的', ('有', GREEN), ('有', GREEN)],
]
add_table(s, data, M, 1.44, [2.85, 4.55, 2.50, 3.09], row_h=0.56, head_h=0.44, size=11)
add_rect(s, M, 4.42, CW, 1.16, fill=WHITE, line=AMBER, line_w=1.2,
         shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.05)
add_rect(s, M, 4.52, 0.05, 0.96, fill=AMBER)
tf = textbox(s, M + 0.28, 4.56, CW - 0.56, 0.92)
para(tf, True, '单调性这条最容易被忽略', size=11.5, bold=True, color=AMBER)
para(tf, False, '我们框架的 A* 一旦把状态放进 Closed，就不会再取回来。这只有在估算单调时才保证最优；'
                '不单调就得允许把结点重新放回 Open。IDA* 不需要单调性，只要可采纳就是最优的。',
     size=10.5, color=INK, before=6, line=1.2)
tf = textbox(s, M, 5.82, CW, 0.44)
para(tf, True, '所以选算法的时候，先看你的估算能不能证明这两个性质，再看内存够不够。',
     size=10.5, color=MUTED, line=1.2)
notes(s, 28, '这一页是四个性质。可采纳性是估算不能多猜，两个算法都必须有，有了才保证最优。'
             '单调性也叫一致性，是沿着路径走估算不会突然变松；A* 必须有，因为框架的 A* 把状态放进 Closed 就不取回了；'
             'IDA* 不需要。完备性是有解就能找到，最优性是找到的一定最短，这两个算法都有。')

# ===== 06 优缺点与数据 =====
s = content(prs, '各自的长处和短处', '李行', accent=CYAN)
card(s, M, 1.44, 5.80, 1.86, 'A*：不重复搜，但内存会爆', [
    '长处：同一个搜索树上只走一遍，小规模问题更快',
    '短处：内存跟着"存过多少状态"走，状态一多就崩',
], accent=BLUE, tsize=13, bsize=10.5, gap=6)
card(s, M + 6.10, 1.44, 5.80, 1.86, 'IDA*：内存不怕，但要重复搜', [
    '长处：内存只跟解路径的长度有关，永远存得下',
    '短处：上限每放宽一次，就要从头再搜一遍',
], accent=CYAN, tsize=13, bsize=10.5, gap=6)
data = [
    ['场景', 'A*', 'IDA*', '谁占优'],
    ['3 阶实例 1（21 步）', '0.004 s', '0.023 s', ('A* 快 6 倍', BLUE, True)],
    ['4 阶 61 步实例', ('跑不动', RED), '108 s', ('IDA*', CYAN, True)],
    ['倒序盘面（70 步）', ('12 GB 崩掉', RED), '516 MB / 828 s', ('IDA* 才跑得动', CYAN, True)],
]
add_table(s, data, M, 3.48, [4.30, 2.60, 3.20, 2.89], row_h=0.48, head_h=0.44, size=11)
band(s, 5.78, '小问题 A* 快，因为不用重搜；大问题只有 IDA* 能跑，因为它存得下。'
              '我们的阶段三恰好落在大问题这一边。', h=0.72, accent=CYAN, tag=None)
notes(s, 28, 'A* 的长处是不重复搜，3 阶实例它比 IDA* 快 6 倍；短处是内存跟着状态数走，'
             '4 阶第 9 个实例就跑不动了，倒序盘面给到 12 GB 直接崩。IDA* 反过来，内存只跟路径长度有关，'
             '同样的盘面 516 MB 就够，代价是要重搜，所以花了 828 秒。'
             '小问题 A* 快，大问题只有 IDA* 能跑。')

# ===== 07 对标 =====
s = content(prs, '和老师的参考程序对一遍', '李行', accent=CYAN)
data = [
    ['A* 实例', '参考：解几步', '我们：解几步', '参考：搜过多少', '我们：搜过多少'],
    ['1（3 阶）', '21', ('21', GREEN, True), '931', ('931', GREEN, True)],
    ['2（3 阶）', '31', ('31', GREEN, True), '21,197', ('21,197', GREEN, True)],
    ['5（4 阶）', '51', ('51', GREEN, True), '3,628,148', ('3,628,148', GREEN, True)],
    ['6（4 阶）', '50', ('50', GREEN, True), '5,229,940', ('5,229,940', GREEN, True)],
]
add_table(s, data, M, 1.44, [2.40, 2.30, 2.30, 2.55, 2.44], row_h=0.48, head_h=0.44, size=11)
add_rect(s, M, 4.30, CW, 0.86, fill=WHITE, line=GREEN, line_w=1.2,
         shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.05)
add_rect(s, M, 4.40, 0.05, 0.66, fill=GREEN)
tf = textbox(s, M + 0.28, 4.36, CW - 0.56, 0.74, anchor=MSO_ANCHOR.MIDDLE)
para(tf, True, '连搜了多少个状态都一样，说明两个程序搜的顺序完全一样。'
               '这比时间差不多有说服力得多。', size=11, color=INK, line=1.2)
tf = textbox(s, M, 5.36, CW, 0.7)
para(tf, True, 'IDA* 那边 9 个实例解出步数也全部一致，总耗时 136.2 秒，参考程序 404.1 秒。',
     size=10.5, color=MUTED, line=1.25)
para(tf, False, '我们特意不比时间：两台机器不一样，比的是解几步、搜过多少个状态，这两个数跟机器无关。',
     size=10.5, color=MUTED, line=1.25, before=4)
notes(s, 24, '我实现完 IDA* 之后做了对标。A* 六个实例，解出步数和搜过的状态数跟老师的参考程序完全一样。'
             'IDA* 九个实例步数也全对，总耗时 136 秒，参考 404 秒。'
             '我们特意不比时间，因为机器不一样，比的是解长和搜过的状态数。')

# ===== 08 模式数据库 =====
s = content(prs, '模式数据库：只看一部分牌', '林子航', accent=PURPLE)
card(s, M, 1.42, 5.80, 1.86, '① 只看一部分牌', [
    '15 块牌全算要 10 万亿种摆法，算不完',
    '只盯 5 块牌加空格，剩下 10 块当长得一样的杂物',
    '这样只有 576 万种，一组表 11 MB',
], accent=PURPLE, tsize=13, bsize=10.5, gap=5)
card(s, M + 6.10, 1.42, 5.80, 1.86, '② 代价划分：三张表才能相加', [
    '每组都把空格挪动算一步，就重复记账，加起来偏大',
    '我们规定只给自己组的牌记步数，空格蹭别人的牌不算钱',
    '三张表加起来仍然不超过真实步数，解还是最优的',
], accent=BLUE, tsize=13, bsize=10.5, gap=5)
add_rect(s, M, 3.42, CW, 0.84, fill=WHITE, line=AMBER, line_w=1.2,
         shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.05)
add_rect(s, M, 3.52, 0.05, 0.64, fill=AMBER)
tf = textbox(s, M + 0.28, 3.46, CW - 0.56, 0.76)
para(tf, True, '③ 倒着把表填满，两趟扫描的顺序不能反', size=11.5, bold=True, color=AMBER)
para(tf, False, '从目标往外走，先走完这一层不要钱的通道，再往下走一层。顺序反了，'
                '本该 3 步的地方会记成 4 步，就猜多了。', size=10, color=INK, before=4, line=1.2)
kpi_row(s, [('576 万', '一组要遍历的摆法', PURPLE),
            ('0.7 s', '一组建表要多久', BLUE),
            ('11 MB', '一组表占多大', TEAL)], 4.40, h=1.00)
band(s, 5.56, '三张表要相加，就得规定只给自己组的牌记步数。这是我们的做法，也是它能保持最优的原因。',
     h=0.68, accent=PURPLE, tag='创新')
notes(s, 35, '我讲模式数据库。思路是提前把答案算好存起来，代价是放弃看全：'
             '只盯 5 块牌加空格，576 万种摆法，一组表 11 MB。'
             '关键是代价划分：三张表要相加，必须规定只给自己组的牌记步数，不然重复记账结果会偏大。'
             '建表是从目标倒着走，先走完不要钱的通道再往下走一层，顺序反了就会猜多。')

# ===== 09 效果数据 =====
s = content(prs, '换上模式数据库之后', '林子航', accent=PURPLE)
tf = textbox(s, M, 1.40, CW, 0.28)
para(tf, True, '阶段二：4 阶实例，A*，只换启发函数', size=11, bold=True, color=PURPLE)
data = [
    ['实例', '解长', '曼哈顿距离', '模式数据库', '提速'],
    ['2', '51', '6.476 s', ('0.264 s', GREEN, True), ('24.5 倍', GREEN, True)],
    ['3', '56', '21.506 s', ('0.289 s', GREEN, True), ('74.4 倍', GREEN, True)],
    ['4', '57', '110.550 s', ('1.049 s', GREEN, True), ('105 倍', GREEN, True)],
    ['5', '50', '13.191 s', ('0.069 s', GREEN, True), ('191 倍', GREEN, True)],
]
add_table(s, data, M, 1.68, [2.10, 1.95, 2.85, 2.60, 2.39], row_h=0.35, head_h=0.36, size=10.5)
tf = textbox(s, M, 3.54, CW, 0.28)
para(tf, True, '阶段三：那个倒序盘面，一路走下来', size=11, bold=True, color=PURPLE)
data = [
    ['方案', '解出几步', '搜过的状态', '耗时', '达标'],
    ['A* + 模式数据库', '—', '—', '内存爆了（12 GB）', ('否', RED)],
    ['IDA* + 模式数据库', ('70（最短）', GREEN), '10.24 亿', '828.25 s', ('否', RED)],
    ['IDA* + 更强的估算', ('70（最短）', GREEN), '2.63 亿', '369.35 s', ('否', RED)],
    ['再把估算放大一点（加权）', ('72', GREEN, True), '155 万', '2.62 s', ('是', GREEN, True)],
]
add_table(s, data, M, 3.84, [3.85, 1.85, 1.85, 1.90, 2.44], row_h=0.35, head_h=0.36, size=10.5)
band(s, 5.90, '达标靠的是把估算放大一点，代价是解从 70 步变成 72 步。70 步那份我们也没丢，两个结果都写进报告。',
     h=0.72, accent=PURPLE, tag=None)
notes(s, 32, '换上模式数据库，阶段二快 24 到 191 倍，还把第 6 个实例从跑不动变成 31 秒解出。'
             '阶段三那个倒序盘面：A* 内存爆了；IDA* 能拿到 70 步最优解但要 828 秒；'
             '换成更强的估算降到 369 秒；最后把估算故意放大一点，2.62 秒解出 72 步，达标。')

# ===== 10 可视化 =====
s = content(prs, '解路径可视化', '张文璟', accent=AMBER)
tf = textbox(s, M, 1.40, CW, 0.32)
para(tf, True, '同一个盘面，动画放出来的三个瞬间', size=11, color=MUTED)
boards = [
    (START_CELLS, '开始：几乎全是蓝的', MUTED),
    ([5, 1, 2, 4, 9, 6, 3, 8, 13, 10, 7, 12, 14, 15, 11, 0], '中间：绿的越来越多', PURPLE),
    (GOAL_CELLS, '结束：全部归位', GREEN),
]
bw = 2.30
gap = (CW - 3 * bw) / 2
for i, (cells, cap, col) in enumerate(boards):
    l = M + i * (bw + gap)
    board(s, l, 1.78, bw, cells, fsize=13)
    tf = textbox(s, l, 4.28, bw, 0.30)
    para(tf, True, cap, size=10.5, bold=True, color=col, align=PP_ALIGN.CENTER)
for i in range(2):
    x = M + bw + i * (bw + gap) + 0.16
    add_rect(s, x, 2.72, gap - 0.32, 0.026, fill=LINE)
tf = textbox(s, M, 4.86, CW, 1.5)
para(tf, True, '三种颜色就是三种状态', size=11.5, bold=True, color=INK)
para(tf, False, '绿色 = 这块牌已经在自己该在的位置；蓝色 = 还没到位；空格是深色槽。'
                '刚滑过来的那一块会加一圈琥珀色描边，方便看清每一步动了谁。', size=10.5, color=INK,
     before=6, line=1.25)
para(tf, False, '支持播放、暂停、单步前进后退、拖动调速；也可以批量连播，关掉一个窗口自动弹下一个。',
     size=10.5, color=INK, before=6, line=1.25)
notes(s, 26, '可视化我负责。这里放的是同一个盘面动画里的三个瞬间：开始时几乎全是蓝色，'
             '中间绿色越来越多，最后全部归位。绿色表示这块牌已经到位，刚移动的那一块会加一圈琥珀色描边。'
             '可以播放、暂停、单步、调速，也能批量连播。')

# ===== 11 结论 =====
s = content(prs, '结论和不足', '张文璟', accent=AMBER)
data = [
    ['阶段', '要求', '结果'],
    ['一', '3 阶 1 秒内', '最慢 0.156 s，余量 6 倍以上'],
    ['二', '4 阶同参考级别', '解几步、搜多少个，都和参考程序一个不差'],
    ['三', '倒序盘面 1 分钟内', '2.62 s 达标（72 步）；另有 70 步最短解，369 s'],
    ['四', '解路径可视化', '窗口动画加批量连播，3 阶和 4 阶都验证过'],
]
add_table(s, data, M, 1.44, [1.00, 3.30, 7.69], row_h=0.44, head_h=0.42, size=11)
add_rect(s, M, 3.92, CW, 1.46, fill=WHITE, line=RED, line_w=1.2,
         shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.05)
add_rect(s, M, 4.02, 0.05, 1.26, fill=RED)
tf = textbox(s, M + 0.28, 4.04, CW - 0.56, 1.26)
para(tf, True, '两个不足', size=11.5, bold=True, color=RED)
para(tf, False, '① 模式数据库我们证明了它不会多猜，但没证出"越走估算越小"这一条，'
                '理论上解可能不是最短的，实验里没遇到。', size=10.5, color=INK, before=6, line=1.2)
para(tf, False, '② 阶段三能进 1 分钟是把估算放大换来的，不保证最短。要最短就得接受 369 秒。',
     size=10.5, color=INK, before=5, line=1.2)
tf = textbox(s, M, 5.54, CW, 0.7)
para(tf, True, '四个阶段都达标了。最耗时间的那个盘面，从跑不完变成 2.62 秒。',
     size=11, color=MUTED, line=1.2)
notes(s, 22, '四个阶段都达标。不足有两个：模式数据库的单调性我们没证出来，'
             '阶段三能进 1 分钟是把估算放大换来的、不保证最短，这两点报告里都写了。')

# ===== 12 谢谢 =====
s = prs.slides.add_slide(prs.slide_layouts[6])
s.shapes.add_picture(BG_DARK, 0, 0, width=prs.slide_width, height=prs.slide_height)
tf = textbox(s, 1.10, 2.05, SW - 2.2, 1.10)
para(tf, True, '谢谢，请老师和同学提问', size=40, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
add_rect(s, SW / 2 - 0.55, 3.32, 1.10, 0.05, fill=RGBColor(0x22, 0xD3, 0xEE))
tf = textbox(s, 1.10, 3.60, SW - 2.2, 0.5)
para(tf, True, '四个阶段全部达标　|　倒序盘面 2.62 秒　|　和参考程序搜过的状态一模一样',
     size=12, color=SKY, align=PP_ALIGN.CENTER, line=1.3)
board(s, SW / 2 - 1.20, 4.42, 2.40, GOAL_CELLS, fsize=11)
tf = textbox(s, 1.10, 6.72, SW - 2.2, 0.36)
para(tf, True, '周以撒　李行　林子航　张文璟', size=10, color=DIM, align=PP_ALIGN.CENTER)
notes(s, 8, '我们的汇报到这里，谢谢老师。')

prs.core_properties.title = 'N-Puzzle 问题求解 · 项目演示（四人分工版）'
prs.core_properties.author = '周以撒 李行 林子航 张文璟'
OUT = sys.argv[1] if len(sys.argv) > 1 else 'N-Puzzle项目演示-分工版.pptx'
prs.save(OUT)
print('saved:', OUT, 'slides =', len(prs.slides._sldIdLst))
