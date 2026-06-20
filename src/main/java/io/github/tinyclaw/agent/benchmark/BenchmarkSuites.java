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
                        "@'\n"
                                + "{\n"
                                + "  \"name\": \"tiny-claw\",\n"
                                + "  \"version\": \"v1.0.0\"\n"
                                + "}\n"
                                + "'@ | Set-Content -Path config.json",
                        "当前目录有 config.json。请使用 edit_file 工具把 version 从 v1.0.0 改为 v2.0.0，不要做其他多余操作。",
                        "$match = Select-String -Path config.json -Pattern 'v2.0.0'; "
                                + "if (-not $match) { exit 1 }",
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
                        false),
                concurrencyCounterRepairCase(
                        "@'\n" + concurrencyCounterProgram() + "\n'@ | Set-Content -Path CounterRaceCheck.java",
                        "javac CounterRaceCheck.java; "
                                + "if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; "
                                + "java CounterRaceCheck 100000"));
    }

    private static List<BenchmarkCase> bashDefaults() {
        return Arrays.asList(
                new BenchmarkCase(
                        "edit_json_version",
                        "Edit config.json version",
                        "cat > config.json <<'EOF'\n"
                                + "{\n"
                                + "  \"name\": \"tiny-claw\",\n"
                                + "  \"version\": \"v1.0.0\"\n"
                                + "}\n"
                                + "EOF",
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
                        false),
                concurrencyCounterRepairCase(
                        "cat > CounterRaceCheck.java <<'EOF'\n"
                                + concurrencyCounterProgram()
                                + "\nEOF",
                        "javac CounterRaceCheck.java && java CounterRaceCheck 100000"));
    }

    private static BenchmarkCase concurrencyCounterRepairCase(String setupCommand, String validateCommand) {
        return new BenchmarkCase(
                "concurrency_counter_repair",
                "Unknown workspace concurrency counter repair",
                setupCommand,
                "当前目录是一个未知 Java 小项目。请先自行探索目录和文件，不要直接假设问题位置。"
                        + "找到并发安全问题，分析原因，修复它，并执行正确性验证。"
                        + "请把验证命令和结果写入最终回答。",
                validateCommand,
                12,
                true);
    }

    private static String concurrencyCounterProgram() {
        return "public final class CounterRaceCheck {\n"
                + "    private static int counter = 0;\n"
                + "\n"
                + "    public static void main(String[] args) throws Exception {\n"
                + "        int expected = args.length == 0 ? 100000 : Integer.parseInt(args[0]);\n"
                + "        int workers = 8;\n"
                + "        int perWorker = expected / workers;\n"
                + "        Thread[] threads = new Thread[workers];\n"
                + "        for (int i = 0; i < workers; i++) {\n"
                + "            threads[i] = new Thread(() -> {\n"
                + "                for (int j = 0; j < perWorker; j++) {\n"
                + "                    int next = counter + 1;\n"
                + "                    if ((j & 255) == 0) {\n"
                + "                        Thread.yield();\n"
                + "                    }\n"
                + "                    counter = next;\n"
                + "                }\n"
                + "            });\n"
                + "            threads[i].start();\n"
                + "        }\n"
                + "        for (Thread thread : threads) {\n"
                + "            thread.join();\n"
                + "        }\n"
                + "        if (counter != expected) {\n"
                + "            throw new IllegalStateException(\"expected \" + expected + \" but got \" + counter);\n"
                + "        }\n"
                + "        System.out.println(\"OK counter=\" + counter);\n"
                + "    }\n"
                + "}\n";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
