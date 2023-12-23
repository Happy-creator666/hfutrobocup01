package hfutinvincible;

import java.util.*;

/**
 * An implementation of Ordered Set that support O(1) add(), remove() and removeRandom()
 */
public class OrderedSet<E> extends AbstractSet<E> implements Set<E>, Iterable<E> {
    private final List<E> list = new ArrayList<>();
    private final Map<E,Integer> indexMap = new HashMap<>();

    public OrderedSet() {
        super();
    }

    public OrderedSet(Collection<E> collection) {
        this.addAll(collection);
    }

    /**
     * Add a element
     * @param e
     * @return
     */
    @Override
    public boolean add(E e) {
        if (contains(e)) return false;
        indexMap.put(e, list.size());
        list.add(e);
        return true;
    }

    /**
     * Remove given element
     */
    @Override
    public boolean remove(Object o) {
        Integer indexBoxed = indexMap.remove(o);
        if (indexBoxed == null) return false;
        int index = indexBoxed;
        int last = list.size() - 1;
        E element = list.remove(last);
        if (index != last) {
            indexMap.put(element, index);
            list.set(index, element);
        }
        return true;
    }

    /**
     * Get element by index
     */
    public E get(int index) {
        return list.get(index);
    }

    /**
     * Get index of given element
     */
    public int getIndex(E e) {
        return indexMap.get(e);
    }

    /**
     * Remove a random element
     */
    public E removeRandom() {
        E element = list.get((int)(Math.random() * size()));
        remove(element);
        return element;
    }

    /**
     * Determine whether this set contains given element
     */
    @Override
    public boolean contains(Object o) {
        return indexMap.containsKey(o);
    }

    @Override
    public Iterator<E> iterator() {
        return list.iterator();
    }

    /**
     * Get size of set
     */
    @Override
    public int size() {
        return list.size();
    }
}