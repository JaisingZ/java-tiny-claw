package io.github.tinyclaw.agent.benchmark;

import java.util.Arrays;
import java.util.List;

/**
 * 内置 benchmark suite。
 */
public final class BenchmarkSuites {

    private BenchmarkSuites() {
    }

    public static List<BenchmarkCase> defaults() {
        if (isWindows()) {
            return windowsDefaults();
        }
        return bashDefaults();
    }

    private static List<BenchmarkCase> windowsDefaults() {
        return Arrays.asList(
                new BenchmarkCase(
                        "edit_json_version",
                        "Edit config.json version",
                        "Set-Content -Path config.json -Value '{\"name\":\"tiny-claw\",\"version\":\"v1.0.0\"}'",
                        "当前目录有 config.json。请使用 edit_file 工具把 version 从 v1.0.0 改为 v2.0.0，不要做其他多余操作。",
                        "Select-String -Path config.json -Pattern 'v2.0.0'",
                        6,
                        false),
                new BenchmarkCase(
                        "java_test_generation",
                        "Generate javac runnable Java test",
                        "@'\n"
                                + "public final class Calculator {\n"
                                + "    public int add(int left, int right) {\n"
                                + "        return left + right;\n"
                                + "    }\n"
                                + "}\n"
                                + "'@ | Set-Content -Path Calculator.java",
                        "当前目录有 Calculator.java。请阅读它并创建 CalculatorTest.java。"
                                + "测试文件不要依赖 JUnit，必须包含 main 方法，用 javac Calculator.java CalculatorTest.java "
                                + "和 java CalculatorTest 可以直接验证 add(2, 3) == 5。",
                        "Test-Path CalculatorTest.java; "
                                + "if (-not $?) { exit 1 }; "
                                + "javac Calculator.java CalculatorTest.java; "
                                + "if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; "
                                + "java CalculatorTest",
                        8,
                        false));
    }

    private static List<BenchmarkCase> bashDefaults() {
        return Arrays.asList(
                new BenchmarkCase(
                        "edit_json_version",
                        "Edit config.json version",
                        "printf '%s\\n' '{\"name\":\"tiny-claw\",\"version\":\"v1.0.0\"}' > config.json",
                        "当前目录有 config.json。请使用 edit_file 工具把 version 从 v1.0.0 改为 v2.0.0，不要做其他多余操作。",
                        "grep 'v2.0.0' config.json",
                        6,
                        false),
                new BenchmarkCase(
                        "java_test_generation",
                        "Generate javac runnable Java test",
                        "cat > Calculator.java <<'EOF'\n"
                                + "public final class Calculator {\n"
                                + "    public int add(int left, int right) {\n"
                                + "        return left + right;\n"
                                + "    }\n"
                                + "}\n"
                                + "EOF",
                        "当前目录有 Calculator.java。请阅读它并创建 CalculatorTest.java。"
                                + "测试文件不要依赖 JUnit，必须包含 main 方法，用 javac Calculator.java CalculatorTest.java "
                                + "和 java CalculatorTest 可以直接验证 add(2, 3) == 5。",
                        "test -f CalculatorTest.java && javac Calculator.java CalculatorTest.java && java CalculatorTest",
                        8,
                        false));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
