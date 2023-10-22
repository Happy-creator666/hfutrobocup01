package hfutinvincible.tool.partition;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class MultiplePartition<E> implements Partition<E> {

    private final List<SinglePartition<E>> innerPartitions;

    public MultiplePartition(@Nonnull Iterable<Partition<E>> partitions) {
        List<SinglePartition<E>> innerPartitions = new ArrayList<>();
        for (Partition<E> partition : partitions) {
            if (partition instanceof SinglePartition<E> singlePartition)
                innerPartitions.add(singlePartition);
            else if (partition instanceof MultiplePartition<E> multiplePartition)
                innerPartitions.addAll(multiplePartition.innerPartitions);
        }
        this.innerPartitions = innerPartitions;
    }

    @SafeVarargs
    public MultiplePartition(@Nonnull Partition<E>... partitions) {
        this(Arrays.stream(partitions).toList());
    }

    public MultiplePartition(@Nonnull Stream<Partition<E>> partitions) {
        this(partitions.toList());
    }

    @Nonnull
    @Override
    public Stream<E> getElements() {
        return innerPartitions.stream()
                .flatMap(Partition::getElements);
    }

    @Override
    public boolean contains(@Nonnull E element) {
        return innerPartitions.stream()
                .anyMatch(singlePartition -> singlePartition.contains(element));
    }
}