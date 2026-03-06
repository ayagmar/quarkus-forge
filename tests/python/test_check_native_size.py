from __future__ import annotations

import importlib.util
import io
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT_PATH = REPO_ROOT / "scripts" / "check_native_size.py"
FIXTURES_DIR = Path(__file__).resolve().parent / "fixtures" / "check_native_size"


def load_module():
    spec = importlib.util.spec_from_file_location("check_native_size", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class CheckNativeSizeScriptTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.module = load_module()

    def test_parse_report_extracts_expected_sections(self):
        total, code_area, image_heap = self.module.parse_report(
            FIXTURES_DIR / "valid-build-report.html"
        )

        self.assertEqual(total, "23.80MB")
        self.assertEqual(code_area, "9.48MB")
        self.assertEqual(image_heap, "8.25MB")

    def test_parse_report_rejects_missing_sections(self):
        with self.assertRaises(SystemExit) as context:
            self.module.parse_report(FIXTURES_DIR / "missing-image-heap-build-report.html")

        self.assertEqual(
            str(context.exception),
            "failed to parse native build report: "
            f"{FIXTURES_DIR / 'missing-image-heap-build-report.html'}",
        )

    def test_parse_report_rejects_malformed_report(self):
        with self.assertRaises(SystemExit) as context:
            self.module.parse_report(FIXTURES_DIR / "malformed-build-report.html")

        self.assertEqual(
            str(context.exception),
            "failed to parse native build report: "
            f"{FIXTURES_DIR / 'malformed-build-report.html'}",
        )

    def test_parse_origins_extracts_left_column_and_honors_limit(self):
        origins = self.module.parse_origins(FIXTURES_DIR / "native.log", top_count=2)

        self.assertEqual(
            origins,
            [
                ("com.example:demo-app", "3.10MB"),
                ("org.acme:dependency", "1.25MB"),
            ],
        )

    def test_parse_origins_returns_empty_when_section_missing(self):
        origins = self.module.parse_origins(
            FIXTURES_DIR / "native-no-origins.log", top_count=5
        )

        self.assertEqual(origins, [])

    def test_parse_args_rejects_negative_max_bytes(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            binary_path = temp_path / "binary"
            report_path = temp_path / "report.html"
            log_path = temp_path / "build.log"
            for path in (binary_path, report_path, log_path):
                path.write_text("stub", encoding="utf-8")

            with redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as context:
                    self.module.parse_args(
                        [
                            "--label",
                            "native",
                            "--binary",
                            str(binary_path),
                            "--report",
                            str(report_path),
                            "--log",
                            str(log_path),
                            "--max-bytes",
                            "-1",
                        ]
                    )

        self.assertEqual(context.exception.code, 2)

    def test_parse_args_rejects_non_positive_top_count(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            binary_path = temp_path / "binary"
            report_path = temp_path / "report.html"
            log_path = temp_path / "build.log"
            for path in (binary_path, report_path, log_path):
                path.write_text("stub", encoding="utf-8")

            with redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as context:
                    self.module.parse_args(
                        [
                            "--label",
                            "native",
                            "--binary",
                            str(binary_path),
                            "--report",
                            str(report_path),
                            "--log",
                            str(log_path),
                            "--max-bytes",
                            "100",
                            "--top-count",
                            "0",
                        ]
                    )

        self.assertEqual(context.exception.code, 2)

    def test_main_prints_ci_summary_with_top_origins(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            binary_path = temp_path / "quarkus-forge"
            binary_path.write_bytes(b"x" * 2048)
            output = io.StringIO()

            with redirect_stdout(output):
                self.module.main(
                    [
                        "--label",
                        "native",
                        "--binary",
                        str(binary_path),
                        "--report",
                        str(FIXTURES_DIR / "valid-build-report.html"),
                        "--log",
                        str(FIXTURES_DIR / "native.log"),
                        "--max-bytes",
                        "4096",
                        "--top-count",
                        "2",
                    ]
                )

        rendered = output.getvalue()
        self.assertIn("### native", rendered)
        self.assertIn("- Build report image total: `23.80MB`", rendered)
        self.assertIn("- Top code origins:", rendered)
        self.assertIn("`com.example:demo-app`: `3.10MB`", rendered)
        self.assertIn("`org.acme:dependency`: `1.25MB`", rendered)

    def test_main_reports_missing_origins_as_unavailable(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            binary_path = temp_path / "quarkus-forge"
            binary_path.write_bytes(b"x" * 1024)
            output = io.StringIO()

            with redirect_stdout(output):
                self.module.main(
                    [
                        "--label",
                        "headless-native",
                        "--binary",
                        str(binary_path),
                        "--report",
                        str(FIXTURES_DIR / "valid-build-report.html"),
                        "--log",
                        str(FIXTURES_DIR / "native-no-origins.log"),
                        "--max-bytes",
                        "2048",
                    ]
                )

        self.assertIn(
            "- Top code origins: unavailable from native-image log",
            output.getvalue(),
        )

    def test_cli_exits_when_binary_exceeds_budget(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            binary_path = temp_path / "quarkus-forge"
            binary_path.write_bytes(b"x" * 4096)

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT_PATH),
                    "--label",
                    "native",
                    "--binary",
                    str(binary_path),
                    "--report",
                    str(FIXTURES_DIR / "valid-build-report.html"),
                    "--log",
                    str(FIXTURES_DIR / "native.log"),
                    "--max-bytes",
                    "1024",
                ],
                capture_output=True,
                text=True,
                check=False,
            )

        self.assertEqual(result.returncode, 1)
        self.assertIn("native binary size 4096 exceeds budget 1024", result.stderr)


if __name__ == "__main__":
    unittest.main()
