package dev.ayagmar.quarkusforge;

final class ExitCodes {
  private ExitCodes() {}

  static final int OK = 0;
  static final int VALIDATION = 2;
  static final int NETWORK = 3;
  static final int ARCHIVE = 4;
  static final int CANCELLED = 130;
}
