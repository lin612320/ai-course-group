# -*- coding: utf-8 -*-
"""md -> docx（模板版）：套用课程模板封面、Word 原生公式、可点击目录。

用法：py -3 md2docx2.py <input.md> <output.docx>
依赖：模板 05-项目1-研究报告模板.docx 位于上一级目录。
"""
import sys, os, re, copy
from xml.sax.saxutils import escape as xesc

from docx import Document
from docx.shared import Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_TAB_ALIGNMENT
from docx.enum.style import WD_STYLE_TYPE
from docx.oxml.ns import qn, nsdecls
from docx.oxml import parse_xml, OxmlElement

HERE = os.path.dirname(os.path.abspath(__file__))
TEMPLATE = os.path.join(HERE, '..', '05-项目1-研究报告模板.docx')

SONG = '\u5b8b\u4f53'
MNS = 'xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"'
MFONT = 'Cambria Math'
BODY_PT = 10.5

MEMBERS = ['林子航', '李行', '张文璟', '周以撒']
TERM = '2026 年秋季学期'

# =====================================================================
#  一、LaTeX 子集 -> OMML
# =====================================================================
CMD_CHAR = {
    'le': '\u2264', 'ge': '\u2265', 'ne': '\u2260', 'approx': '\u2248',
    'times': '\u00d7', 'cdot': '\u00b7', 'to': '\u2192', 'rightarrow': '\u2192',
    'cdots': '\u22ef', 'dots': '\u2026', 'ldots': '\u2026',
    'equiv': '\u2261', 'iff': '\u21d4', 'Rightarrow': '\u21d2', 'll': '\u226a',
    'Delta': '\u0394', 'Sigma': '\u03a3', 'sum': '\u2211', 'prod': '\u220f',
    'sim': '~', '{': '{', '}': '}', '_': '_', '%': '%', '&': '&', '#': '#',
    'quad': '\u2003', 'qquad': '\u2003\u2003',
    ',': '\u2009', ';': '\u2005', ':': '\u2005', ' ': ' ', '\\': ' ',
    'big': '', 'Big': '', 'bigg': '', 'Bigg': '',
    'bigl': '', 'bigr': '', 'Bigl': '', 'Bigr': '',
    'left': '', 'right': '',
}
UPRIGHT_CMD = {'max', 'min', 'log', 'exp'}
DROP_CMD = {'displaystyle', 'limits', 'nolimits'}


def tok(s):
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == '\\':
            j = i + 1
            if j >= len(s):
                break
            if s[j].isalpha():
                k = j
                while k < len(s) and s[k].isalpha():
                    k += 1
                out.append(('cmd', s[j:k]))
                i = k
            else:
                out.append(('cmd', s[j]))
                i = j + 1
        elif c == '{':
            out.append(('open',))
            i += 1
        elif c == '}':
            out.append(('close',))
            i += 1
        elif c in '_^':
            out.append((c,))
            i += 1
        else:
            out.append(('ch', c))
            i += 1
    return out


def mk_run(text, upright=False):
    if not text:
        return ''
    sty = '<m:rPr><m:sty m:val="p"/></m:rPr>' if upright else ''
    return ('<m:r>' + sty +
            '<w:rPr><w:rFonts w:ascii="%s" w:hAnsi="%s"/></w:rPr>'
            '<m:t xml:space="preserve">%s</m:t></m:r>') % (MFONT, MFONT, xesc(text))


def is_italic_char(c):
    o = ord(c)
    return (0x41 <= o <= 0x5A) or (0x61 <= o <= 0x7A) or (0x370 <= o <= 0x3FF)


