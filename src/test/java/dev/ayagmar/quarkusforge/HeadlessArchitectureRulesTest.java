package dev.ayagmar.quarkusforge;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "dev.ayagmar.quarkusforge")
class HeadlessArchitectureRulesTest {
  @ArchTest
  static final ArchRule headlessComponentsDoNotDependOnTuiOrTamboui =
      classes()
          .that()
          .haveSimpleNameStartingWith("Headless")
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
          .resideInAnyPackage(
              "dev.ayagmar.quarkusforge.api..",
              "dev.ayagmar.quarkusforge.archive..",
              "dev.ayagmar.quarkusforge.domain..",
              "dev.ayagmar.quarkusforge.diagnostics..",
              "dev.ayagmar.quarkusforge.util..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.ayagmar.quarkusforge.ui..");
}
