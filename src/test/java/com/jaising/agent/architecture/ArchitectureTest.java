package com.jaising.agent.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.library.Architectures;

/**
 * 架构约束测试
 * 保证分层依赖不越界
 */
@AnalyzeClasses(
    packages = "com.jaising.agent",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final com.tngtech.archunit.lang.ArchRule layered_structure =
      Architectures.layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Domain").definedBy("com.jaising.agent.domain..")
          .layer("Provider").definedBy("com.jaising.agent.provider..")
          .layer("Tool").definedBy("com.jaising.agent.tool..")
          .layer("Middleware").definedBy("com.jaising.agent.middleware..")
          .layer("State").definedBy("com.jaising.agent.state..")
          .layer("Trace").definedBy("com.jaising.agent.trace..")
          .layer("Runtime").definedBy("com.jaising.agent.runtime..")
          .layer("App").definedBy("com.jaising.agent.app..")
          .whereLayer("Runtime").mayOnlyAccessLayers(
              "Domain", "Provider", "Tool", "Middleware", "State", "Trace")
          .whereLayer("Provider").mayOnlyAccessLayers("Domain")
          .whereLayer("Tool").mayOnlyAccessLayers("Domain")
          .whereLayer("Middleware").mayOnlyAccessLayers("Domain")
          .whereLayer("State").mayOnlyAccessLayers("Domain")
          .whereLayer("Trace").mayOnlyAccessLayers("Domain")
          .whereLayer("App").mayOnlyAccessLayers(
              "Domain", "Provider", "Tool", "Middleware", "State", "Trace", "Runtime");
}
