#!/usr/bin/env python3
"""Summarize native-image size data and enforce a file-size budget."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


IMAGE_TOTAL_PATTERN = re.compile(r'"title":"Image Details","subtitle":"([^"]+) in total"')
CODE_AREA_PATTERN = re.compile(r'"label":"Code Area","text":"([^"]+)"')
IMAGE_HEAP_PATTERN = re.compile(r'"label":"Image Heap","text":"([^"]+)"')
ORIGINS_HEADER = "Top 10 origins of code area:"
OBJECT_TYPES_HEADER = "Top 10 object types in image heap:"
LEFT_ORIGIN_PATTERN = re.compile(
    r"^\s*([0-9.]+(?:MB|kB|B))\s+(.+?)\s{2,}[0-9.]+(?:MB|kB|B)\s+.+$"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--label", required=True)
    parser.add_argument("--binary", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--log", required=True, type=Path)
    parser.add_argument("--max-bytes", required=True, type=int)
    parser.add_argument("--top-count", type=int, default=5)
    args = parser.parse_args()
    if args.max_bytes < 0:
        parser.error("--max-bytes must be >= 0")
    if args.top_count < 1:
        parser.error("--top-count must be >= 1")
    return args


def require_file(path: Path) -> None:
    if not path.is_file():
        raise SystemExit(f"required file missing: {path}")


def parse_report(report_path: Path) -> tuple[str, str, str]:
    text = report_path.read_text(encoding="utf-8")
    total = IMAGE_TOTAL_PATTERN.search(text)
    code_area = CODE_AREA_PATTERN.search(text)
    image_heap = IMAGE_HEAP_PATTERN.search(text)
    if total is None or code_area is None or image_heap is None:
        raise SystemExit(f"failed to parse native build report: {report_path}")
    return total.group(1), code_area.group(1), image_heap.group(1)


def parse_origins(log_path: Path, top_count: int) -> list[tuple[str, str]]:
    origins: list[tuple[str, str]] = []
    in_section = False

    for raw_line in log_path.read_text(encoding="utf-8").splitlines():
        if ORIGINS_HEADER in raw_line:
            in_section = True
            continue
        if not in_section:
            continue
        if OBJECT_TYPES_HEADER in raw_line or raw_line.startswith("                           For more details"):
            break

        match = LEFT_ORIGIN_PATTERN.match(raw_line)
        if match:
            origins.append((match.group(2).strip(), match.group(1)))
            if len(origins) >= top_count:
                break

    return origins


def format_bytes(size_bytes: int) -> str:
    return f"{size_bytes / (1024 * 1024):.2f} MiB"


def main() -> None:
    args = parse_args()
    require_file(args.binary)
    require_file(args.report)
    require_file(args.log)

    image_total, code_area, image_heap = parse_report(args.report)
    origins = parse_origins(args.log, args.top_count)
    binary_size = args.binary.stat().st_size

    print(f"### {args.label}")
    print(f"- Binary: `{args.binary}`")
    print(
        f"- File size: `{binary_size}` bytes ({format_bytes(binary_size)})"
        f" / budget `{args.max_bytes}` bytes ({format_bytes(args.max_bytes)})"
    )
    print(f"- Build report image total: `{image_total}`")
    print(f"- Build report code area: `{code_area}`")
    print(f"- Build report image heap: `{image_heap}`")
    if origins:
        print("- Top code origins:")
        for origin, size in origins:
            print(f"  - `{origin}`: `{size}`")
    else:
        print("- Top code origins: unavailable from native-image log")

    if binary_size > args.max_bytes:
        raise SystemExit(
            f"{args.label} binary size {binary_size} exceeds budget {args.max_bytes}"
        )


if __name__ == "__main__":
    main()
