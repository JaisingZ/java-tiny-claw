package io.github.tinyclaw.agent.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.library.Architectures;

/**
 * 架构约束测试
 * 保证分层依赖不越界
 */
@AnalyzeClasses(
        packages = "io.github.tinyclaw.agent",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final com.tngtech.archunit.lang.ArchRule layered_structure =
            Architectures.layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Domain").definedBy("io.github.tinyclaw.agent.domain..")
                    .layer("Provider").definedBy("io.github.tinyclaw.agent.provider..")
                    .layer("Context").definedBy("io.github.tinyclaw.agent.context..")
                    .layer("Tool").definedBy("io.github.tinyclaw.agent.tool..")
                    .layer("Runtime").definedBy("io.github.tinyclaw.agent.runtime..")
                    .layer("Communication").definedBy("io.github.tinyclaw.agent.communication..")
                    .layer("App").definedBy("io.github.tinyclaw.agent.app..")
                    .whereLayer("Runtime").mayOnlyAccessLayers(
                            "Domain", "Provider", "Tool", "Context")
                    .whereLayer("Communication").mayOnlyAccessLayers(
                            "Domain", "Provider", "Tool", "Runtime")
                    .whereLayer("Provider").mayOnlyAccessLayers("Domain")
                    .whereLayer("Context").mayOnlyAccessLayers("Domain")
                    .whereLayer("Tool").mayOnlyAccessLayers("Domain")
                    .whereLayer("App").mayOnlyAccessLayers(
                            "Domain", "Provider", "Tool", "Runtime", "Communication", "Context");
}
