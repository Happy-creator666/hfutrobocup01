package hfutinvincible.common;

import javax.swing.*;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListSet;

public class SortedListModel<T> extends AbstractListModel<T> {

    private ConcurrentSkipListSet<T> model;

    public SortedListModel(Comparator<T> comparator) {
        this.model = new ConcurrentSkipListSet<>(comparator);
    }


    @Override
    public int getSize() {
        return model.size();
    }

    @Override
    public T getElementAt(int index) {
        Iterator<T> iter = model.iterator();
        while (index-- > 0)
            iter.next();
        return iter.next();
    }

    public void add(T element) {
        if (model.add(element)) {
            fireContentsChanged(this, 0, getSize());
        }
    }

    public void addAll(Collection<T> elements) {
        if (model.addAll(elements)) {
            fireContentsChanged(this, 0, getSize());
        }
    }

    public void clear() {
        model.clear();
        fireContentsChanged(this, 0, getSize());
    }

    public boolean contains(T element) {
        return model.contains(element);
    }

    public T firstElement() {
        return model.first();
    }

    public T lastElement() {
        return model.last();
    }

    public Iterator<T> iterator() {
        return model.iterator();
    }
}