class Cur:
    """累积一串 item，相邻同风格字符合并成 run。"""
    def __init__(self):
        self.items = []

    def ch(self, c):
        up = not is_italic_char(c)
        if self.items and self.items[-1]['k'] == 'r' and self.items[-1]['u'] == up:
            self.items[-1]['t'] += c
        else:
            self.items.append({'k': 'r', 'u': up, 't': c})

    def text(self, t, upright=True):
        if not t:
            return
        if self.items and self.items[-1]['k'] == 'r' and self.items[-1]['u'] == upright:
            self.items[-1]['t'] += t
        else:
            self.items.append({'k': 'r', 'u': upright, 't': t})

    def node(self, k, **kw):
        d = {'k': k}
        d.update(kw)
        self.items.append(d)

    def xml(self):
        out = []
        for it in self.items:
            if it['k'] == 'r':
                out.append(mk_run(it['t'], it['u']))
            else:
                out.append(it['x'])
        return ''.join(out)


def wrap_sub(e, sub):
    return '<m:sSub><m:sSubPr/><m:e>%s</m:e><m:sub>%s</m:sub></m:sSub>' % (e, sub)


def wrap_sup(e, sup):
    return '<m:sSup><m:sSupPr/><m:e>%s</m:e><m:sup>%s</m:sup></m:sSup>' % (e, sup)


def wrap_subsup(e, sub, sup):
    return ('<m:sSubSup><m:sSubSupPr/><m:e>%s</m:e><m:sub>%s</m:sub>'
            '<m:sup>%s</m:sup></m:sSubSup>') % (e, sub, sup)


def read_raw_group(ts, i):
    """ts[i] 应为 open；返回 (组内原文, 结束后位置)。"""
    assert ts[i][0] == 'open'
    depth, i, buf = 1, i + 1, []
    while i < len(ts) and depth:
        t = ts[i]
        if t[0] == 'open':
            depth += 1
            buf.append('{')
        elif t[0] == 'close':
            depth -= 1
            if depth:
                buf.append('}')
        elif t[0] == 'cmd':
            buf.append(t[1] if len(t[1]) > 1 else '')
        else:
            buf.append(t[1] if len(t) > 1 else t[0])
        i += 1
    return ''.join(buf), i


def parse(ts, i, stop_close=False):
    """返回 (Cur, 结束位置)。"""
    cur = Cur()
    while i < len(ts):
        t = ts[i]
        if t[0] == 'close':
            if stop_close:
                return cur, i + 1
            i += 1
            continue
        if t[0] == 'open':
            sub, i = parse(ts, i + 1, True)
            for it in sub.items:
                cur.items.append(it)
            continue
        if t[0] == 'cmd':
            name = t[1]
            if name == 'text':
                if i + 1 < len(ts) and ts[i + 1][0] == 'open':
                    raw, i = read_raw_group(ts, i + 1)
                    cur.text(raw, True)
                else:
                    i += 1
                continue
            if name == 'tag':
                if i + 1 < len(ts) and ts[i + 1][0] == 'open':
                    _, i = read_raw_group(ts, i + 1)
                else:
                    i += 1
                continue
            if name in DROP_CMD:
                i += 1
                continue
            if name in ('mathrm', 'operatorname', 'mathcal'):
                if i + 1 < len(ts) and ts[i + 1][0] == 'open':
                    raw, i = read_raw_group(ts, i + 1)
                    cur.text(raw, True)
                else:
                    i += 1
                continue
            if name in CMD_CHAR:
                for c in CMD_CHAR[name]:
                    cur.ch(c)
                i += 1
                continue
            if name in UPRIGHT_CMD:
                for c in name:
                    cur.text(c, True)
                i += 1
                continue
            # 未知命令：原样输出字母
            for c in name:
                cur.ch(c)
            i += 1
            continue
        if t[0] == 'ch':
            cur.ch(t[1])
            i += 1
            continue
        # _ 或 ^
        if not cur.items:
            i += 1
            continue
        base = cur.items.pop()
        add, i = _attach(base, ts, i)
        cur.items.extend(add)
    return cur, i


