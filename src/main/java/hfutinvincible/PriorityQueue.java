package hfutinvincible;

import java.util.function.Function;

/**
 * Priority queue that support update priority in O(log n), based on binary heap
 * TODO support iterate
 */
public class PriorityQueue<V, P extends Comparable<P>> {

    BinaryHeap<Entry> bh;

    public PriorityQueue() {
        bh = new BinaryHeap<>();
    }

    public boolean isEmpty() {
        return bh.isEmpty();
    }

    public void add(V value, P priority) {
        bh.add(new Entry(value, priority));
    }

    public V poll() {
        return bh.poll().value;
    }

    public V peek() {
        return bh.peek().value;
    }

    public V find(Function<V, Boolean> predicate) {
        return bh.find(p -> predicate.apply(p.value)).value;
    }

    public void findAndUpdatePriority(Function<V, Boolean> predicate, P newPriority) {
        bh.findAndApply(
                p -> predicate.apply(p.value),
                p -> new Entry(p.value, newPriority)
        );
    }

    public class Entry implements Comparable<Entry> {
        V value;
        P priority;

        public Entry(V value, P priority) {
            this.value = value;
            this.priority = priority;
        }


        @Override
        public int compareTo(Entry o) {
            // max heap about priority
            return o.priority.compareTo(this.priority);
        }
    }
}