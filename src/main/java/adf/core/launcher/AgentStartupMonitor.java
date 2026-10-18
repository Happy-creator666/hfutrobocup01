package adf.core.launcher;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/** Tracks milestones independently: a submitted command is not a confirmed server action. */
public final class AgentStartupMonitor {
  private static final AgentStartupMonitor INSTANCE = new AgentStartupMonitor();
  private final Map<String, Entry> agents = new HashMap<>();

  private static final class Entry {
    private final String type;
    private boolean connected;
    private boolean initialized;
    private boolean active;
    private boolean failed;

    private Entry(String type) {
      this.type = type;
    }
  }

  public record Counts(int connected, int initialized, int active, int failed) {}

  public static AgentStartupMonitor getInstance() {
    return INSTANCE;
  }

  private Entry entry(String type, int id) {
    return agents.computeIfAbsent(type + ":" + id, key -> new Entry(type));
  }

  public synchronized void connected(String type, int id) {
    Entry agent = entry(type, id);
    if (!agent.connected) {
      agent.connected = true;
      report(type, id, "CONNECTED");
    }
  }

  public synchronized void initialized(String type, int id, String mode) {
    Entry agent = entry(type, id);
    if (!agent.connected || agent.failed) {
      throw new IllegalStateException("Cannot initialize an unconnected or failed agent: " + type + ":" + id);
    }
    if (!agent.initialized) {
      agent.initialized = true;
      report(type, id, "INITIALIZED mode=" + mode);
    }
  }

  public synchronized void actionSubmitted(String type, int id, int time) {
    Entry agent = entry(type, id);
    if (!agent.initialized || agent.failed) {
      throw new IllegalStateException("Cannot submit an action before initialization: " + type + ":" + id);
    }
    if (!agent.active) {
      agent.active = true;
      report(type, id, "FIRST_ACTION_SUBMITTED timestep=" + time);
    }
  }

  public synchronized void failed(String type, int id, Throwable failure) {
    Entry agent = entry(type, id);
    agent.failed = true;
    ConsoleOutput.error("Agent " + type + ":" + id + " INITIALIZATION_FAILED: "
        + failure.getClass().getSimpleName() + ": " + failure.getMessage());
    report(type, id, "FAILED");
  }

  public synchronized Counts counts(String type) {
    int connected = 0, initialized = 0, active = 0, failed = 0;
    for (Entry agent : agents.values()) {
      if (!agent.type.equals(type)) continue;
      if (agent.connected) connected++;
      if (agent.initialized) initialized++;
      if (agent.active) active++;
      if (agent.failed) failed++;
    }
    return new Counts(connected, initialized, active, failed);
  }

  private void report(String type, int id, String stage) {
    ConsoleOutput.info("Agent " + type + ":" + id + " " + stage + "; " + type + " " + counts(type));
  }

  public synchronized void reportAll() {
    TreeSet<String> types = new TreeSet<>();
    agents.values().forEach(agent -> types.add(agent.type));
    for (String type : types) ConsoleOutput.info("Startup summary " + type + " " + counts(type));
  }
}