def _operand(ts, i):
    """i 指向 _/^ 之后。返回 (Cur, 结束后位置)。"""
    if i < len(ts) and ts[i][0] == 'open':
        return parse(ts, i + 1, True)
    if i < len(ts):
        t = ts[i]
        if t[0] == 'cmd':
            name = t[1]
            if name == 'text' and i + 1 < len(ts) and ts[i + 1][0] == 'open':
                raw, j = read_raw_group(ts, i + 1)
                c = Cur()
                c.text(raw, True)
                return c, j
            c = Cur()
            for ch in CMD_CHAR.get(name, name):
                c.ch(ch)
            return c, i + 1
        if t[0] in ('close', '_', '^'):
            return Cur(), i
        c = Cur()
        c.ch(t[1])
        return c, i + 1
    return Cur(), i


def _attach(base, ts, i):
    """base 后面紧跟 ts[i]（_ 或 ^）；返回 (要追加的 item 列表, 结束后位置)。"""
    op = ts[i][0]
    operand, j = _operand(ts, i + 1)
    other = None
    if j < len(ts) and ts[j][0] in ('_', '^') and ts[j][0] != op:
        other, j = _operand(ts, j + 1)
    base_xml = base if base['k'] != 'r' else mk_run(base['t'], base['u'])
    if other is not None:
        if op == '_':
            x = wrap_subsup(base_xml, operand.xml(), other.xml())
        else:
            x = wrap_subsup(base_xml, other.xml(), operand.xml())
        return [{'k': 'x', 'x': x}], j
    if op == '_':
        return [{'k': 'x', 'x': wrap_sub(base_xml, operand.xml())}], j
    return [{'k': 'x', 'x': wrap_sup(base_xml, operand.xml())}], j


def latex_to_omml(s):
    ts = tok(s)
    cur, _ = parse(ts, 0)
    inner = cur.xml()
    if not inner:
        inner = mk_run(' ', True)
    return parse_xml('<m:oMath %s %s>%s</m:oMath>' % (MNS, nsdecls('w'), inner))


# =====================================================================
#  二、段落级工具
# =====================================================================
def set_east_asia(run, name=SONG):
    rPr = run._element.get_or_add_rPr()
    rf = rPr.find(qn('w:rFonts'))
    if rf is None:
        rf = OxmlElement('w:rFonts')
        rPr.insert(0, rf)
    rf.set(qn('w:eastAsia'), name)


def add_text_run(par, text, size=None, bold=False, italic=False, mono=False):
    r = par.add_run(text)
    r.bold = bold
    r.italic = italic
    if mono:
        r.font.name = 'Consolas'
        r.font.size = Pt(9)
    else:
        set_east_asia(r)
        if size:
            r.font.size = Pt(size)
    return r


INLINE = re.compile(r'\$[^$]+\$|\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*')


def _plain(par, text, size=None, bold=False, italic=False):
    if not text:
        return
    add_text_run(par, text.replace('\x01', '*'), size, bold=bold, italic=italic)


def add_rich(par, text, size=None, bold=False, italic=False):
    """行内标记：$公式$ → Word 原生公式；**粗**、`代码`、*斜* 可任意嵌套。"""
    text = text.replace('\\*', '\x01')
    pos = 0
    for m in INLINE.finditer(text):
        if m.start() > pos:
            _plain(par, text[pos:m.start()], size, bold, italic)
        t = m.group(0)
        if t.startswith('$'):
            body = t[1:-1]
            if body.strip():
                par._p.append(latex_to_omml(body))
        elif t.startswith('**'):
            add_rich(par, t[2:-2], size, True, italic)
        elif t.startswith('`'):
            r = par.add_run(t[1:-1].replace('\x01', '*'))
            r.font.name = 'Consolas'
            r.font.size = Pt(9)
            r.bold = bold
        else:
            add_rich(par, t[1:-1], size, bold, True)
        pos = m.end()
    if pos < len(text):
        _plain(par, text[pos:], size, bold, italic)


# =====================================================================
#  三、模板处理
# =====================================================================
def ensure_style(doc, name, base, size=None, bold=None):
    try:
        return doc.styles[name]
    except KeyError:
        pass
    st = doc.styles.add_style(name, WD_STYLE_TYPE.PARAGRAPH)
    try:
        st.base_style = doc.styles[base]
    except KeyError:
        pass
    if size:
        st.font.size = Pt(size)
    if bold is not None:
        st.font.bold = bold
    return st


