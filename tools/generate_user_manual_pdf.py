#!/usr/bin/env python3
"""Render the community-edition Markdown user manual as a phone-friendly PDF."""

from __future__ import annotations

import argparse
import html
import re
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    HRFlowable,
    KeepTogether,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


INK = colors.HexColor("#172033")
MUTED = colors.HexColor("#5F6B7A")
BLUE = colors.HexColor("#2563EB")
PALE_BLUE = colors.HexColor("#EFF6FF")
PALE_AMBER = colors.HexColor("#FFF7E6")
LINE = colors.HexColor("#DCE3ED")
PAPER = colors.white


def register_fonts() -> tuple[str, str]:
    candidates = [
        Path("/System/Library/Fonts/STHeiti Light.ttc"),
        Path("/System/Library/Fonts/Supplemental/Songti.ttc"),
    ]
    font_path = next((path for path in candidates if path.exists()), None)
    if font_path is None:
        raise FileNotFoundError("没有找到可嵌入的中文字体")
    pdfmetrics.registerFont(TTFont("ManualCJK", str(font_path), subfontIndex=0))
    # The same family is used for bold text to keep every Chinese glyph embedded.
    pdfmetrics.registerFont(TTFont("ManualCJKBold", str(font_path), subfontIndex=0))
    return "ManualCJK", "ManualCJKBold"


def inline_markup(value: str) -> str:
    """Escape Markdown text and retain safe links and inline code."""
    placeholders: list[str] = []

    def stash(fragment: str) -> str:
        placeholders.append(fragment)
        return f"@@TOKEN{len(placeholders) - 1}@@"

    def link(match: re.Match[str]) -> str:
        label = html.escape(match.group(1))
        href = html.escape(match.group(2), quote=True)
        return stash(f'<link href="{href}" color="#2563EB"><u>{label}</u></link>')

    def code(match: re.Match[str]) -> str:
        return stash(f'<font color="#B45309">{html.escape(match.group(1))}</font>')

    value = re.sub(r"\[([^\]]+)\]\((https?://[^)]+)\)", link, value)
    value = re.sub(r"`([^`]+)`", code, value)
    value = html.escape(value)
    for index, fragment in enumerate(placeholders):
        value = value.replace(f"@@TOKEN{index}@@", fragment)
    return value


def styles(font: str, bold: str) -> dict[str, ParagraphStyle]:
    base = getSampleStyleSheet()
    return {
        "cover_title": ParagraphStyle(
            "CoverTitle",
            parent=base["Title"],
            fontName=bold,
            fontSize=27,
            leading=37,
            textColor=INK,
            alignment=TA_LEFT,
            wordWrap="CJK",
            spaceAfter=8 * mm,
        ),
        "cover_subtitle": ParagraphStyle(
            "CoverSubtitle",
            parent=base["BodyText"],
            fontName=font,
            fontSize=13,
            leading=22,
            textColor=MUTED,
            wordWrap="CJK",
        ),
        "h1": ParagraphStyle(
            "H1",
            parent=base["Heading1"],
            fontName=bold,
            fontSize=18,
            leading=26,
            textColor=INK,
            wordWrap="CJK",
            spaceBefore=7 * mm,
            spaceAfter=3.5 * mm,
            keepWithNext=True,
        ),
        "h2": ParagraphStyle(
            "H2",
            parent=base["Heading2"],
            fontName=bold,
            fontSize=13.5,
            leading=21,
            textColor=BLUE,
            wordWrap="CJK",
            spaceBefore=4.5 * mm,
            spaceAfter=2.5 * mm,
            keepWithNext=True,
        ),
        "body": ParagraphStyle(
            "Body",
            parent=base["BodyText"],
            fontName=font,
            fontSize=10.5,
            leading=18,
            textColor=INK,
            wordWrap="CJK",
            spaceAfter=2.7 * mm,
        ),
        "bullet": ParagraphStyle(
            "Bullet",
            parent=base["BodyText"],
            fontName=font,
            fontSize=10.5,
            leading=18,
            leftIndent=6 * mm,
            firstLineIndent=-4.5 * mm,
            textColor=INK,
            wordWrap="CJK",
            spaceAfter=1.7 * mm,
        ),
        "quote": ParagraphStyle(
            "Quote",
            parent=base["BodyText"],
            fontName=font,
            fontSize=10.5,
            leading=18,
            leftIndent=4 * mm,
            rightIndent=4 * mm,
            textColor=colors.HexColor("#8A4B08"),
            backColor=PALE_AMBER,
            borderColor=colors.HexColor("#F4C56A"),
            borderWidth=0.6,
            borderPadding=8,
            wordWrap="CJK",
            spaceAfter=4 * mm,
        ),
        "code": ParagraphStyle(
            "Code",
            parent=base["Code"],
            fontName=font,
            fontSize=8.8,
            leading=15,
            leftIndent=3 * mm,
            rightIndent=3 * mm,
            textColor=colors.HexColor("#1E3A5F"),
            backColor=PALE_BLUE,
            borderColor=colors.HexColor("#BFDBFE"),
            borderWidth=0.5,
            borderPadding=8,
            wordWrap="CJK",
            spaceAfter=4 * mm,
        ),
        "small": ParagraphStyle(
            "Small",
            parent=base["BodyText"],
            fontName=font,
            fontSize=8.5,
            leading=14,
            textColor=MUTED,
            wordWrap="CJK",
        ),
    }


