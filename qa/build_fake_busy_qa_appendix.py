#!/usr/bin/env python3
"""Append tester-ready false-busy incoming-call regression cases to the HIMA QA SOP."""

from __future__ import annotations

import argparse
from pathlib import Path

from pypdf import PdfReader, PdfWriter
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    KeepTogether,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)


GREEN = colors.HexColor("#1B5E3F")
DARK_GREEN = colors.HexColor("#14342A")
LIGHT_GREEN = colors.HexColor("#EEF6F0")
TEXT = colors.HexColor("#1F2937")
MUTED = colors.HexColor("#6B7280")
BORDER = colors.HexColor("#D9E3DD")
RED = colors.HexColor("#B42318")


def page_decor(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(BORDER)
    canvas.line(15 * mm, 13 * mm, A4[0] - 15 * mm, 13 * mm)
    canvas.setFont("Helvetica", 7.5)
    canvas.setFillColor(MUTED)
    canvas.drawString(
        15 * mm,
        8.5 * mm,
        "Hima QA Release Testing SOP - False-busy call teardown - Rev 2026-07-23",
    )
    canvas.drawRightString(
        A4[0] - 15 * mm,
        8.5 * mm,
        f"Appendix page {doc.page}",
    )
    canvas.restoreState()


def build_appendix(path: Path) -> None:
    styles = getSampleStyleSheet()
    title = ParagraphStyle(
        "Title",
        parent=styles["Title"],
        fontName="Helvetica-Bold",
        fontSize=18,
        leading=22,
        alignment=TA_CENTER,
        textColor=DARK_GREEN,
        spaceAfter=3 * mm,
    )
    subtitle = ParagraphStyle(
        "Subtitle",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=9.1,
        leading=11.7,
        alignment=TA_CENTER,
        textColor=MUTED,
        spaceAfter=4 * mm,
    )
    case_title = ParagraphStyle(
        "CaseTitle",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=9.8,
        leading=11.8,
        textColor=DARK_GREEN,
    )
    body = ParagraphStyle(
        "Body",
        parent=styles["BodyText"],
        fontName="Helvetica",
        fontSize=7.8,
        leading=9.7,
        textColor=TEXT,
    )
    label = ParagraphStyle(
        "Label",
        parent=body,
        fontName="Helvetica-Bold",
        textColor=DARK_GREEN,
    )
    gate = ParagraphStyle(
        "Gate",
        parent=body,
        fontName="Helvetica-Bold",
        textColor=RED,
        alignment=TA_CENTER,
    )

    doc = BaseDocTemplate(
        str(path),
        pagesize=A4,
        rightMargin=15 * mm,
        leftMargin=15 * mm,
        topMargin=14 * mm,
        bottomMargin=17 * mm,
        title="Hima QA False-busy Call Teardown Appendix",
        author="Innovfix Pvt. Ltd.",
    )
    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="normal")
    doc.addPageTemplates([PageTemplate(id="qa", frames=frame, onPage=page_decor)])

    story = [
        Paragraph("RELEASE TEST APPENDIX FBUSY", gate),
        Spacer(1, 2 * mm),
        Paragraph("Creator Must Not Remain Falsely Busy", title),
        Paragraph(
            "Android closes the exact unaccepted ring when its task is swiped away, "
            "releases stale Activity and Telecom ownership, and durably closes a "
            "row when a genuinely concurrent ring is rejected. Accepted and newer "
            "calls are protected by exact sender and call-ID guards.",
            subtitle,
        ),
    ]
    gate_box = Table(
        [[
            Paragraph("Release gate", label),
            Paragraph(
                "FBUSY-001 through FBUSY-005 are MUST-PASS on production-flavor "
                "builds before APK rollout. Test audio and video in both directions "
                "on at least one affected OEM device. Confirm normal calls and "
                "billing are unchanged.",
                body,
            ),
        ]],
        colWidths=[32 * mm, 128 * mm],
    )
    gate_box.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), LIGHT_GREEN),
        ("BOX", (0, 0), (-1, -1), 0.8, GREEN),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]))
    story.extend([gate_box, Spacer(1, 5 * mm)])

    cases = [
        (
            "FBUSY-001",
            "Original swipe-away then immediate redial",
            "MUST-PASS / P0",
            "Production-flavor APK containing the fix; creator availability enabled; caller and creator test accounts; affected OEM device when available.",
            "Audio and video; FCM delivery and OneSignal fallback delivery; app foreground, background and killed.",
            "1. Caller starts a direct call. 2. While creator is ringing, swipe Hima away from recents. 3. Within 1-3 seconds call the same creator again. 4. Repeat each delivery and lifecycle combination. 5. Inspect redacted call state.",
            "The first ring, notification and Telecom entry end. Its row becomes terminal. The second call rings the same free creator; caller does not receive false userBusy/unavailable and is not redirected to random matching.",
        ),
        (
            "FBUSY-002",
            "Accepted call is never torn down by task removal",
            "MUST-PASS / P0",
            "Production-flavor APK on two devices; valid accounts and sufficient caller balance.",
            "Accept from full-screen UI and notification Answer; audio/video; task swipe immediately after accept and after Agora joins.",
            "1. Start and accept the call. 2. Swipe Hima from recents at each timing boundary. 3. Observe call status, Telecom and server row. 4. End normally. 5. Verify duration, debit and creator income once.",
            "The accepted call is not converted to rejected/not_answered by the ring backstop. No matching live Telecom connection is destroyed. Normal connected-call teardown and single billing remain unchanged.",
        ),
        (
            "FBUSY-003",
            "Old callback cannot clear a newer ring",
            "MUST-PASS / P0",
            "QA build capable of closely spaced calls from two callers; creator availability enabled.",
            "Caller A ring followed by caller B ring; delayed old service callback; different sender and different call ID.",
            "1. Start caller A ring. 2. End A and immediately deliver caller B ring. 3. Trigger or observe A's delayed task-removal/service teardown. 4. Accept or decline B. 5. Repeat 30 times.",
            "The stale A callback fails its exact identity claim and does not stop B's ringtone, notification, Activity or Telecom entry. B remains actionable and receives the correct terminal state.",
        ),
        (
            "FBUSY-004",
            "Real concurrent busy rejection closes its own row",
            "MUST-PASS / P0",
            "Creator already in a real connected call or a valid current ring; a second caller starts another call; backend terminal compare-and-set guard enabled.",
            "Online delivery, network loss during status post and recovery; duplicate worker enqueue; audio/video and both recipient genders.",
            "1. Keep call A legitimately active. 2. Start call B to the same user. 3. Confirm B receives userBusy. 4. Interrupt recipient network before the terminal post, then restore it. 5. Inspect redacted B state and retry behavior.",
            "Call A is untouched. Call B alone becomes not_answered after inline delivery or bounded WorkManager retry. Duplicate delivery is idempotent; B does not remain open and cannot create a later false-busy state.",
        ),
        (
            "FBUSY-005",
            "Late retry, performance and normal-call regression",
            "MUST-PASS / P0",
            "Production-flavor unit suite passing; QA backend with terminal compare-and-set; WorkManager and API telemetry available.",
            "Terminal retry after a row has already started; 100 sequential swipe/redial cycles; normal accept/end, decline and timeout calls.",
            "1. Delay a not_answered retry until after the target row is marked started in QA. 2. Send the retry. 3. Run repeated swipe/redial cycles. 4. Run nearby normal flows. 5. Compare latency, worker count, API errors and billing with baseline.",
            "The backend guard ignores a late ring-terminal write on a started call. Only real busy rejections add one unique bounded worker. There is no main-thread network work, worker duplication, material call latency, call loss, revenue change or billing duplication.",
        ),
    ]

    for index, (case_id, name, priority, pre, data, steps, expected) in enumerate(cases):
        heading = Table(
            [[Paragraph(f"{case_id} - {name}", case_title), Paragraph(priority, gate)]],
            colWidths=[122 * mm, 38 * mm],
        )
        heading.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, -1), LIGHT_GREEN),
            ("BOX", (0, 0), (-1, -1), 0.7, BORDER),
            ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ("LEFTPADDING", (0, 0), (-1, -1), 6),
            ("RIGHTPADDING", (0, 0), (-1, -1), 6),
            ("TOPPADDING", (0, 0), (-1, -1), 5),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ]))
        details = Table(
            [
                [Paragraph("Preconditions / build / role", label), Paragraph(pre, body)],
                [Paragraph("Test data", label), Paragraph(data, body)],
                [Paragraph("Numbered steps", label), Paragraph(steps, body)],
                [Paragraph("Expected result", label), Paragraph(expected, body)],
                [Paragraph("Execution", label), Paragraph(
                    "Status: NOT RUN | Evidence: ____________________ | "
                    "Defect: __________ | Retest: __________",
                    body,
                )],
            ],
            colWidths=[39 * mm, 121 * mm],
        )
        details.setStyle(TableStyle([
            ("BOX", (0, 0), (-1, -1), 0.7, BORDER),
            ("INNERGRID", (0, 0), (-1, -1), 0.4, BORDER),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#FAFBFA")),
            ("LEFTPADDING", (0, 0), (-1, -1), 6),
            ("RIGHTPADDING", (0, 0), (-1, -1), 6),
            ("TOPPADDING", (0, 0), (-1, -1), 4),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ]))
        story.append(KeepTogether([heading, details]))
        if index < len(cases) - 1:
            story.append(Spacer(1, 4 * mm))

    doc.build(story)


def merge(base: Path, appendix: Path, output: Path) -> None:
    writer = PdfWriter()
    for source in (base, appendix):
        for page in PdfReader(str(source)).pages:
            writer.add_page(page)
    writer.add_metadata({
        "/Title": "Hima QA Release Testing SOP and Roadmap - Rev 2026-07-23",
        "/Subject": "Includes false-busy incoming-call teardown regression appendix",
        "/Author": "Innovfix Pvt. Ltd.",
    })
    with output.open("wb") as handle:
        writer.write(handle)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--appendix", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    build_appendix(args.appendix)
    merge(args.base, args.appendix, args.output)


if __name__ == "__main__":
    main()
