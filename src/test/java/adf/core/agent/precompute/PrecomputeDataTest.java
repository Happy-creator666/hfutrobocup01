package adf.core.agent.precompute;

import adf.core.agent.info.WorldInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import rescuecore2.standard.entities.Edge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardWorldModel;
import rescuecore2.worldmodel.EntityID;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrecomputeDataTest {
  @TempDir Path temporary;

  private WorldInfo world(int x) {
    StandardWorldModel model = new StandardWorldModel();
    Road road = new Road(new EntityID(1));
    road.setX(x);
    road.setY(0);
    road.setEdges(List.of(new Edge(0, 0, 10, 0), new Edge(10, 0, 0, 10), new Edge(0, 10, 0, 0)));
    model.addEntity(road);
    return new WorldInfo(model);
  }

  @Test void successfulGenerationRoundTripsPayloadAndManifest() {
    String file = temporary.resolve("cache.bin").toString();
    WorldInfo world = world(0);
    PrecomputeData generated = new PrecomputeData(file, false);
    generated.generateAndWrite(world, () -> {
      generated.setInteger("answer", 42);
      generated.setIntegerList("ids", List.of(1, 2));
      generated.markModuleComplete("test.tool");
    });
    PrecomputeData restored = new PrecomputeData(file, true);
    assertTrue(restored.isReady(world));
    assertEquals(42, restored.getInteger("answer"));
    assertEquals(List.of(1, 2), restored.getIntegerList("ids"));
    assertDoesNotThrow(() -> restored.requireCompletedModule("test.tool"));
    assertThrows(IllegalStateException.class, () -> restored.requireCompletedModule("missing.tool"));
    assertTrue(restored.copy().isReady(world));
  }

  @Test void failedGenerationPreservesPreviouslyValidFile() throws Exception {
    Path file = temporary.resolve("cache.bin");
    WorldInfo world = world(0);
    PrecomputeData data = new PrecomputeData(file.toString(), false);
    data.generateAndWrite(world, () -> data.markModuleComplete("old.tool"));
    byte[] previous = Files.readAllBytes(file);

    assertThrows(IllegalStateException.class, () -> data.generateAndWrite(world, () -> {
      data.markModuleComplete("partial.tool");
      throw new IllegalStateException("a tool failed");
    }));
    assertFalse(data.isReady(world));
    assertFalse(data.write());
    assertArrayEquals(previous, Files.readAllBytes(file));
    PrecomputeData restored = new PrecomputeData(file.toString(), true);
    assertTrue(restored.isReady(world));
    assertDoesNotThrow(() -> restored.requireCompletedModule("old.tool"));
    assertThrows(IllegalStateException.class, () -> restored.requireCompletedModule("partial.tool"));
    try (var files = Files.list(temporary)) {
      assertEquals(1L, files.count(), "temporary publication files must be removed");
    }
  }

  @Test void regenerationDoesNotRetainOldCompletionMarkers() {
    PrecomputeData data = new PrecomputeData(temporary.resolve("cache.bin").toString(), false);
    data.generateAndWrite(world(0), () -> data.markModuleComplete("old.tool"));
    data.generateAndWrite(world(0), () -> data.markModuleComplete("new.tool"));
    assertThrows(IllegalStateException.class, () -> data.requireCompletedModule("old.tool"));
  }

  @Test void fingerprintRejectsDifferentMapWithSameEntityCount() {
    PrecomputeData data = new PrecomputeData(temporary.resolve("cache.bin").toString(), false);
    data.generateAndWrite(world(0), () -> {});
    assertTrue(data.isReady(world(0)));
    assertFalse(data.isReady(world(100)));
  }

  @Test void cacheReadSwitchBypassesEvenValidCacheWithoutDeletingIt() throws Exception {
    Path file = temporary.resolve("cache.bin");
    PrecomputeData data = new PrecomputeData(file.toString(), false);
    data.generateAndWrite(world(0), () -> {});
    byte[] previous = Files.readAllBytes(file);
    String previousProperty = System.getProperty(PrecomputeData.READ_CACHE_PROPERTY);
    try {
      System.setProperty(PrecomputeData.READ_CACHE_PROPERTY, "false");
      assertFalse(new PrecomputeData(file.toString()).isReady(world(0)));
      assertArrayEquals(previous, Files.readAllBytes(file));
      System.setProperty(PrecomputeData.READ_CACHE_PROPERTY, "typo");
      assertThrows(IllegalArgumentException.class, () -> new PrecomputeData(file.toString()));
    } finally {
      if (previousProperty == null) System.clearProperty(PrecomputeData.READ_CACHE_PROPERTY);
      else System.setProperty(PrecomputeData.READ_CACHE_PROPERTY, previousProperty);
    }
  }

  @Test void legacyMalformedAndNullPayloadsAreNotReady() throws Exception {
    Path file = temporary.resolve("cache.bin");
    ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());
    PreData legacy = new PreData();
    legacy.isReady = true;
    legacy.readyID = "old weak map identifier";
    mapper.writeValue(file.toFile(), legacy);
    assertFalse(new PrecomputeData(file.toString(), true).isReady(world(0)));

    Files.write(file, new byte[]{(byte) 0xc1});
    assertFalse(new PrecomputeData(file.toString(), true).isReady(world(0)));

    PrecomputeData valid = new PrecomputeData(file.toString(), false);
    valid.generateAndWrite(world(0), () -> {});
    PreData broken = mapper.readValue(file.toFile(), PreData.class);
    broken.intValues = null;
    mapper.writeValue(file.toFile(), broken);
    assertFalse(new PrecomputeData(file.toString(), true).isReady(world(0)));
  }

  @Test void failedPublicationDoesNotLeaveReadyInMemory() throws Exception {
    Path directory = Files.createDirectory(temporary.resolve("not-a-file"));
    Files.writeString(directory.resolve("keep.txt"), "keep");
    PrecomputeData data = new PrecomputeData(directory.toString(), false);
    assertThrows(IllegalStateException.class, () -> data.generateAndWrite(world(0), () -> {}));
    assertFalse(data.isReady(world(0)));
    assertEquals("keep", Files.readString(directory.resolve("keep.txt")));
  }
}
