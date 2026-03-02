package dev.ayagmar.quarkusforge.api;

record CacheWriteOutcome(boolean written, boolean rejected, String detail) {
  public CacheWriteOutcome {
    detail = detail == null ? "" : detail.strip();
  }

  static CacheWriteOutcome writeSucceeded() {
    return new CacheWriteOutcome(true, false, "");
  }

  static CacheWriteOutcome writeRejected(String detail) {
    return new CacheWriteOutcome(false, true, detail);
  }

  static CacheWriteOutcome writeFailed(String detail) {
    return new CacheWriteOutcome(false, false, detail);
  }
}
