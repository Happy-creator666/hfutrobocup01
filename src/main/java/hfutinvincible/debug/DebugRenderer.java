package hfutinvincible.debug;

import hfutinvincible.Tuple2;
import hfutinvincible.debug.paintable.Paintable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

public interface DebugRenderer {

    /**
     * 根据 DebugData 返回一系列可供绘制的 Paintable 对象，以及目标图层名
     * @param debugData 调试数据
     * @return 目标图层名与一系列可供绘制的 Paintable 对象
     */
    @Nullable
    Tuple2<String, Collection<Paintable>> render(@Nonnull DebugData debugData);
}
