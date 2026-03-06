package dev.ayagmar.quarkusforge.postgen;

interface ShellExecutorDiagnostics {
  void success(String actionName);

  void error(String actionName, String message);
}
