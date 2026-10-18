package hfutinvincible.tool.executor;

import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.launcher.ConfigKey;
import hfutinvincible.debug.Painter;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.path.SearchPathTool;
import org.junit.jupiter.api.Test;
import rescuecore2.config.Config;
import rescuecore2.standard.entities.Edge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardWorldModel;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AtExecutorAfterCalcTest {
  private AtExecutor executor(boolean debug) {
    AtExecutor executor = new AtExecutor();
    Config config = new Config();
    config.setBooleanValue(ConfigKey.KEY_DEBUG_FLAG, debug);
    executor.scenarioInfo = new ScenarioInfo(config);
    executor.searchPathTool = new SearchPathTool() {
      @Override public SearchPathTool calc() {
        throw new AssertionError("afterCalc must never run pathfinding");
      }
    };
    return executor;
  }

  @Test void competitionAndEmptyPainterPathsDoNotSearchOrRequireWorldState() {
    CalcContext context = new CalcContext();
    context.setTargetPath(List.of(new EntityID(1)));
    assertDoesNotThrow(() -> executor(false).afterCalc(context));
    assertDoesNotThrow(() -> executor(true).afterCalc(context));
  }

  @Test void renderingUsesSelectedPathAndDoesNotChangeDecision() {
    AtExecutor executor = executor(true);
    StandardWorldModel model = new StandardWorldModel();
    Road road = new Road(new EntityID(1));
    road.setEdges(List.of(new Edge(0, 0, 10, 0), new Edge(10, 0, 0, 10), new Edge(0, 10, 0, 0)));
    model.addEntity(road);
    executor.worldInfo = new WorldInfo(model);
    CalcContext context = new CalcContext();
    List<EntityID> path = List.of(road.getID());
    context.setTargetPath(path);
    CountingPainter painter = new CountingPainter();
    executor.drawTargetPath(context, painter);
    assertEquals(1, painter.count);
    assertSame(path, context.getTargetPath());
    assertNull(context.getResultAction());
    executor.drawTargetPath(new CalcContext(), painter);
    assertEquals(1, painter.count);
  }

  private static final class CountingPainter implements Painter<Void> {
    private int count;
    @Override public Void addPaintables(String name, boolean persist, Paintable... paintables) {
      count += paintables.length;
      return null;
    }
    @Override public Void addPaintables(String name, boolean persist, Collection<Paintable> paintables) {
      count += paintables.size();
      return null;
    }
  }
}
