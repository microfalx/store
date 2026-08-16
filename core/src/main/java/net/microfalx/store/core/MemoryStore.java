package net.microfalx.store.core;

import com.google.common.collect.AbstractIterator;
import net.microfalx.lang.Identifiable;
import net.microfalx.lang.annotation.Order;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.resource.Resource;
import net.microfalx.store.api.Store;
import net.microfalx.store.api.StoreFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static net.microfalx.store.core.StoreUtils.getTimer;

public class MemoryStore<T extends Identifiable<ID>, ID> extends AbstractStore<T, ID> {

    private final Map<ID, byte[]> data = new ConcurrentHashMap<>();

    public MemoryStore(Options options, Resource resource) {
        super(options, resource);
    }

    @Override
    protected byte[] doReadData(ID id) throws Exception {
        return data.get(id);
    }

    @Override
    protected void doWriteData(ID id, byte[] data) throws Exception {
        this.data.put(id, data);
    }

    @Override
    protected void doRemove(ID id) {
        data.remove(id);
    }

    @Override
    protected void doFlush(AtomicLong count) {

    }

    @Override
    protected void doClear(AtomicLong count) {
        count.addAndGet(data.size());
    }

    @Override
    protected void doClose() {

    }

    @Override
    public long count(Location location) {
        return 0;
    }

    @Override
    public long size(Location location) {
        return 0;
    }

    @Override
    public Iterator<T> iterator() {
        return new IteratorImpl();
    }

    private class IteratorImpl extends AbstractIterator<T> {

        private final Iterator<byte[]> iterator;

        public IteratorImpl() {
            iterator = data.values().iterator();
        }

        @Override
        protected T computeNext() {
            return getTimer("Next", MemoryStore.this).record(() -> {
                if (!iterator.hasNext()) {
                    endOfData();
                    return null;
                } else {
                    return deserialize(iterator.next());
                }
            });
        }

    }

    @Provider
    @Order(Order.AFTER)
    public static class Factory<T extends Identifiable<ID>, ID> implements StoreFactory<T, ID> {

        @Override
        public Store<T, ID> create(Store.Options options, Resource directory) {
            return new MemoryStore<>(options, directory);
        }

        @Override
        public String toString() {
            return "Memory Store Factory";
        }
    }
}
