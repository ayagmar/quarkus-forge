package dev.ayagmar.quarkusforge;

interface ShellExecutorDiagnostics {
  void success(String actionName);

  void error(String actionName, String message);
}