def set_outline(style, lvl):
    pPr = style.element.get_or_add_pPr()
    for e in pPr.findall(qn('w:outlineLvl')):
        pPr.remove(e)
    e = OxmlElement('w:outlineLvl')
    e.set(qn('w:val'), str(lvl))
    pPr.append(e)


def fill_cover(doc):
    """把模板封面的学期与姓名补上；姓名不够 4 行就照原样式复制。"""
    paras = doc.paragraphs
    term_p = None
    name_ps = []
    for p in paras:
        if '季学期' in p.text:
            term_p = p
        if '姓名' in p.text and '学号' in p.text:
            name_ps.append(p)
    if term_p is not None:
        for r in list(term_p.runs)[1:]:
            r._element.getparent().remove(r._element)
        r0 = term_p.runs[0] if term_p.runs else term_p.add_run()
        r0.text = TERM
        r0.bold = True
        r0.font.size = Pt(12)
        set_east_asia(r0)
    if len(name_ps) >= 1:
        proto = name_ps[-1]
        cursor = proto._p
        while len(name_ps) < len(MEMBERS):
            newp = copy.deepcopy(proto._p)
            cursor.addnext(newp)
            cursor = newp
            name_ps.append(proto.__class__(newp, proto._parent))
        for p, nm in zip(name_ps, MEMBERS):
            for r in list(p.runs)[1:]:
                r._element.getparent().remove(r._element)
            r0 = p.runs[0] if p.runs else p.add_run()
            r0.text = '姓名：%s　　学号：' % nm
            r0.bold = False
            r0.font.size = Pt(12)
            set_east_asia(r0)


def set_update_fields(doc):
    settings = doc.settings.element
    for e in settings.findall(qn('w:updateFields')):
        settings.remove(e)
    e = OxmlElement('w:updateFields')
    e.set(qn('w:val'), 'true')
    settings.append(e)


