package hfutinvincible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class BinaryHeap<T extends Comparable<T>> {

    List<T> buffer = new ArrayList<>();

    /**
     * Determine whether the heap is empty
     */
    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    /**
     * Insert a new element
     */
    public void add(T value) {
        buffer.add(value);
        shiftUp(buffer.size() - 1);
    }

    /**
     * Remove the minimum element and return it
     */
    public T poll() {
        if (buffer.size() <= 0) {
            return null;
        }
        if (buffer.size() == 1) {
            return buffer.remove(0);
        }
        Collections.swap(buffer, 0, buffer.size() - 1);
        T ret = buffer.remove(buffer.size() - 1);
        shiftDown(0);
        return ret;
    }

    /**
     * Peek the root
     */
    public T peek() {
        return buffer.get(0);
    }

    /**
     * Find an element by given predicate function
     */
    public T find(Function<T, Boolean> predicate) {
        for (T elem: buffer) {
            if (predicate.apply(elem)) {
                return elem;
            }
        }
        return null;
    }

    /**
     * Find an element by given predicate, and change its value
     */
    public void findAndChange(Function<T, Boolean> predicate, T newValue) {
        for (int i=0; i<buffer.size(); i++) {
            T elem = buffer.get(i);
            if (predicate.apply(elem)) {
                if (newValue.compareTo(elem) < 0) {
                    buffer.set(i, newValue);
                    shiftUp(i);
                } else if (newValue.compareTo(elem) > 0) {
                    buffer.set(i, newValue);
                    shiftDown(i);
                } else {
                    buffer.set(i, newValue);
                }
            }
        }
    }

    /**
     * Find an element by given predicate, and apply f on it
     */
    public void findAndApply(Function<T, Boolean> predicate, Function<T, T> f) {
        for (int i=0; i<buffer.size(); i++) {
            T elem = buffer.get(i);
            if (predicate.apply(elem)) {
                buffer.set(i, f.apply(elem));
            }
        }
    }

    private void shiftUp(int current) {
        while (true) {
            int fa = (current - 1) / 2;
            if (fa < 0) break;
            if (buffer.get(fa).compareTo(buffer.get(current)) <= 0) break;
            Collections.swap(buffer, fa, current);
            current = fa;
        }
    }

    private void shiftDown(int current) {
        while (true) {
            int leftChild = (current + 1) * 2 - 1;
            int rightChild = (current + 1) * 2;

            int smallerChild;
            if (leftChild >= buffer.size()) break;
            if (rightChild >= buffer.size()) {
                smallerChild = leftChild;
            } else {
                smallerChild = buffer.get(leftChild).compareTo(buffer.get(rightChild)) < 0 ? leftChild : rightChild;
            }

            if (buffer.get(current).compareTo(buffer.get(smallerChild)) < 0) break;
            Collections.swap(buffer, current, smallerChild);
            current = smallerChild;
        }
    }
}