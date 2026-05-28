package com.jaising.agent.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jaising.agent.domain.AgentState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * 文件状态存储
 * 适合需要落盘和恢复的场景
 */
public final class FileStateStore implements StateStore {

    private final Path baseDir;
    private final ObjectMapper mapper;

    /**
     * 创建文件存储
     * 使用缩进输出便于人工查看
     */
    public FileStateStore(Path baseDir) {
        this.baseDir = baseDir;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 读取状态文件
     * 文件不存在时返回空
     */
    @Override
    public Optional<AgentState> load(String taskId) {
        Path path = stateFile(taskId);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return Optional.of(mapper.readValue(bytes, AgentState.class));
        } catch (IOException ex) {
            /**
             * 执行 IllegalStateException 操作。
             */
            throw new IllegalStateException("Failed to load state for task " + taskId, ex);
        }
    }

    /**
     * 写入状态文件
     * 先创建目录再覆盖写入
     */
    @Override
    public void save(AgentState state) {
        try {
            Files.createDirectories(baseDir);
            byte[] bytes = mapper.writeValueAsBytes(state);
            Files.write(stateFile(state.taskId()), bytes,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save state for task " + state.taskId(), ex);
        }
    }

    /**
     * 计算状态文件路径
     */
    private Path stateFile(String taskId) {
        return baseDir.resolve(taskId + ".json");
    }
}
