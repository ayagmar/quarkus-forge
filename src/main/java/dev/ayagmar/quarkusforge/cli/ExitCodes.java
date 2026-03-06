package dev.ayagmar.quarkusforge.cli;

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
public final class ExitCodes {
  private ExitCodes() {}

  public static final int OK = 0;
  public static final int VALIDATION = 2;
  public static final int NETWORK = 3;
  public static final int ARCHIVE = 4;
  public static final int INTERNAL = 5;
  public static final int CANCELLED = 130;
}
