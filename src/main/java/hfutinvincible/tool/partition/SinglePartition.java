package hfutinvincible.tool.partition;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.stream.Stream;

public class SinglePartition<E> implements Partition<E> {

    private final int id;
    private final Set<E> elements;
    private final E centerElement;

    public SinglePartition(
            int id,
            @Nonnull Set<E> elements,
            @Nonnull E centerElement
    ) {
        this.id = id;
        this.elements = elements;
        this.centerElement = centerElement;
    }

    @Nonnull
    @Override
    public Stream<E> getElements() {
        return elements.stream();
    }

    @Override
    public boolean contains(@Nonnull E element) {
        return elements.contains(element);
    }

    public int getId() {
        return id;
    }

    public E getCenterElement() {
        return centerElement;
    }
}