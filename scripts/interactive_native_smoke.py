#!/usr/bin/env python3
"""Linux/macOS PTY smoke for the interactive native TUI binary."""

from __future__ import annotations

import argparse
import fcntl
import os
import pty
import re
import select
import signal
import struct
import subprocess
import sys
import termios
import time


ALT_SCREEN_ENTER = b"\x1b[?1049h"
ANSI_ESCAPE_PATTERN = re.compile(
    r"""
    \x1B
    (?:
        \[[0-?]*[ -/]*[@-~]
      | \][^\x07]*(?:\x07|\x1B\\)
      | P.*?\x1B\\
      | [@-Z\\-_]
    )
    """,
    re.VERBOSE | re.DOTALL,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Smoke-test an interactive binary through a pseudo-terminal."
    )
    parser.add_argument("--binary", required=True, help="Path to the interactive binary")
    parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=15.0,
        help="Maximum time to wait for the startup milestone",
    )
    parser.add_argument("--rows", type=int, default=40, help="Pseudo-terminal row count")
    parser.add_argument("--cols", type=int, default=120, help="Pseudo-terminal column count")
    parser.add_argument(
        "--expect-text",
        action="append",
        default=[],
        help="Visible text that must appear after ANSI control codes are stripped",
    )
    parser.add_argument(
        "--extra-arg",
        action="append",
        default=[],
        help="Additional argument forwarded to the binary",
    )
    return parser.parse_args()


def normalize_output(raw_output: bytes) -> str:
    text = raw_output.decode("utf-8", "replace")
    text = ANSI_ESCAPE_PATTERN.sub("", text)
    text = text.replace("\r", "\n")
    text = "".join(character for character in text if character == "\n" or character >= " ")
    return re.sub(r"\s+", " ", text).strip()


def format_failure(
    binary: str,
    timeout_seconds: float,
    raw_output: bytes,
    normalized_output: str,
    process: subprocess.Popen[bytes],
) -> str:
    exit_code = process.poll()
    raw_preview = raw_output.decode("utf-8", "replace")
    status = "still running" if exit_code is None else f"exit code {exit_code}"
    return (
        f"Interactive smoke failed for {binary} after {timeout_seconds:.1f}s: {status}\n"
        f"Expected alternate-screen enter plus visible text markers.\n"
        f"Normalized output:\n{normalized_output or '<empty>'}\n\n"
        f"Raw output:\n{raw_preview or '<empty>'}"
    )


def terminate_process_group(process: subprocess.Popen[bytes]) -> None:
    if process.poll() is not None:
        return
    try:
        os.killpg(process.pid, signal.SIGINT)
        process.wait(timeout=2)
    except ProcessLookupError:
        return
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=2)


def main() -> int:
    args = parse_args()
    expected_texts = args.expect_text or ["Project Metadata"]
    command = [args.binary, *(args.extra_arg or ["--verbose"])]

    master_fd, slave_fd = pty.openpty()
    fcntl.ioctl(slave_fd, termios.TIOCSWINSZ, struct.pack("HHHH", args.rows, args.cols, 0, 0))

    def configure_child_terminal() -> None:
        os.setsid()
        fcntl.ioctl(slave_fd, termios.TIOCSCTTY, 0)

    env = os.environ.copy()
    env.setdefault("TERM", "xterm-256color")
    process = subprocess.Popen(
        command,
        stdin=slave_fd,
        stdout=slave_fd,
        stderr=slave_fd,
        env=env,
        close_fds=True,
        preexec_fn=configure_child_terminal,
    )
    os.close(slave_fd)

    raw_output = bytearray()
    milestone_reached = False
    normalized_output = ""
    deadline = time.monotonic() + args.timeout_seconds

    try:
        while time.monotonic() < deadline:
            if process.poll() is not None and not select.select([master_fd], [], [], 0)[0]:
                break

            ready, _, _ = select.select([master_fd], [], [], 0.2)
            if master_fd not in ready:
                continue

            try:
                chunk = os.read(master_fd, 4096)
            except OSError:
                break

            if not chunk:
                break

            raw_output.extend(chunk)
            normalized_output = normalize_output(raw_output)
            if ALT_SCREEN_ENTER in raw_output and all(
                marker in normalized_output for marker in expected_texts
            ):
                milestone_reached = True
                break
    finally:
        os.close(master_fd)

    if milestone_reached:
        terminate_process_group(process)
        print(
            "Interactive native smoke reached alternate-screen render milestone: "
            + ", ".join(expected_texts)
        )
        return 0

    terminate_process_group(process)
    normalized_output = normalized_output or normalize_output(raw_output)
    print(
        format_failure(args.binary, args.timeout_seconds, raw_output, normalized_output, process),
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
