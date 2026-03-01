package dev.ayagmar.quarkusforge.ui;

public enum GitHubVisibility {
  PRIVATE("private"),
  PUBLIC("public"),
  INTERNAL("internal");

  private final String cliFlag;

  GitHubVisibility(String cliFlag) {
    this.cliFlag = cliFlag;
  }

  public String cliFlag() {
    return cliFlag;
  }
}
