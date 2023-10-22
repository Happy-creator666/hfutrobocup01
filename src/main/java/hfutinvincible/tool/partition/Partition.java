package hfutinvincible.tool.partition;

import javax.annotation.Nonnull;
import java.util.stream.Stream;

public interface Partition<E> {
    @Nonnull
    Stream<E> getElements();

    boolean contains(@Nonnull E element);
}