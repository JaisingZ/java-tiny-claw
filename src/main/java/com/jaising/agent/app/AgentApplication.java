package com.jaising.agent.app;

import com.jaising.agent.provider.SiliconFlowConfig;
import com.jaising.agent.provider.SiliconFlowModelProvider;
import com.jaising.agent.tool.ReadFileTool;
import com.jaising.agent.tool.ToolRegistry;
import java.nio.file.Path;

/**
 * 应用入口
 * 只负责启动提示 不承载业务逻辑
 */
public final class AgentApplication {

    private AgentApplication() {
    }

    /**
     * 程序入口
     * 当前版本仅完成 Provider 和工具注册表最小装配
     */
    public static void main(String[] args) {
        SiliconFlowConfig config = SiliconFlowConfig.loadDefault();
        new SiliconFlowModelProvider(config);
        ToolRegistry registry = new ToolRegistry()
                .register(new ReadFileTool(Path.of(".")));
        System.out.println("java-tiny-claw agent harness provider=" + config.model()
                + " tools=" + registry.definitions().size());
    }
}
