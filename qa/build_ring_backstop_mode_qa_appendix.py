#!/usr/bin/env python3
"""Append tester-ready ring-backstop-mode cases to the maintained HIMA QA SOP."""

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
    PageBreak,
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
        "Hima QA Release Testing SOP - Ring backstop mode - Rev 2026-07-23",
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
        fontSize=7.85,
        leading=9.8,
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
        title="Hima QA Ring Backstop Mode Appendix",
        author="Innovfix Pvt. Ltd.",
    )
    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="normal")
    doc.addPageTemplates([PageTemplate(id="qa", frames=frame, onPage=page_decor)])

    story = [
        Paragraph("RELEASE TEST APPENDIX RBM", gate),
        Spacer(1, 2 * mm),
        Paragraph("Incoming-Ring Swipe Backstop", title),
        Paragraph(
            "Android regression coverage - 2026-07-23. The fix starts the existing "
            "bounded ring watcher for OneSignal fallback delivery and for FCM rings "
            "shown while Hima is foreground and unlocked. A no-action task swipe must "
            "report rejected so a free creator is not left temporarily busy. Normal "
            "connected-call ending is unchanged.",
            subtitle,
        ),
    ]
    gate_box = Table(
        [
            [
                Paragraph("Release gate", label),
                Paragraph(
                    "RBM-001 through RBM-006 are MUST-PASS on physical devices before "
                    "shipping. The backend call-status terminal guard (CSG) must be active "
                    "first; without it, a delayed durable reject could relabel a call that "
                    "connected during the retry window. Source compile success alone does "
                    "not authorize APK release.",
                    body,
                ),
            ]
        ],
        colWidths=[32 * mm, 128 * mm],
    )
    gate_box.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), LIGHT_GREEN),
                ("BOX", (0, 0), (-1, -1), 0.8, GREEN),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 7),
                ("RIGHTPADDING", (0, 0), (-1, -1), 7),
                ("TOPPADDING", (0, 0), (-1, -1), 6),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
            ]
        )
    )
    story.extend([gate_box, Spacer(1, 5 * mm)])

    cases = [
        (
            "RBM-001",
            "FCM foreground-unlocked swipe releases creator",
            "MUST-PASS / P0",
            "Current Android candidate on two physical devices; receiver app open and unlocked; "
            "backend terminal guard active in staging; stable network.",
            "Female receiver and male receiver; one audio and one video direct call each.",
            "1. Keep the receiver inside Hima with the phone unlocked. 2. Start the call and confirm "
            "the in-app accept screen appears with no duplicate system popup. 3. Open Recents and "
            "swipe Hima away without Answer or Decline. 4. Immediately call the same receiver again. "
            "5. Inspect client logs, first call row and API result.",
            "The bounded backstop starts without a notification, onTaskRemoved reports rejected once, "
            "the caller exits Connecting, and the receiver is selectable/reachable again within five "
            "seconds. The first row is closed and no duplicate ring card appears.",
        ),
        (
            "RBM-002",
            "OneSignal-only fallback swipe releases creator",
            "MUST-PASS / P0",
            "Physical Android device with FCM delivery suppressed in a controlled test environment; "
            "OneSignal fallback enabled; notification and full-screen permissions recorded.",
            "Locked/background and process-cold cases for female and male receivers; audio and video.",
            "1. Suppress only the primary FCM delivery. 2. Send the test call through the normal "
            "OneSignal fallback. 3. Confirm one CallStyle ring and Telecom registration. 4. Swipe the "
            "task away without action. 5. Retry the receiver immediately and inspect the call row/logs.",
            "OneSignal starts the foreground watcher, exactly one incoming-call card is visible, the "
            "swipe reports rejected once, and the receiver is free within five seconds. If Telecom/FGS "
            "dispatch is denied, the display fallback remains visible and the failure is logged.",
        ),
        (
            "RBM-003",
            "Accepted call is never rejected by task swipe",
            "MUST-PASS / P0",
            "Physical devices; accepted audio and video calls; backend row has started_time.",
            "FCM and OneSignal-fallback entry variants, both receiver genders.",
            "1. Start and Answer each call. 2. Confirm both peers join Agora and started_time is set. "
            "3. Swipe Hima from Recents while the watcher is still inside its 35-second lifetime. "
            "4. Inspect logs and row. 5. End the call normally and reconcile the result.",
            "wasRingAcceptedFor blocks the swipe reject. No rejected/not_answered write is attempted, "
            "the connected row is not relabeled, and normal connected-call teardown and settlement "
            "remain unchanged.",
        ),
        (
            "RBM-004",
            "Decline survives offline and app death for both receivers",
            "MUST-PASS / P0",
            "Backend terminal guard active; WorkManager inspection available; network can be toggled.",
            "Female and male receiver Decline cases plus one delayed-retry race that connects first.",
            "1. Disable receiver networking while ringing. 2. Tap Decline and close the app. 3. Restore "
            "network and allow WorkManager to run. 4. Repeat with a controlled race where started_time "
            "wins before the retry. 5. Inspect unique work, API response and row.",
            "One unique reject job per call survives process death. An unanswered row closes when "
            "network returns; an already-connected row is a guarded no-op. There is no retry loop, "
            "duplicate notification, or connected-call relabel.",
        ),
        (
            "RBM-005",
            "Lifecycle, notification and capacity regression",
            "MUST-PASS / P1",
            "Android 8, 13, 14 and 15 physical/emulated coverage; profiler/log collection enabled.",
            "100 foreground-unlocked rings, 100 OneSignal fallback rings, and 100 controlled "
            "same-call FCM+OneSignal simultaneous/OneSignal-first deliveries without provider "
            "fan-out to production users.",
            "1. Repeat each ring path and cancel normally, swipe, Answer, and let it time out. 2. Count "
            "Telecom addNewIncomingCall dispatches, service instances, notifications, API calls and "
            "WorkManager jobs. 3. For dual delivery, verify both provider callbacks race on the same "
            "call ID and only one Telecom registration wins. 4. Measure main-thread stalls, memory "
            "and battery wake time. 5. Verify service teardown after 35 seconds.",
            "At most one bounded service instance exists per process, no network work is added to the "
            "main thread, each same-call dual delivery dispatches addNewIncomingCall exactly once, the "
            "foreground-open path adds no system popup, and all service callbacks are removed at "
            "teardown. No material latency, memory, battery or notification regression occurs.",
        ),
        (
            "RBM-006",
            "Compatibility, rollback and production smoke gate",
            "MUST-PASS / P0 / PROD",
            "Signed QA build first; explicit release approval; backend guard already verified active; "
            "test accounts only; rollback build retained.",
            "Fresh install and upgrade install, notification denied/allowed, keyguard on/off, process "
            "warm/cold, both app flavors.",
            "1. Complete RBM-001 through RBM-005 on the signed QA build. 2. Verify development and "
            "production Kotlin compiles. 3. Install the production candidate on controlled devices. "
            "4. Run one FCM and one OneSignal smoke call per receiver gender. 5. Compare busy duration, "
            "crashes, ANRs and call-status errors. 6. Rehearse rollback to the prior APK.",
            "All compatibility states retain a visible actionable ring or safe display fallback, free "
            "receivers are not stranded busy after a swipe, connected calls remain protected, and no "
            "new crash/ANR/call-status error appears. Rollback restores prior app behavior without a "
            "server rollback.",
        ),
    ]

    for index, (case_id, name, priority, pre, data, steps, expected) in enumerate(cases):
        if index == 3:
            story.append(PageBreak())
        heading = Table(
            [
                [
                    Paragraph(f"{case_id} - {name}", case_title),
                    Paragraph(priority, gate),
                ]
            ],
            colWidths=[122 * mm, 38 * mm],
        )
        heading.setStyle(
            TableStyle(
                [
                    ("BACKGROUND", (0, 0), (-1, -1), LIGHT_GREEN),
                    ("BOX", (0, 0), (-1, -1), 0.7, BORDER),
                    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 6),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                    ("TOPPADDING", (0, 0), (-1, -1), 5),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
                ]
            )
        )
        details = Table(
            [
                [Paragraph("Preconditions / build / role", label), Paragraph(pre, body)],
                [Paragraph("Test data", label), Paragraph(data, body)],
                [Paragraph("Numbered steps", label), Paragraph(steps, body)],
                [Paragraph("Expected result", label), Paragraph(expected, body)],
                [
                    Paragraph("Execution", label),
                    Paragraph(
                        "Status: NOT RUN | Evidence: ____________________ | "
                        "Defect: __________ | Retest: __________",
                        body,
                    ),
                ],
            ],
            colWidths=[39 * mm, 121 * mm],
        )
        details.setStyle(
            TableStyle(
                [
                    ("BOX", (0, 0), (-1, -1), 0.7, BORDER),
                    ("INNERGRID", (0, 0), (-1, -1), 0.4, BORDER),
                    ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#FAFBFA")),
                    ("LEFTPADDING", (0, 0), (-1, -1), 6),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                    ("TOPPADDING", (0, 0), (-1, -1), 4),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
                ]
            )
        )
        story.append(KeepTogether([heading, details]))
        if index < len(cases) - 1 and index != 2:
            story.append(Spacer(1, 4 * mm))

    doc.build(story)


def merge(base: Path, appendix: Path, output: Path) -> None:
    writer = PdfWriter()
    for source in (base, appendix):
        for page in PdfReader(str(source)).pages:
            writer.add_page(page)
    writer.add_metadata(
        {
            "/Title": "Hima QA Release Testing SOP and Roadmap - Rev 2026-07-23",
            "/Subject": "Includes incoming-ring swipe backstop regression appendix",
            "/Author": "Innovfix Pvt. Ltd.",
        }
    )
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
