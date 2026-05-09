package com.jaising.agent.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.Task;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 文件状态存储测试
 * 验证写入和读取可用
 */
class FileStateStoreTest {

  @TempDir
  Path tempDir;

  /**
   * 保存后可再次读取
   */
  @Test
  void savesAndLoadsState() {
    FileStateStore store = new FileStateStore(tempDir);
    AgentState state = AgentState.create(new Task("task-1", "write docs"))
        .advance()
        .observe("first observation");

    store.save(state);

    assertThat(store.load("task-1")).hasValue(state);
  }
}
