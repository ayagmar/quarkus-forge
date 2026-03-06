package dev.ayagmar.quarkusforge;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "dev.ayagmar.quarkusforge")
class HeadlessArchitectureRulesTest {
  private static final String[] CORE_NON_UI_PACKAGES = {
    "dev.ayagmar.quarkusforge.application..",
    "dev.ayagmar.quarkusforge.api..",
    "dev.ayagmar.quarkusforge.archive..",
    "dev.ayagmar.quarkusforge.domain..",
    "dev.ayagmar.quarkusforge.diagnostics..",
    "dev.ayagmar.quarkusforge.forge..",
    "dev.ayagmar.quarkusforge.headless..",
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
          .haveSimpleName("ProjectRequestFactory")
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
  static final ArchRule uiLayerDoesNotDependOnPicocli =
      noClasses()
          .that()
          .resideInAnyPackage("dev.ayagmar.quarkusforge.ui..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("picocli..");
}