# =====================================================================
#  四、主流程
# =====================================================================
def main(src, dst):
    lines = open(src, encoding='utf-8').read().split('\n')

    doc = Document(TEMPLATE)
    ensure_style(doc, 'Heading 3', 'Heading 2', 12, True)
    ensure_style(doc, 'Heading 4', 'Heading 3', 11, True)
    for lvl in (0, 1, 2, 3):
        try:
            set_outline(doc.styles['Heading %d' % (lvl + 1)], lvl)
        except KeyError:
            pass
    fill_cover(doc)
    set_update_fields(doc)
    doc.core_properties.title = 'N-Puzzle 问题求解研究报告'

    # 清空模板正文里 TOC 之后的空段落
    body = doc.element.body
    sdt = body.find(qn('w:sdt'))
    if sdt is not None:
        nxt = sdt.getnext()
        while nxt is not None and nxt.tag == qn('w:p') and not ''.join(
                t.text or '' for t in nxt.iter(qn('w:t'))).strip():
            gone = nxt
            nxt = nxt.getnext()
            body.remove(gone)

    # 正文从“# 1.”开始（前面的封面与手工目录由模板接管）
    start = 0
    for i, ln in enumerate(lines):
        if re.match(r'^#\s+1[\.、]', ln.strip()):
            start = i
            break

    i = start
    first_block = True
    while i < len(lines):
        ln = lines[i]
        s = ln.strip()

        # 代码块
        if s.startswith('```'):
            i += 1
            buf = []
            while i < len(lines) and not lines[i].strip().startswith('```'):
                buf.append(lines[i])
                i += 1
            i += 1
            p = doc.add_paragraph()
            p.paragraph_format.left_indent = Pt(18)
            p.paragraph_format.space_after = Pt(6)
            r = p.add_run('\n'.join(buf))
            r.font.name = 'Consolas'
            r.font.size = Pt(9)
            continue

        # 独立公式 $$...$$
        if s == '$$':
            j = i + 1
            buf = []
            while j < len(lines) and lines[j].strip() != '$$':
                buf.append(lines[j])
                j += 1
            latex = ' '.join(x.strip() for x in buf)
            tag = None
            m = re.search(r'\\tag\{([^}]*)\}', latex)
            if m:
                tag = m.group(1)
                latex = latex[:m.start()] + latex[m.end():]
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.space_before = Pt(6)
            p.paragraph_format.space_after = Pt(6)
            p._p.append(latex_to_omml(latex))
            if tag:
                r = p.add_run('\t(%s)' % tag)
                set_east_asia(r)
                p.paragraph_format.tab_stops.add_tab_stop(
                    Pt(430), WD_TAB_ALIGNMENT.RIGHT)
            i = j + 1
            continue

        # 表格
        if s.startswith('|') and i + 1 < len(lines) and re.match(
                r'^\s*\|[\s:|-]+\|\s*$', lines[i + 1]):
            hdr = [c.strip() for c in s.strip('|').split('|')]
            i += 2
            rows = []
            while i < len(lines) and lines[i].strip().startswith('|'):
                rows.append([c.strip() for c in lines[i].strip().strip('|').split('|')])
                i += 1
            t = doc.add_table(rows=1, cols=len(hdr))
            try:
                t.style = 'Table Grid'
            except KeyError:
                pass
            for j, h in enumerate(hdr):
                c = t.rows[0].cells[j]
                c.text = ''
                add_rich(c.paragraphs[0], h)
                for r in c.paragraphs[0].runs:
                    r.bold = True
                    r.font.size = Pt(9)
            for row in rows:
                cells = t.add_row().cells
                for j in range(min(len(row), len(hdr))):
                    cells[j].text = ''
                    add_rich(cells[j].paragraphs[0], row[j])
                    for r in cells[j].paragraphs[0].runs:
                        r.font.size = Pt(9)
            doc.add_paragraph()
            continue

        if not s or s == '---':
            i += 1
            continue

        # 标题
        m = re.match(r'^(#{1,4})\s+(.*)$', s)
        if m:
            lvl = len(m.group(1))
            txt = m.group(2).replace('\\*', '*')
            h = doc.add_heading('', level=min(lvl, 4))
            add_rich(h, txt)
            if lvl == 1 and first_block:
                first_block = False
            i += 1
            continue

        # 引用块
        if s.startswith('>'):
            txt = s.lstrip('>').strip()
            p = doc.add_paragraph()
            p.paragraph_format.left_indent = Pt(18)
            add_rich(p, txt)
            for r in p.runs:
                r.italic = True
            i += 1
            continue

        # 列表（课程模板没有 List Bullet / List Number 样式，这里手工排版）
        m = re.match(r'^(\s*)[-*]\s+(.*)$', s)
        if m:
            ind = len(m.group(1))
            level = 0 if ind < 2 else (1 if ind < 4 else 2)
            p = doc.add_paragraph()
            pf = p.paragraph_format
            pf.left_indent = Pt(BODY_PT * 2 * (level + 1))
            pf.first_line_indent = Pt(-BODY_PT * 1.4)
            pf.space_after = Pt(2)
            add_rich(p, ('\u25aa  ' if level == 0 else '\u2013  ') + m.group(2))
            i += 1
            continue

        m = re.match(r'^(\s*)(\d+)\.\s+(.*)$', s)
        if m:
            num = m.group(2)
            p = doc.add_paragraph()
            pf = p.paragraph_format
            pf.left_indent = Pt(BODY_PT * 2)
            pf.first_line_indent = Pt(-BODY_PT * 1.4)
            pf.space_after = Pt(2)
            add_rich(p, '%s.  %s' % (num, m.group(3)))
            i += 1
            continue

        p = doc.add_paragraph()
        p.paragraph_format.first_line_indent = Pt(BODY_PT * 2)
        add_rich(p, s)
        i += 1

    doc.save(dst)
    print('saved:', dst)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
