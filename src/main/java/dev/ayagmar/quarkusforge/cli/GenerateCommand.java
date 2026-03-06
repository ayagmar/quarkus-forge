package dev.ayagmar.quarkusforge.cli;

import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.forge.Forgefile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
    name = "generate",
    mixinStandardHelpOptions = true,
    description = "Generate a Quarkus project without starting the TUI")
public final class GenerateCommand implements Callable<Integer> {
  @ParentCommand private HeadlessRunner rootCommand;

  @Mixin private RequestOptions requestOptions = new RequestOptions();

  @Option(
      names = {"-e", "--extension"},
      split = ",",
      description = "Extension id to include (repeatable and comma-separated)")
  private List<String> extensions = new ArrayList<>();

  @Option(
      names = "--preset",
      split = ",",
      description = "Extension preset(s) from code.quarkus.io plus favorites")
  private List<String> presets = new ArrayList<>();

  @Option(
      names = "--from",
      description =
          "Path or name for a Forgefile template. "
              + "If local path is missing, resolves from ~/.quarkus-forge/recipes")
  private String fromFile;

  @Option(
      names = "--save-as",
      description =
          "Write the Forgefile to this path after generation. "
              + "Simple names are written under ~/.quarkus-forge/recipes")
  private String saveAsFile;

  @Option(
      names = "--lock",
      defaultValue = "false",
      description =
          "Write or update the locked section in the Forgefile (requires --from or --save-as)")
  private boolean lock;

  @Option(
      names = "--lock-check",
      defaultValue = "false",
      description = "Verify no drift against the locked section (requires --from)")
  private boolean lockCheck;

  @Option(
      names = "--dry-run",
      defaultValue = "false",
      description = "Validate full generation request without writing files")
  private boolean dryRun;

  @Override
  public Integer call() {
    return rootCommand.runHeadlessGenerate(this);
  }

  public CliPrefill cliPrefill() {
    return requestOptions.toCliPrefill();
  }

  public Forgefile explicitTemplate() {
    return requestOptions
        .toExplicitTemplate()
        .withSelections(
            presets.isEmpty() ? null : presets, extensions.isEmpty() ? null : extensions);
  }

  public void setCliPrefill(CliPrefill prefill) {
    requestOptions = RequestOptions.explicitFromCliPrefill(prefill);
  }

  public List<String> extensions() {
    return extensions;
  }

  public List<String> presets() {
    return presets;
  }

  public String fromFile() {
    return fromFile;
  }

  public void setFromFile(String fromFile) {
    this.fromFile = fromFile;
  }

  public String saveAsFile() {
    return saveAsFile;
  }

  public void setSaveAsFile(String saveAsFile) {
    this.saveAsFile = saveAsFile;
  }

  public boolean lock() {
    return lock;
  }

  public void setLock(boolean lock) {
    this.lock = lock;
  }

  public boolean lockCheck() {
    return lockCheck;
  }

  public void setLockCheck(boolean lockCheck) {
    this.lockCheck = lockCheck;
  }

  public boolean dryRun() {
    return dryRun;
  }
}
