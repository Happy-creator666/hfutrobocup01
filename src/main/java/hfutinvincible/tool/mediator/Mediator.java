package hfutinvincible.tool.mediator;

import javax.annotation.Nonnull;

@FunctionalInterface
public interface Mediator<T> {
    boolean isAllowed(@Nonnull T target);
}