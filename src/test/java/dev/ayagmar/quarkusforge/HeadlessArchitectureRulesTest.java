package dev.ayagmar.quarkusforge;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "dev.ayagmar.quarkusforge",
    importOptions = ImportOption.DoNotIncludeTests.class)
class HeadlessArchitectureRulesTest {
  private static final String[] CORE_NON_UI_PACKAGES = {
    "dev.ayagmar.quarkusforge.application..",
    "dev.ayagmar.quarkusforge.api..",
    "dev.ayagmar.quarkusforge.archive..",
    "dev.ayagmar.quarkusforge.domain..",
    "dev.ayagmar.quarkusforge.diagnostics..",
    "dev.ayagmar.quarkusforge.forge..",
    "dev.ayagmar.quarkusforge.headless..",
    "dev.ayagmar.quarkusforge.persistence..",
    "dev.ayagmar.quarkusforge.util.."
  };

  @ArchTest
  static final ArchRule headlessComponentsDoNotDependOnTuiOrTamboui =
      classes()
          .that()
          .resideInAnyPackage("dev.ayagmar.quarkusforge.headless..")
          .or()
          .haveSimpleName("HeadlessCli")
          .or()
          .haveSimpleName("GenerateCommand")
          .or()
          .haveSimpleName("InputResolutionService")
          .should()
          .onlyDependOnClassesThat()
          .resideOutsideOfPackages("dev.ayagmar.quarkusforge.ui..", "dev.tamboui..");

  @ArchTest
  static final ArchRule nonUiCoreLayersDoNotDependOnUiPackage =
      noClasses()
          .that()
          .resideInAnyPackage(CORE_NON_UI_PACKAGES)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.ayagmar.quarkusforge.ui..");

  @ArchTest
  static final ArchRule nonUiCoreLayersDoNotDependOnTamboui =
      noClasses()
          .that()
          .resideInAnyPackage(CORE_NON_UI_PACKAGES)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.tamboui..");

  @ArchTest
  static final ArchRule applicationLayerDoesNotDependOnPicocli =
      noClasses()
          .that()
          .resideInAnyPackage("dev.ayagmar.quarkusforge.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("picocli..");

  @ArchTest
  static final ArchRule runtimeLayerDoesNotDependOnCliOrPicocli =
      noClasses()
          .that()
          .resideInAnyPackage("dev.ayagmar.quarkusforge.runtime..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.ayagmar.quarkusforge.cli..", "picocli..");

  @ArchTest
  static final ArchRule uiLayerDoesNotDependOnCliHeadlessRuntimeOrPicocli =
      noClasses()
          .that()
          .resideInAnyPackage("dev.ayagmar.quarkusforge.ui..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "dev.ayagmar.quarkusforge.cli..",
              "dev.ayagmar.quarkusforge.headless..",
              "dev.ayagmar.quarkusforge.runtime..",
              "picocli..");

  @ArchTest
  static final ArchRule headlessLayerDoesNotDependOnRuntime =
      noClasses()
          .that()
          .resideInAnyPackage("dev.ayagmar.quarkusforge.headless..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.ayagmar.quarkusforge.runtime..");

  @ArchTest
  static final ArchRule cliLayerDoesNotDependOnPersistence =
      noClasses()
          .that()
          .resideInAnyPackage("dev.ayagmar.quarkusforge.cli..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.ayagmar.quarkusforge.persistence..");

  @ArchTest
  static final ArchRule postgenLayerDoesNotDependOnCliHeadlessOrPersistence =
      noClasses()
          .that()
          .resideInAnyPackage("dev.ayagmar.quarkusforge.postgen..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "dev.ayagmar.quarkusforge.cli..",
              "dev.ayagmar.quarkusforge.headless..",
              "dev.ayagmar.quarkusforge.persistence..",
              "picocli..",
              "dev.tamboui..");

  @ArchTest
  static final ArchRule persistenceLayerDoesNotDependOnCliHeadlessRuntimeUiOrPostgen =
      noClasses()
          .that()
          .resideInAnyPackage("dev.ayagmar.quarkusforge.persistence..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "dev.ayagmar.quarkusforge.cli..",
              "dev.ayagmar.quarkusforge.headless..",
              "dev.ayagmar.quarkusforge.postgen..",
              "dev.ayagmar.quarkusforge.runtime..",
              "dev.ayagmar.quarkusforge.ui..",
              "picocli..",
              "dev.tamboui..");
}
