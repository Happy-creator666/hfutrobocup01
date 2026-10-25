package hfutinvincible.tool.partition;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.platoon.PlatoonAmbulance;
import adf.core.agent.precompute.PreData;
import adf.core.agent.precompute.PrecomputeData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Edge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardWorldModel;
import rescuecore2.worldmodel.EntityID;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StaticPartitionCacheTest {
  @TempDir Path temporary;
  private static final String PREFIX = StaticPartitionTool.class.getName() + ":";

  private PreData validPayload() {
    PreData data = new PreData();
    data.formatVersion = PrecomputeData.FORMAT_VERSION;
    data.intValues.put(PREFIX + "StaticPartitionTool.numPartitions", 1);
    data.idLists.put(PREFIX + "StaticPartitionTool.partition0.elements", List.of(1));
    data.idValues.put(PREFIX + "StaticPartitionTool.partition0.centerElement", 1);
    data.intLists.put(PREFIX + "StaticPartitionTool.partition0.neighbors", List.of());
    data.idLists.put(PREFIX + "StaticPartitionTool.agentAssignedPartition.keys", List.of(2));
    data.intLists.put(PREFIX + "StaticPartitionTool.agentAssignedPartition.values", List.of(0));
    data.doubleValues.put(PREFIX + "StaticPartitionTool.avgDistanceBetweenCenters", 0.0);
    data.intValues.put(PREFIX + "avgSizeOfPartition", 1);
    return data;
  }

  private PrecomputeData cache(PreData payload) throws Exception {
    Path file = temporary.resolve("partitions.bin");
    new ObjectMapper(new MessagePackFactory()).writeValue(file.toFile(), payload);
    return new PrecomputeData(file.toString(), true);
  }

  private StaticPartitionTool tool() {
    StandardWorldModel world = new StandardWorldModel();
    Road road = new Road(new EntityID(1));
    road.setX(0);
    road.setY(0);
    road.setEdges(List.of(new Edge(0, 0, 10, 0), new Edge(10, 0, 0, 10), new Edge(0, 10, 0, 0)));
    AmbulanceTeam human = new AmbulanceTeam(new EntityID(2));
    human.setPosition(road.getID());
    world.addEntity(road);
    world.addEntity(human);
    PlatoonAmbulance agent = new PlatoonAmbulance(null, "test", false, false, null, null) {
      @Override public EntityID getID() { return human.getID(); }
    };
    StaticPartitionTool tool = new StaticPartitionTool();
    tool.worldInfo = new WorldInfo(world);
    tool.agentInfo = new AgentInfo(agent, world);
    tool.logger = Logger.getLogger(StaticPartitionCacheTest.class);
    return tool;
  }

  @Test void validRestoreIsRepeatableWithoutAccumulatingPartitions() throws Exception {
    StaticPartitionTool tool = tool();
    PrecomputeData cache = cache(validPayload());
    tool.resume(cache);
    tool.resume(cache);
    assertEquals(0, tool.getAssignedPartitionId());
    assertTrue(tool.getPartition(0).contains(new EntityID(1)));
    assertThrows(IndexOutOfBoundsException.class, () -> tool.getPartition(1));
  }

  @Test void incompleteRestoreDoesNotReplacePreviouslyInitializedState() throws Exception {
    StaticPartitionTool tool = tool();
    tool.resume(cache(validPayload()));
    Partition<EntityID> previous = tool.getPartition(0);
    PreData broken = validPayload();
    broken.intLists.remove(PREFIX + "StaticPartitionTool.partition0.neighbors");
    assertThrows(IllegalStateException.class, () -> tool.resume(cache(broken)));
    assertSame(previous, tool.getPartition(0));
  }

  @Test void outOfRangeAssignmentsFailBeforeApplyingAnyPartition() throws Exception {
    StaticPartitionTool tool = tool();
    PreData broken = validPayload();
    broken.intLists.put(PREFIX + "StaticPartitionTool.agentAssignedPartition.values", List.of(1));
    assertThrows(IllegalStateException.class, () -> tool.resume(cache(broken)));
    assertThrows(IndexOutOfBoundsException.class, () -> tool.getPartition(0));
  }
}
