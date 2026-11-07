# -*- coding: utf-8 -*-
"""《N-Puzzle 问题求解》四人分工演示（12 页 / 300 秒）—— 关键词版。

设计原则：
  1. 页面上只放关键词和数字，解释一律留在备注（讲的时候说）
  2. 数据尽量做成图（柱状图、对比条），不用表格堆数字
  3. 配色收敛：深藏蓝 + 主蓝 + 警示红 + 中性灰，讲者只用灰色小字标注
  4. 版式有变化，不十二页一个样

运行：py -3 make_ppt_v4.py [输出文件名]
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

INK   = RGBColor(0x0B, 0x1B, 0x33)     # 主文字 / 深底
BLUE  = RGBColor(0x1D, 0x4E, 0xDB)     # 主色
BLUE_L = RGBColor(0x5B, 0x8E, 0xF0)    # 浅主色
RED   = RGBColor(0xDC, 0x26, 0x26)     # 警示
GREEN = RGBColor(0x15, 0x8A, 0x3E)     # 达标
MUTED = RGBColor(0x6B, 0x7A, 0x8F)     # 中性
LINE  = RGBColor(0xDC, 0xE3, 0xEE)
SURF  = RGBColor(0xF5, 0xF8, 0xFD)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
GHOST = RGBColor(0xE9, 0xEE, 0xF7)
SKY   = RGBColor(0x9F, 0xC6, 0xF2)
DIM   = RGBColor(0x6F, 0x88, 0xA8)

SW, SH = 13.333, 7.5
M = 0.85
CW = SW - 2 * M
NO_STYLE = '{2D5ABB26-0587-4C30-8999-92F81FD0307C}'


def set_run(run, text, size=12, bold=False, color=INK, font=YH, spc=None):
    run.text = text
    f = run.font
    f.size = Pt(size)
    f.bold = bold
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
         before=0, after=0, align=PP_ALIGN.LEFT, line=1.1, spc=None):
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


PAGE = {'n': 1}


def content(prs, title, who):
    s = prs.slides.add_slide(prs.slide_layouts[6])
    s.shapes.add_picture(BG_LIGHT, 0, 0, width=prs.slide_width, height=prs.slide_height)
    add_rect(s, M, 0.72, 0.05, 0.46, fill=BLUE)
    tf = textbox(s, M + 0.22, 0.66, 9.6, 0.58, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, title, size=27, bold=True, color=INK)
    PAGE['n'] += 1
    tf = textbox(s, 8.70, 0.36, 1.90, 0.26, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, who, size=10.5, color=MUTED, align=PP_ALIGN.RIGHT)
    tf = textbox(s, 10.95, 0.30, 1.55, 0.80, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, '%02d' % PAGE['n'], size=38, bold=True, color=GHOST, align=PP_ALIGN.RIGHT)
    add_rect(s, M, 1.34, CW, 0.008, fill=LINE)
    tf = textbox(s, M, 7.02, 6.0, 0.24, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, 'N-Puzzle 问题求解', size=8.5, color=RGBColor(0xA8, 0xB6, 0xC8))
    return s


def notes(slide, secs, text):
    slide.notes_slide.notes_text_frame.text = '【约 %d 秒】%s' % (secs, text)


def stat(slide, l, t, w, value, label, color=BLUE, vsize=34, lsize=10.5):
    tf = textbox(slide, l, t, w, 0.62)
    para(tf, True, value, size=vsize, bold=True, color=color, line=1.0)
    tf = textbox(slide, l, t + 0.66, w, 0.30)
    para(tf, True, label, size=lsize, color=MUTED)


def kw_card(slide, l, t, w, h, title, keys, accent=BLUE, tsize=15, ksize=12):
    """标题 + 几个关键词，不放句子。卡片用直角，顶条才能严丝合缝。"""
    add_rect(slide, l, t, w, h, fill=WHITE, line=LINE, line_w=1.0)
    add_rect(slide, l, t, w, 0.05, fill=accent)
    tf = textbox(slide, l + 0.28, t + 0.28, w - 0.56, h - 0.45)
    para(tf, True, title, size=tsize, bold=True, color=INK, line=1.0)
    for k in keys:
        para(tf, False, k, size=ksize, color=MUTED, before=9, line=1.1)


def hbar(slide, l, t, w, h, label, value_str, frac, color=BLUE, lab_w=1.9):
    """水平对比条，frac 是 0~1 的相对长度。"""
    tf = textbox(slide, l, t, lab_w, h, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, label, size=11.5, bold=True, color=INK)
    bar_x = l + lab_w + 0.1
    bar_max = w - lab_w - 1.62
    add_rect(slide, bar_x, t + h * 0.22, bar_max, h * 0.56, fill=RGBColor(0xEE, 0xF2, 0xF8))
    add_rect(slide, bar_x, t + h * 0.22, max(bar_max * frac, 0.06), h * 0.56,
             fill=color, shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.25)
    tf = textbox(slide, bar_x + bar_max + 0.14, t, 1.40, h, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, value_str, size=12.5, bold=True, color=color)


def vbars(slide, l, t, w, h, items, unit='', color=BLUE, vmax=None):
    """竖直柱状图。items = [(标签, 数值, 显示文字)]"""
    vals = [it[1] for it in items]
    vmax = vmax or max(vals) * 1.15
    n = len(items)
    slot = w / n
    bw = slot * 0.46
    base_y = t + h
    add_rect(slide, l, base_y, w, 0.012, fill=LINE)
    for i, (lab, v, disp) in enumerate(items):
        bh = h * (v / vmax)
        x = l + slot * i + (slot - bw) / 2
        add_rect(slide, x, base_y - bh, bw, bh, fill=color,
                 shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.12)
        tf = textbox(slide, x - 0.25, base_y - bh - 0.42, bw + 0.5, 0.36)
        para(tf, True, disp, size=13, bold=True, color=color, align=PP_ALIGN.CENTER)
        tf = textbox(slide, x - 0.25, base_y + 0.12, bw + 0.5, 0.30)
        para(tf, True, lab, size=10.5, color=MUTED, align=PP_ALIGN.CENTER)


def board(slide, left, top, size, cells, n=4, fsize=15):
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

GOAL = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0]
START = [15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 1, 2, 0]
MID = [5, 1, 2, 4, 9, 6, 3, 8, 13, 10, 7, 12, 14, 15, 11, 0]

# ===== 01 封面 =====
s = prs.slides.add_slide(prs.slide_layouts[6])
s.shapes.add_picture(BG_DARK, 0, 0, width=prs.slide_width, height=prs.slide_height)
board(s, 9.70, 1.80, 2.90, [7, 2, 4, 5, 0, 6, 3, 8, 9, 1, 11, 12, 13, 10, 14, 15], fsize=13)
tf = textbox(s, 1.05, 1.92, 8.2, 1.05)
para(tf, True, 'N-Puzzle 问题求解', size=54, bold=True, color=WHITE, line=1.0)
add_rect(s, 1.10, 3.18, 1.00, 0.05, fill=RGBColor(0x22, 0xD3, 0xEE))
tf = textbox(s, 1.05, 3.44, 7.8, 0.5)
para(tf, True, '四个阶段　·　四个人', size=22, color=SKY)
for i, (v, lab) in enumerate([('4', '阶段达标'), ('2.62 s', '倒序盘面'),
                              ('3.0×', 'IDA* vs 参考')]):
    l = 1.10 + i * 2.45
    if i:
        add_rect(s, l - 0.32, 4.46, 0.008, 0.72, fill=RGBColor(0x2C, 0x4A, 0x78))
    tf = textbox(s, l, 4.42, 2.15, 0.46)
    para(tf, True, v, size=30, bold=True, color=WHITE, line=1.0)
    tf = textbox(s, l, 4.94, 2.15, 0.28)
    para(tf, True, lab, size=10, color=SKY)
tf = textbox(s, 1.05, 6.02, 8.6, 0.9)
para(tf, True, '人工智能　项目一　·　5 分钟', size=11, color=RGBColor(0x8F, 0xA8, 0xC8))
para(tf, False, '周以撒　李行　林子航　张文璟', size=13, color=WHITE, before=5)
notes(s, 12, '老师好。我们四个人各讲一块：我先讲数据结构和启发函数，李行讲 A* 和 IDA*，'
             '林子航讲模式数据库，张文璟讲可视化和结论。')

# ===== 02 四个阶段 + 分工 =====
s = content(prs, '四个阶段', '周以撒')
stages = [('一', '3 阶 · 1 秒内', '0.156 s'), ('二', '4 阶 · 同参考级', '2.4 倍'),
          ('三', '倒序盘面 · 1 分钟', '2.62 s'), ('四', '解路径可视化', 'Swing')]
w = (CW - 3 * 0.28) / 4
for i, (num, req, ret) in enumerate(stages):
    l = M + i * (w + 0.28)
    add_rect(s, l, 1.56, w, 1.94, fill=WHITE, line=LINE,
             shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.05)
    circ = add_rect(s, l + 0.30, 1.80, 0.44, 0.44, fill=BLUE, shape=MSO_SHAPE.OVAL)
    shape_text(circ, num, size=14, bold=True, color=WHITE)
    tf = textbox(s, l + 0.30, 2.42, w - 0.55, 0.35)
    para(tf, True, req, size=13, bold=True, color=INK)
    tf = textbox(s, l + 0.30, 2.92, w - 0.55, 0.44)
    para(tf, True, ret, size=19, bold=True, color=GREEN)
tf = textbox(s, M, 3.82, CW, 0.30)
para(tf, True, '分工', size=11, bold=True, color=BLUE, spc=120)
rows = [('周以撒', '数据结构 · 启发函数'), ('李　行', 'A* · IDA*'),
        ('林子航', '模式数据库'), ('张文璟', '可视化 · 结论')]
for i, (nm, what) in enumerate(rows):
    t = 4.20 + i * 0.52
    tf = textbox(s, M, t, 1.30, 0.42, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, nm, size=11.5, bold=True, color=INK)
    tf = textbox(s, M + 1.50, t, CW - 1.5, 0.42, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, what, size=12, color=MUTED)
notes(s, 18, '四个阶段一层比一层难。四个人四块内容，互不重复：我讲数据结构和启发函数，'
             '李行讲两个搜索算法，林子航讲模式数据库，张文璟讲可视化和结论。')

# ===== 03 数据结构 =====
s = content(prs, '把盘面压成一个数', '周以撒')
add_rect(s, M, 1.50, 5.70, 1.52, fill=WHITE, line=LINE,
         shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.05)
tf = textbox(s, M + 0.30, 1.66, 5.1, 0.40)
para(tf, True, '数组', size=15, bold=True, color=MUTED)
for i, k in enumerate(['16 次比较', '128 字节']):
    tf = textbox(s, M + 0.30 + i * 2.45, 2.16, 2.3, 0.34)
    para(tf, True, k, size=13, color=MUTED)
add_rect(s, M + 6.20, 1.50, 5.70, 1.52, fill=WHITE, line=BLUE, line_w=1.6,
         shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.05)
tf = textbox(s, M + 6.50, 1.66, 5.1, 0.40)
para(tf, True, '64 位整数', size=15, bold=True, color=BLUE)
for i, k in enumerate(['1 次比较', '48 字节']):
    tf = textbox(s, M + 6.50 + i * 2.45, 2.16, 2.3, 0.34)
    para(tf, True, k, size=13, bold=True, color=BLUE)
data = [['4 阶实例', '原来', '之后', '提速'],
        ['1 · 52 步', '0.728 s', '0.475 s', ('1.5×', GREEN, True)],
        ['2 · 51 步', '15.502 s', '6.476 s', ('2.4×', GREEN, True)],
        ['3 · 56 步', '49.989 s', '21.506 s', ('2.3×', GREEN, True)]]
tbl = s.shapes.add_table(4, 4, Inches(M), Inches(3.30), Inches(CW), Inches(1.66)).table
tblPr = tbl._tbl.find(qn('a:tblPr'))
for el in tblPr.findall(qn('a:tableStyleId')):
    tblPr.remove(el)
el = tblPr.makeelement(qn('a:tableStyleId'), {})
el.text = NO_STYLE
tblPr.append(el)
tblPr.set('firstRow', '0')
tblPr.set('bandRow', '0')
for j, cw in enumerate([3.50, 3.20, 3.20, 2.59]):
    tbl.columns[j].width = Inches(cw)
tbl.rows[0].height = Inches(0.40)
for i in range(1, 4):
    tbl.rows[i].height = Inches(0.42)
for i, row in enumerate(data):
    for j, raw in enumerate(row):
        color, bold = None, False
        if isinstance(raw, tuple):
            text, color, bold = raw
        else:
            text = raw
        c = tbl.cell(i, j)
        c.margin_left = Inches(0.14)
        c.margin_top = Inches(0.02)
        c.margin_bottom = Inches(0.02)
        c.vertical_anchor = MSO_ANCHOR.MIDDLE
        c.fill.solid()
        c.fill.fore_color.rgb = INK if i == 0 else (WHITE if i % 2 else SURF)
        p = c.text_frame.paragraphs[0]
        p.alignment = PP_ALIGN.LEFT if j == 0 else PP_ALIGN.CENTER
        p.line_spacing = 1.0
        if i == 0:
            set_run(p.add_run(), text, size=10.5, bold=True, color=WHITE)
        else:
            set_run(p.add_run(), text, size=12.5,
                    bold=bold or (j == 0), color=color if color else (INK if j == 0 else MUTED))
tf = textbox(s, M, 5.42, CW, 0.34)
para(tf, True, '惰性删除　4%~14%', size=13, bold=True, color=AMBER if False else RGBColor(0xB4, 0x5F, 0x06))
tf = textbox(s, M, 5.84, CW, 0.34)
para(tf, True, '瓶颈不在 Open 表　·　在状态表示', size=13, color=MUTED)
notes(s, 30, '原来盘面用数组存，比较一次要挨个比 16 个数，一个状态一百多字节。'
             '把整个盘面压成一个 64 位整数，每格 4 位，比一次只比一个数，内存也少了一半多。'
             '4 阶三个实例从 0.73、15.5、50 秒降到 0.48、6.5、21.5 秒，快了 2.4 倍。'
             '另外我们把 Open 表的删旧结点改成标作废，原以为这是瓶颈，实测只快了百分之四到十四。'
             '所以真正的瓶颈不在 Open 表，在状态表示。')

# ===== 04 三种估步数 =====
s = content(prs, '三种估步数', '周以撒')
cards = [('错位将牌数', 'h = 不在位的牌数', '最松'),
         ('曼哈顿距离', 'h = Σ(|Δr| + |Δc|)', '更紧'),
         ('线性冲突', 'h = 曼哈顿 + 2×冲突对', '零内存')]
w = (CW - 2 * 0.30) / 3
for i, (nm, fml, note) in enumerate(cards):
    l = M + i * (w + 0.30)
    add_rect(s, l, 1.52, w, 1.58, fill=WHITE, line=LINE,
             shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.05)
    tf = textbox(s, l + 0.30, 1.72, w - 0.60, 0.36)
    para(tf, True, nm, size=14.5, bold=True, color=INK)
    tf = textbox(s, l + 0.30, 2.18, w - 0.60, 0.46)
    para(tf, True, fml, size=15, bold=True, color=BLUE)
    tf = textbox(s, l + 0.30, 2.74, w - 0.60, 0.28)
    para(tf, True, note, size=10.5, color=MUTED)
data = [['4 阶实例', '最优步数', '曼哈顿', '线性冲突'],
        ['52 步', '52', '44', ('44', MUTED, False)],
        ['50 步', '50', '34', ('36', GREEN, True)],
        ['57 步', '57', '41', ('43', GREEN, True)],
        ['倒序盘面', '70', '44', ('56', GREEN, True)]]
tbl = s.shapes.add_table(5, 4, Inches(M), Inches(3.30), Inches(CW), Inches(1.82)).table
tblPr = tbl._tbl.find(qn('a:tblPr'))
for el in tblPr.findall(qn('a:tableStyleId')):
    tblPr.remove(el)
el = tblPr.makeelement(qn('a:tableStyleId'), {})
el.text = NO_STYLE
tblPr.append(el)
tblPr.set('firstRow', '0')
tblPr.set('bandRow', '0')
for j, cw in enumerate([3.10, 2.50, 3.10, 2.93]):
    tbl.columns[j].width = Inches(cw)
tbl.rows[0].height = Inches(0.38)
for i in range(1, 5):
    tbl.rows[i].height = Inches(0.36)
for i, row in enumerate(data):
    for j, raw in enumerate(row):
        color, bold = None, False
        if isinstance(raw, tuple):
            text, color, bold = raw
        else:
            text = raw
        c = tbl.cell(i, j)
        c.margin_left = Inches(0.14)
        c.vertical_anchor = MSO_ANCHOR.MIDDLE
        c.fill.solid()
        c.fill.fore_color.rgb = INK if i == 0 else (WHITE if i % 2 else SURF)
        p = c.text_frame.paragraphs[0]
        p.alignment = PP_ALIGN.LEFT if j == 0 else PP_ALIGN.CENTER
        p.line_spacing = 1.0
        if i == 0:
            set_run(p.add_run(), text, size=10.5, bold=True, color=WHITE)
        else:
            set_run(p.add_run(), text, size=12.5,
                    bold=bold or (j == 0), color=color if color else (INK if j == 0 else MUTED))
tf = textbox(s, M, 5.36, CW, 0.36)
para(tf, True, '线性冲突　零内存　·　但比曼哈顿更紧', size=13, color=MUTED)
notes(s, 37, '启发函数我做了三个。错位将牌数就是数几块牌不在位，公式最简单，猜得最少；'
             '曼哈顿距离是把每块牌横竖要走几步加起来，更紧；'
             '线性冲突是在曼哈顿基础上，同行同列两块牌顺序反了再加两步，它一张表都不用建。'
             '这张表是四个实例上的估算值，可以看到线性冲突在困难的盘面上明显比曼哈顿紧，'
             '倒序盘面差了 12 步。三种方法解出来的步数必须一模一样，哪天不一样就说明有 bug。')

# ===== 05 定义 · 区别 · 特点 · 关联 =====
s = content(prs, 'A* 与 IDA*', '李行')
blocks = [
    ('定义', ['A*：f = g + h', 'IDA*：按 f 阈值反复深搜'], BLUE),
    ('区别', ['A* 存所有结点', 'IDA* 只存当前路径'], RED),
    ('特点', ['A* 快 · 内存爆', 'IDA* 省内存 · 重复搜'], GREEN),
    ('关联', ['同一套 f', 'IDA* = A* + 阈值'], MUTED),
]
bw = (CW - 0.40) / 2
bh = 1.62
for i, (nm, keys, col) in enumerate(blocks):
    l = M + (i % 2) * (bw + 0.40)
    t = 1.54 + (i // 2) * (bh + 0.36)
    kw_card(s, l, t, bw, bh, nm, keys, accent=col, tsize=12.5, ksize=15)
tf = textbox(s, M, 5.42, CW, 0.36)
para(tf, True, 'A* 进 Closed 不放回　·　要求单调　　|　　IDA* 只要可采纳', size=13.5, bold=True, color=INK)
notes(s, 28, 'A* 和 IDA* 用同一套 f = g + h。区别在于 A* 把所有生成过的结点都存进优先队列，'
             'IDA* 一张表都不存，只把当前 f 当阈值反复做深度优先搜索。'
             '所以 A* 快、但内存跟着状态数涨；IDA* 省内存、但每放宽一次阈值都要重搜。'
             '关联就在这儿：IDA* 本质上就是拿 A* 的 f 值当阈值，把全局最优搜索改成一层层加深的深搜。'
             '另外 A* 我们用的是进 Closed 不放回的策略，这要求估算单调；IDA* 只要求可采纳。')

# ===== 06 长处短处 =====
s = content(prs, '长处和短处', '李行')
tf = textbox(s, M, 1.54, 5.2, 0.56)
para(tf, True, 'A*', size=30, bold=True, color=BLUE)
tf = textbox(s, M, 2.24, 5.2, 0.36)
para(tf, True, '快', size=17, color=INK)
tf = textbox(s, M, 2.70, 5.2, 0.36)
para(tf, True, '内存爆', size=17, color=RED)
add_rect(s, M + 5.60, 1.54, 0.008, 1.52, fill=LINE)
tf = textbox(s, M + 5.92, 1.54, 5.2, 0.56)
para(tf, True, 'IDA*', size=30, bold=True, color=GREEN)
tf = textbox(s, M + 5.92, 2.24, 5.2, 0.36)
para(tf, True, '省内存', size=17, color=INK)
tf = textbox(s, M + 5.92, 2.70, 5.2, 0.36)
para(tf, True, '重复搜', size=17, color=MUTED)
tf = textbox(s, M, 3.34, CW, 0.30)
para(tf, True, '倒序盘面 · 同一个启发函数', size=11, bold=True, color=MUTED)
hbar(s, M, 3.62, CW, 0.62, 'A*', '12 GB 崩', 1.0, RED)
hbar(s, M, 4.34, CW, 0.62, 'IDA*', '516 MB', 0.043, GREEN)
tf = textbox(s, M, 5.14, CW, 0.34)
para(tf, True, '内存差 23 倍', size=15, bold=True, color=INK)
tf = textbox(s, M, 5.70, CW, 0.60)
para(tf, True, '3 阶：A* 0.004 s　·　IDA* 0.023 s', size=12.5, color=MUTED)
notes(s, 28, 'A* 的长处是不重复搜，3 阶实例它比 IDA* 快 6 倍；短处是内存跟着状态数走，'
             '倒序盘面给到 12 GB 直接崩。IDA* 反过来，内存只跟路径长度有关，同样的盘面 516 MB 就够，'
             '代价是要重搜，所以花了 828 秒。小问题 A* 快，大问题只有 IDA* 能跑。')

# ===== 07 对标 =====
s = content(prs, '和参考程序对标', '李行')
data = [['A* 实例', '参考解长', '我们解长', '参考结点', '我们结点'],
        ['1 · 3 阶', '21', ('21', GREEN, True), '931', ('931', GREEN, True)],
        ['2 · 3 阶', '31', ('31', GREEN, True), '21,197', ('21,197', GREEN, True)],
        ['5 · 4 阶', '51', ('51', GREEN, True), '3,628,148', ('3,628,148', GREEN, True)],
        ['6 · 4 阶', '50', ('50', GREEN, True), '5,229,940', ('5,229,940', GREEN, True)]]
tbl = s.shapes.add_table(5, 5, Inches(M), Inches(1.52), Inches(CW), Inches(2.66)).table
tblPr = tbl._tbl.find(qn('a:tblPr'))
for el in tblPr.findall(qn('a:tableStyleId')):
    tblPr.remove(el)
el = tblPr.makeelement(qn('a:tableStyleId'), {})
el.text = NO_STYLE
tblPr.append(el)
tblPr.set('firstRow', '0')
tblPr.set('bandRow', '0')
for j, cw in enumerate([2.50, 2.35, 2.35, 2.35, 2.08]):
    tbl.columns[j].width = Inches(cw)
tbl.rows[0].height = Inches(0.44)
for i in range(1, 5):
    tbl.rows[i].height = Inches(0.50)
for i, row in enumerate(data):
    for j, raw in enumerate(row):
        color, bold = None, False
        if isinstance(raw, tuple):
            text, color, bold = raw
        else:
            text = raw
        c = tbl.cell(i, j)
        c.margin_left = Inches(0.14)
        c.vertical_anchor = MSO_ANCHOR.MIDDLE
        c.fill.solid()
        c.fill.fore_color.rgb = INK if i == 0 else (WHITE if i % 2 else SURF)
        p = c.text_frame.paragraphs[0]
        p.alignment = PP_ALIGN.LEFT if j == 0 else PP_ALIGN.CENTER
        p.line_spacing = 1.0
        if i == 0:
            set_run(p.add_run(), text, size=10.5, bold=True, color=WHITE)
        else:
            set_run(p.add_run(), text, size=12,
                    bold=bold or (j == 0), color=color if color else (INK if j == 0 else MUTED))
for i, k in enumerate(['解长 6/6', '结点 6/6', 'IDA* 快 3×']):
    x = M + i * ((CW - 0.6) / 3 + 0.3)
    add_rect(s, x, 4.46, (CW - 0.6) / 3, 0.86, fill=WHITE, line=LINE,
             shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.06)
    tf = textbox(s, x, 4.46, (CW - 0.6) / 3, 0.86, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, k, size=15, bold=True, color=GREEN, align=PP_ALIGN.CENTER)
tf = textbox(s, M, 5.62, CW, 0.34)
para(tf, True, '不比时间　·　比跟机器无关的数', size=13, color=MUTED)
notes(s, 24, '我实现完 IDA* 之后做了对标。A* 四个实例，解出步数和搜过的状态数跟老师的参考程序完全一样。'
             'IDA* 九个实例步数也全对，总耗时 136 秒，参考 404 秒。'
             '我们特意不比时间，因为机器不一样，比的是解长和搜过的状态数。')

# ===== 08 模式数据库 =====
s = content(prs, '模式数据库', '林子航')
blocks = [('只看 5 块牌', ['576 万种', '11 MB']),
          ('代价划分', ['只算自己组的牌']),
          ('倒着建表', ['两趟扫描', '顺序不能反'])]
w = (CW - 2 * 0.30) / 3
for i, (nm, keys) in enumerate(blocks):
    l = M + i * (w + 0.30)
    kw_card(s, l, 1.52, w, 1.92, nm, keys, accent=BLUE if i != 1 else RED,
            tsize=16, ksize=13)
tf = textbox(s, M, 3.72, CW, 0.30)
para(tf, True, '一组表的开销', size=11, bold=True, color=MUTED)
for i, (v, lab) in enumerate([('576 万', '要遍历的摆法'), ('0.7 s', '建一次表'),
                              ('11 MB', '一组占多大'), ('33 MB', '三组共占')]):
    stat(s, M + i * (CW / 4), 4.10, CW / 4 - 0.3, v, lab,
         color=[BLUE, INK, GREEN, MUTED][i], vsize=44)
tf = textbox(s, M, 5.72, CW, 0.36)
para(tf, True, '三张表相加　·　仍然不超过真实步数　→　解还是最优', size=13.5, color=MUTED)
notes(s, 35, '模式数据库就是提前把答案算好存起来。只盯 5 块牌加空格，576 万种摆法，一组表 11 MB。'
             '关键是代价划分：三张表要相加，必须规定只给自己组的牌记步数，不然重复记账结果会偏大。'
             '建表是从目标倒着走，先走完不要钱的通道再往下走一层，顺序反了就会猜多。')

# ===== 09 效果 =====
s = content(prs, '效果', '林子航')
tf = textbox(s, M, 1.46, CW, 0.28)
para(tf, True, '阶段二 · 只换启发函数', size=11, bold=True, color=MUTED)
vbars(s, M, 1.92, CW, 1.90,
      [('实例 2', 24.5, '24.5×'), ('实例 3', 74.4, '74.4×'),
       ('实例 4', 105, '105×'), ('实例 5', 191, '191×')])
tf = textbox(s, M, 4.24, CW, 0.28)
para(tf, True, '阶段三 · 倒序盘面', size=11, bold=True, color=MUTED)
for i, (lab, val, frac, col, disp) in enumerate([
        ('IDA* + PDB', 828.25, 1.0, RED, '828 s'),
        ('组合启发', 369.35, 0.446, RGBColor(0xB4, 0x5F, 0x06), '369 s'),
        ('加权 w=1.2', 2.62, 0.032, GREEN, '2.62 s')]):
    hbar(s, M, 4.58 + i * 0.56, CW, 0.50, lab, disp, frac, col, lab_w=2.6)
tf = textbox(s, M, 6.36, CW, 0.36)
para(tf, True, '70 步最优解保留　·　72 步达标解', size=13.5, bold=True, color=MUTED)
notes(s, 32, '换上模式数据库，阶段二快 24 到 191 倍，还把第 6 个实例从跑不动变成 31 秒解出。'
             '阶段三那个倒序盘面：A* 内存爆了；IDA* 能拿到 70 步最优解但要 828 秒；'
             '换成组合启发降到 369 秒；最后把估算故意放大一点，2.62 秒解出 72 步，达标。'
             '70 步那份我们也没丢，两个结果都写进报告。')

# ===== 10 可视化 =====
s = content(prs, '可视化', '张文璟')
boards = [(START, '开始'), (MID, '中间'), (GOAL, '结束')]
bw = 2.45
gap = (CW - 3 * bw) / 2
for i, (cells, cap) in enumerate(boards):
    l = M + i * (bw + gap)
    board(s, l, 1.60, bw, cells, fsize=14)
    tf = textbox(s, l, 4.16, bw, 0.30)
    para(tf, True, cap, size=11.5, bold=True, color=MUTED, align=PP_ALIGN.CENTER)
for i in range(2):
    x = M + bw + i * (bw + gap) + 0.22
    add_rect(s, x, 2.62, gap - 0.44, 0.018, fill=RGBColor(0xC8, 0xD3, 0xE4))
keys = [('绿', '已归位'), ('蓝', '未到位'), ('琥珀', '刚移动')]
for i, (c, what) in enumerate(keys):
    x = M + i * 2.60
    add_rect(s, x, 4.86, 0.30, 0.30, fill=[GREEN, RGBColor(0x4A, 0x7C, 0xD8), RGBColor(0xD9, 0x77, 0x06)][i],
             shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.2)
    tf = textbox(s, x + 0.42, 4.86, 2.0, 0.30, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, what, size=12, color=MUTED)
tf = textbox(s, M, 5.40, CW, 0.28)
para(tf, True, '技术', size=10, bold=True, color=MUTED, spc=120)
techs = ['Swing', 'Graphics2D 抗锯齿', 'Timer 驱动', 'EDT 线程',
         '坐标自适应', '键盘绑定', '批量连播']
x = M
for t in techs:
    tw = sum(0.156 if ord(c) > 127 else 0.088 for c in t) + 0.38
    add_rect(s, x, 5.74, tw, 0.36, fill=WHITE, line=LINE,
             shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.5)
    tf = textbox(s, x, 5.74, tw, 0.36, anchor=MSO_ANCHOR.MIDDLE)
    para(tf, True, t, size=10.5, color=INK, align=PP_ALIGN.CENTER)
    x += tw + 0.14
tf = textbox(s, M, 6.30, CW, 0.34)
para(tf, True, '播放　·　暂停　·　单步　·　调速　·　连播', size=14, bold=True, color=INK)
notes(s, 26, '可视化我负责。这是同一个盘面动画里的三个瞬间：开始时几乎全是蓝色，'
             '中间绿色越来越多，最后全部归位。绿色表示这块牌已经到位，蓝色是没到位，'
             '刚移动的那一块会加一圈琥珀色描边。可以播放、暂停、单步、调速，也能批量连播。')

# ===== 11 结论 =====
s = content(prs, '结论', '张文璟')
data = [['阶段', '要求', '结果'],
        ['一', '3 阶 1 秒内', ('0.156 s', GREEN, True)],
        ['二', '4 阶同参考级', ('逐位一致', GREEN, True)],
        ['三', '倒序 1 分钟内', ('2.62 s', GREEN, True)],
        ['四', '解路径可视化', ('已验证', GREEN, True)]]
tbl = s.shapes.add_table(5, 3, Inches(M), Inches(1.52), Inches(CW), Inches(2.50)).table
tblPr = tbl._tbl.find(qn('a:tblPr'))
for el in tblPr.findall(qn('a:tableStyleId')):
    tblPr.remove(el)
el = tblPr.makeelement(qn('a:tableStyleId'), {})
el.text = NO_STYLE
tblPr.append(el)
tblPr.set('firstRow', '0')
tblPr.set('bandRow', '0')
for j, cw in enumerate([1.30, 4.10, 6.53]):
    tbl.columns[j].width = Inches(cw)
tbl.rows[0].height = Inches(0.46)
for i in range(1, 5):
    tbl.rows[i].height = Inches(0.50)
for i, row in enumerate(data):
    for j, raw in enumerate(row):
        color, bold = None, False
        if isinstance(raw, tuple):
            text, color, bold = raw
        else:
            text = raw
        c = tbl.cell(i, j)
        c.margin_left = Inches(0.16)
        c.vertical_anchor = MSO_ANCHOR.MIDDLE
        c.fill.solid()
        c.fill.fore_color.rgb = INK if i == 0 else (WHITE if i % 2 else SURF)
        p = c.text_frame.paragraphs[0]
        p.alignment = PP_ALIGN.CENTER if j != 1 else PP_ALIGN.LEFT
        p.line_spacing = 1.0
        if i == 0:
            set_run(p.add_run(), text, size=10.5, bold=True, color=WHITE)
        else:
            set_run(p.add_run(), text, size=12.5,
                    bold=bold or (j != 1), color=color if color else (INK if j == 0 else MUTED))
tf = textbox(s, M, 4.34, CW, 0.30)
para(tf, True, '两个不足', size=11, bold=True, color=RED, spc=120)
for i, k in enumerate(['单调性未证　·　理论上可能次优', '达标靠加权　·　不保证最短']):
    tf = textbox(s, M, 4.74 + i * 0.52, CW, 0.40)
    para(tf, True, k, size=13.5, color=INK)
tf = textbox(s, M, 5.86, CW, 0.34)
para(tf, True, '四个阶段全部达标', size=14, bold=True, color=MUTED)
notes(s, 22, '四个阶段都达标。不足有两个：模式数据库的单调性我们没证出来，理论上解可能不是最短的；'
             '阶段三能进 1 分钟是把估算放大换来的，不保证最短。这两点报告里都写了。')

# ===== 12 谢谢 =====
s = prs.slides.add_slide(prs.slide_layouts[6])
s.shapes.add_picture(BG_DARK, 0, 0, width=prs.slide_width, height=prs.slide_height)
tf = textbox(s, 1.10, 2.10, SW - 2.2, 0.90)
para(tf, True, '谢谢', size=52, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
add_rect(s, SW / 2 - 0.50, 3.14, 1.00, 0.05, fill=RGBColor(0x22, 0xD3, 0xEE))
tf = textbox(s, 1.10, 3.44, SW - 2.2, 0.40)
para(tf, True, '四个阶段全部达标　·　倒序盘面 2.62 秒', size=15, color=SKY, align=PP_ALIGN.CENTER)
board(s, SW / 2 - 1.15, 4.30, 2.30, GOAL, fsize=11)
tf = textbox(s, 1.10, 6.74, SW - 2.2, 0.30)
para(tf, True, '周以撒　李行　林子航　张文璟', size=9.5, color=DIM, align=PP_ALIGN.CENTER)
notes(s, 8, '我们的汇报到这里，谢谢老师。')

prs.core_properties.title = 'N-Puzzle 问题求解 · 项目演示（四人分工版）'
prs.core_properties.author = '周以撒 李行 林子航 张文璟'
OUT = sys.argv[1] if len(sys.argv) > 1 else 'N-Puzzle项目演示-分工版.pptx'
prs.save(OUT)
print('saved:', OUT, 'slides =', len(prs.slides._sldIdLst))
