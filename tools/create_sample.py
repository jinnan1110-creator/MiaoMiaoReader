#!/usr/bin/env python3
"""Generate the project's original reading fixture; Python standard library only."""
from pathlib import Path
import json
import textwrap

ROOT = Path(__file__).resolve().parents[1]
CHAPTERS = [
    ("A quiet hour", [
        "Every evening, Maya sets aside a quiet hour to read. She chooses a short article, puts her phone on a stand, and opens her notebook. Her aim is not to finish as quickly as possible. She wants to understand how each idea connects to the next. When a word is unfamiliar, she first looks at the surrounding sentence. Sometimes the meaning becomes clear without a dictionary.",
        "At other times, Maya saves a small note in Chinese beside the word. The note records the meaning that fits this particular passage. A familiar word may have a different meaning in another context, so she does not treat every definition as a rule. She reads the paragraph again, checks the original page when necessary, and changes her note if her understanding improves.",
        "At the end of the hour, she exports a few useful words together with their original sentences. The next morning she hides the Chinese notes and reads the passage once more. This simple routine turns a list of isolated words into a record of ideas she has understood."
    ]),
    ("The small city garden", [
        "A small garden stands between two apartment buildings. For many years it was an empty corner where people left broken chairs. One spring, a group of neighbours decided to change it. They cleared the ground, collected wooden boxes, and planted herbs that could survive the summer heat. Nobody expected the garden to feed the whole neighbourhood. They simply wanted a useful place to meet.",
        "The project taught the group to work with limited resources. Rainwater was stored in covered containers, while fallen leaves became compost. Volunteers took turns watering the plants. When an early plan failed, they discussed what had happened and tried a different approach. The most valuable resource was not money, but the time that people were willing to share.",
        "By autumn, the garden had become a place for conversation. Children watched insects, older residents exchanged advice, and new neighbours learned one another's names. The harvest was modest, yet the change in daily life was easy to see. A neglected space had become a common responsibility."
    ]),
    ("Learning from a forest", [
        "A forest is more than a collection of trees. Beneath the branches, many forms of life depend on one another. Fallen wood provides shelter for insects. Fungi break down dead material and return nutrients to the soil. Small plants grow in the changing light, while birds carry seeds to new places. These relationships are often difficult to notice during a short visit.",
        "Researchers who study a forest return to the same locations over many years. They record growth, rainfall, temperature, and changes in the number of species. A single measurement can be useful, but a series of measurements helps reveal a pattern. An unusually dry season may have effects that only become visible later. Good observations therefore require both careful methods and patience.",
        "Understanding these connections can improve decisions about conservation. Protecting one familiar species may be helpful, but the wider environment also matters. A policy that preserves soil, water, and different habitats gives the whole forest a better chance to recover after a disturbance. Studying a living system reminds us that the most important relationships are not always the most obvious ones."
    ])
]

def pdf_string(value):
    return value.replace('\\', '\\\\').replace('(', '\\(').replace(')', '\\)')

def build():
    # Courier has a fixed width of 600 units; cached boxes match PDF coordinates.
    objects = [b"<< /Type /Catalog /Pages 2 0 R >>", b"", b"<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>"]
    page_ids, cached = [], []
    for title, paragraphs in CHAPTERS:
        lines = ["MIAOMIAO READER / ORIGINAL SAMPLE", "", title, ""]
        for paragraph in paragraphs:
            lines.extend(textwrap.wrap(paragraph, width=70))
            lines.append("")
        commands = ["0.14 0.30 0.25 rg", "BT", "/F1 11 Tf", "15 TL", "44 754 Td"]
        tokens, index = [], 0
        for line_number, line in enumerate(lines):
            if line_number:
                commands.append("T*")
            commands.append("(" + pdf_string(line) + ") Tj")
            for start, word in word_positions(line):
                tokens.append({"i": index, "t": word, "x": (44 + start * 6.6) / 600,
                               "y": (800 - (754 - line_number * 15) - 9) / 800,
                               "w": len(word) * 6.6 / 600, "h": 12 / 800})
                index += 1
        commands.append("ET")
        content = ("\n".join(commands) + "\n").encode('ascii')
        page_id, content_id = len(objects) + 1, len(objects) + 2
        page_ids.append(page_id)
        objects.append(("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 800] /Resources << /Font << /F1 3 0 R >> >> /Contents %d 0 R >>" % content_id).encode())
        objects.append(("<< /Length %d >>\nstream\n" % len(content)).encode() + content + b"endstream")
        cached.append({"blocks": [{"tokens": tokens}], "w": 600, "h": 800, "engine": "Original sample / Courier 11pt"})
    objects[1] = ("<< /Type /Pages /Kids [%s] /Count %d >>" % (" ".join("%d 0 R" % i for i in page_ids), len(page_ids))).encode()
    result = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = [0]
    for number, obj in enumerate(objects, 1):
        offsets.append(len(result)); result.extend(("%d 0 obj\n" % number).encode() + obj + b"\nendobj\n")
    xref = len(result)
    result.extend(("xref\n0 %d\n0000000000 65535 f \n" % len(offsets)).encode())
    for offset in offsets[1:]:
        result.extend(("%010d 00000 n \n" % offset).encode())
    result.extend(("trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(offsets), xref)).encode())
    assets = ROOT / 'app/src/main/assets'
    (assets / 'sample_reading.pdf').write_bytes(result)
    (assets / 'sample_pages.json').write_text(json.dumps(cached, ensure_ascii=False, separators=(',', ':')) + '\n')
    (ROOT / 'docs/sample_text.txt').write_text('\n\n'.join(title + '\n\n' + '\n\n'.join(paragraphs) for title, paragraphs in CHAPTERS) + '\n')
    (assets / 'licenses/SAMPLE_SOURCE.txt').write_text('Original demonstration text and PDF written for MiaoMiaoReader. Copyright (c) 2026 MiaoMiaoReader contributors. MIT License; see repository LICENSE. No third-party examination material.\n')
    print('Generated original sample: %d pages, %d tokens' % (len(cached), sum(len(p['blocks'][0]['tokens']) for p in cached)))

def word_positions(line):
    import re
    return [(m.start(), m.group()) for m in re.finditer(r'\S+', line)]

if __name__ == '__main__':
    build()
