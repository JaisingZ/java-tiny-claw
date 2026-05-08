package com.jaising.agent.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jaising.agent.domain.AgentState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class FileStateStore implements StateStore {

  private final Path baseDir;
  private final ObjectMapper mapper;

  public FileStateStore(Path baseDir) {
    this.baseDir = baseDir;
    this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  }

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
      throw new IllegalStateException("Failed to load state for task " + taskId, ex);
    }
  }

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

  private Path stateFile(String taskId) {
    return baseDir.resolve(taskId + ".json");
  }
}
