package hfutinvincible;

import java.util.Objects;

public abstract class Tuple {

    public abstract <T> T get(int i)  throws IndexOutOfBoundsException;
    public abstract <T> void set(int i, T newVal)  throws IndexOutOfBoundsException;
    public abstract int getElementNum();

    @SuppressWarnings("unchecked")
    public static <T extends Tuple> T of(Object... elements) {
        if (elements.length == 2)
            return (T) Tuple2.of(elements[0], elements[1]);
        if (elements.length == 3)
            return (T) Tuple3.of(elements[0], elements[1], elements[2]);
        throw new IllegalArgumentException("Illegal number of elements. " + elements.length + " of [1, 2].");
    }

    public final Object[] toArray() {
        Object[] ret = new Object[getElementNum()];
        for (int i = 0; i < getElementNum(); i ++)
            ret[i] = get(i);
        return ret;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tuple tuple = (Tuple) o;
        for (int i = 0; i < getElementNum(); i ++)
            if (!Objects.equals(get(i), tuple.get(i)))
                return false;
        return true;
    }

    @Override
    public final int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        for (int i = 0; i < getElementNum(); i ++)
            builder.append((Object) get(i));
        return builder.build();
    }

    @Override
    public final String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < getElementNum() - 1; i ++) {
            sb.append(get(i).toString());
            sb.append(", ");
        }
        sb.append(get(getElementNum() - 1).toString());
        sb.append(")");
        return sb.toString();
    }
}
