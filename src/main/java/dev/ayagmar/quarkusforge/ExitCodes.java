package dev.ayagmar.quarkusforge;

/**
 * Canonical exit codes used across TUI and headless entry points.
 *
 * <ul>
 *   <li>{@code 0} – success
 *   <li>{@code 2} – validation error (invalid user input or prefill)
 *   <li>{@code 3} – network / API error
 *   <li>{@code 4} – archive extraction error
 *   <li>{@code 5} – internal error (unexpected exception type)
 *   <li>{@code 130} – cancelled by Ctrl+C (POSIX SIGINT convention: 128 + 2)
 * </ul>
 */
final class ExitCodes {
  private ExitCodes() {}

  static final int OK = 0;
  static final int VALIDATION = 2;
  static final int NETWORK = 3;
  static final int ARCHIVE = 4;
  static final int INTERNAL = 5;
  static final int CANCELLED = 130;
}
