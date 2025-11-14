// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * An extension of the `java.util.ArrayList` that can be write-protected by calling the `makeReadOnly()` method.
 * This is designed to be equivalent to using `Collections.unmodifiableList()`, but it's more memory efficient
 * because this class doesn't require an extra wrapper object.
 * @param <E> the type of elements
 */
public class UnmodifiableArrayList<E> extends ArrayList<E> {
    private boolean isReadOnly = false;

    /**
     * Mark this list as read-only.
     * After this call, any mutating methods will throw the `UnsupportedOperationException`.
     */
    public void makeReadOnly() {
        isReadOnly = true;
    }

    private void checkReadOnly() {
        if (isReadOnly) {
            // Note that technically, this should be an IllegalStateException.
            // However, we throw UnsupportedOperationException for consistency with JDK unmodifiable collections.
            throw new UnsupportedOperationException("This list is read-only");
        }
    }

    @Override
    public E set(int index, E element) {
        checkReadOnly();
        return super.set(index, element);
    }

    @Override
    public boolean add(E o) {
        checkReadOnly();
        return super.add(o);
    }

    @Override
    public void add(int index, E element) {
        checkReadOnly();
        super.add(index, element);
    }

    @Override
    public void addFirst(E element) {
        checkReadOnly();
        super.addFirst(element);
    }

    @Override
    public void addLast(E element) {
        checkReadOnly();
        super.addLast(element);
    }

    @Override
    public E remove(int index) {
        checkReadOnly();
        return super.remove(index);
    }

    @Override
    public E removeFirst() {
        checkReadOnly();
        return super.removeFirst();
    }

    @Override
    public E removeLast() {
        checkReadOnly();
        return super.removeLast();
    }

    @Override
    public boolean remove(Object o) {
        checkReadOnly();
        return super.remove(o);
    }

    @Override
    public void clear() {
        checkReadOnly();
        super.clear();
    }

    @Override
    public boolean addAll(Collection c) {
        checkReadOnly();
        return super.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection c) {
        checkReadOnly();
        return super.addAll(index, c);
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        checkReadOnly();
        super.removeRange(fromIndex, toIndex);
    }

    @Override
    public boolean removeAll(Collection c) {
        checkReadOnly();
        return super.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection c) {
        checkReadOnly();
        return super.retainAll(c);
    }

    @Override
    public boolean removeIf(Predicate filter) {
        checkReadOnly();
        return super.removeIf(filter);
    }

    @Override
    public void replaceAll(UnaryOperator operator) {
        checkReadOnly();
        super.replaceAll(operator);
    }

    @Override
    public void sort(Comparator c) {
        checkReadOnly();
        super.sort(c);
    }

    @Override
    public ListIterator<E> listIterator() {
        return listIterator(0);
    }

    @Override
    public ListIterator<E> listIterator(final int index) {
        return new ListIterator<>() {
            private final ListIterator<E> i = UnmodifiableArrayList.super.listIterator(index);

            public boolean hasNext() {
                return i.hasNext();
            }

            public E next() {
                return i.next();
            }

            public boolean hasPrevious() {
                return i.hasPrevious();
            }

            public E previous() {
                return i.previous();
            }

            public int nextIndex() {
                return i.nextIndex();
            }

            public int previousIndex() {
                return i.previousIndex();
            }

            public void remove() {
                checkReadOnly();
                i.remove();
            }

            public void set(E e) {
                checkReadOnly();
                i.set(e);
            }

            public void add(E e) {
                checkReadOnly();
                i.add(e);
            }

            @Override
            public void forEachRemaining(Consumer<? super E> action) {
                i.forEachRemaining(action);
            }
        };
    }
}
