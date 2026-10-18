package adf.core.launcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentStartupMonitorTest {
  @Test void countsConnectionInitializationAndFirstActionIndependently() {
    AgentStartupMonitor monitor = new AgentStartupMonitor();
    monitor.connected("AT", 1);
    monitor.connected("AT", 2);
    monitor.connected("AT", 1);
    monitor.initialized("AT", 1, "NON_PRECOMPUTE");
    monitor.actionSubmitted("AT", 1, 5);
    monitor.actionSubmitted("AT", 1, 6);
    monitor.failed("AT", 2, new IllegalStateException("tool failed"));
    assertEquals(new AgentStartupMonitor.Counts(2, 1, 1, 1), monitor.counts("AT"));
    assertEquals(new AgentStartupMonitor.Counts(0, 0, 0, 0), monitor.counts("PF"));
  }

  @Test void failedInitializationCannotBeCountedAsReadyOrActive() {
    AgentStartupMonitor monitor = new AgentStartupMonitor();
    monitor.connected("AT", 1);
    monitor.failed("AT", 1, new IllegalStateException("invalid cache"));
    assertThrows(IllegalStateException.class, () -> monitor.initialized("AT", 1, "PRECOMPUTED"));
    assertThrows(IllegalStateException.class, () -> monitor.actionSubmitted("AT", 1, 1));
    assertEquals(new AgentStartupMonitor.Counts(1, 0, 0, 1), monitor.counts("AT"));
  }
}