def cover(sty: dict[str, ParagraphStyle]) -> list:
    status = Table(
        [[Paragraph("开发测试版 · 0.2.0", sty["small"])],
         [Paragraph("适用：仅使用 Android 手机的用户", sty["body"])]],
        colWidths=[158 * mm],
    )
    status.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), PALE_BLUE),
        ("BOX", (0, 0), (-1, -1), 0.7, colors.HexColor("#BFDBFE")),
        ("LINEBELOW", (0, 0), (-1, 0), 0.5, colors.HexColor("#D7E7FF")),
        ("LEFTPADDING", (0, 0), (-1, -1), 12),
        ("RIGHTPADDING", (0, 0), (-1, -1), 12),
        ("TOPPADDING", (0, 0), (-1, -1), 9),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 9),
    ]))
    return [
        Spacer(1, 18 * mm),
        HRFlowable(width=35 * mm, thickness=4, color=BLUE, hAlign="LEFT"),
        Spacer(1, 10 * mm),
        Paragraph("Pocket Studio", sty["cover_title"]),
        Paragraph("用户手册", sty["cover_title"]),
        Paragraph(
            "今日头条固定版的视频筛选、官方链接解析、下载、标题辅助与本地裁剪工具。",
            sty["cover_subtitle"],
        ),
        Spacer(1, 17 * mm),
        status,
        Spacer(1, 18 * mm),
        Paragraph(
            "安全边界：完全免费，不需要卡密；用户的模型密钥仅加密保存在本机；只处理自有或已获授权的视频；最终发布由用户本人确认。",
            sty["quote"],
        ),
        Spacer(1, 28 * mm),
        Paragraph("文档版本：2026-08-26", sty["small"]),
        PageBreak(),
    ]


def markdown_story(markdown: str, sty: dict[str, ParagraphStyle]) -> list:
    lines = markdown.splitlines()
    story: list = []
    paragraph: list[str] = []
    in_code = False
    code_lines: list[str] = []

    def flush_paragraph() -> None:
        if paragraph:
            story.append(Paragraph(inline_markup(" ".join(paragraph)), sty["body"]))
            paragraph.clear()

    for raw in lines:
        line = raw.rstrip()
        if line.startswith("```"):
            flush_paragraph()
            if in_code:
                code_text = "<br/>".join(html.escape(item).replace(" ", "&nbsp;") for item in code_lines)
                story.append(Paragraph(code_text, sty["code"]))
                code_lines.clear()
                in_code = False
            else:
                in_code = True
            continue
        if in_code:
            code_lines.append(line)
            continue
        if not line.strip():
            flush_paragraph()
            continue
        if line.startswith("# "):
            # The Markdown H1 is represented by the designed cover.
            flush_paragraph()
            continue
        if line.startswith("## "):
            flush_paragraph()
            story.append(Paragraph(inline_markup(line[3:]), sty["h1"]))
            continue
        if line.startswith("### "):
            flush_paragraph()
            story.append(Paragraph(inline_markup(line[4:]), sty["h2"]))
            continue
        if line.startswith("> "):
            flush_paragraph()
            story.append(Paragraph(inline_markup(line[2:]), sty["quote"]))
            continue
        numbered = re.match(r"^(\d+)\.\s+(.*)$", line)
        if numbered:
            flush_paragraph()
            story.append(Paragraph(
                f'<font color="#2563EB"><b>{numbered.group(1)}.</b></font> {inline_markup(numbered.group(2))}',
                sty["bullet"],
            ))
            continue
        if line.startswith("- "):
            flush_paragraph()
            story.append(Paragraph(f'<font color="#2563EB">●</font> {inline_markup(line[2:])}', sty["bullet"]))
            continue
        paragraph.append(line.strip())
    flush_paragraph()
    return story


def decorate_page(canvas, document) -> None:
    canvas.saveState()
    page = canvas.getPageNumber()
    width, height = A4
    if page > 1:
        canvas.setStrokeColor(LINE)
        canvas.setLineWidth(0.5)
        canvas.line(22 * mm, height - 14 * mm, width - 22 * mm, height - 14 * mm)
        canvas.setFont("ManualCJK", 8)
        canvas.setFillColor(MUTED)
        canvas.drawString(22 * mm, height - 10.5 * mm, "Pocket Studio · 用户手册")
        canvas.drawRightString(width - 22 * mm, 12 * mm, f"第 {page} 页")
    canvas.restoreState()


def build(source: Path, output: Path) -> None:
    font, bold = register_fonts()
    sty = styles(font, bold)
    output.parent.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(
        str(output),
        pagesize=A4,
        rightMargin=22 * mm,
        leftMargin=22 * mm,
        topMargin=20 * mm,
        bottomMargin=20 * mm,
        title="Pocket Studio 用户手册（开发测试版）",
        author="Pocket Studio",
        subject="Android 手机端安装与使用说明",
    )
    manual = source.read_text(encoding="utf-8")
    story = cover(sty)
    story.extend(markdown_story(manual, sty))
    story.append(Spacer(1, 5 * mm))
    story.append(KeepTogether([
        HRFlowable(width="100%", thickness=0.6, color=LINE),
        Spacer(1, 3 * mm),
        Paragraph("当前为测试版。完成真机全流程验证后，版本号、APK 签名和本手册会同步更新。", sty["small"]),
    ]))
    doc.build(story, onFirstPage=decorate_page, onLaterPages=decorate_page)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    build(args.source, args.output)


if __name__ == "__main__":
    main()
