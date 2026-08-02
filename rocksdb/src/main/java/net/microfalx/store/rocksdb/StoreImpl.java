package net.microfalx.store.rocksdb;

import com.google.common.collect.AbstractIterator;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.lang.Identifiable;
import net.microfalx.lang.ObjectUtils;
import net.microfalx.resource.FileResource;
import net.microfalx.resource.Resource;
import net.microfalx.resource.rocksdb.RocksDbManager;
import net.microfalx.store.api.StoreException;
import net.microfalx.store.core.AbstractStore;
import org.rocksdb.*;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.store.core.StoreUtils.METRICS_FAILURES;
import static net.microfalx.store.core.StoreUtils.getTimer;

@Slf4j
public class StoreImpl<T extends Identifiable<ID>, ID> extends AbstractStore<T, ID> {

    private final RocksDB db;

    public StoreImpl(Options options, Resource resource) {
        super(options, resource);
        this.db = RocksDbManager.getInstance().create(((FileResource) resource.toFile()).getFile());
    }

    public StoreImpl(Options options, Resource resource, RocksDB db) {
        super(options, resource);
        requireNonNull(db);
        this.db = db;
    }

    @Override
    protected void doRemove(ID id) {
        try {
            db.delete(ObjectUtils.toString(id).getBytes());
        } catch (Exception e) {
            throw new StoreException("Failed to remove item " + id + "'", e);
        }
    }

    @Override
    public long count(Location location) {
        requireNonNull(location);
        return switch (location) {
            case MEMORY -> RocksDbManager.getMemoryCount(db);
            case DISK -> RocksDbManager.getDiskCount(db);
        };
    }

    @Override
    public long size(Location location) {
        requireNonNull(location);
        return switch (location) {
            case MEMORY -> RocksDbManager.getMemorySize(db);
            case DISK -> RocksDbManager.getDiskSize(db);
        };
    }

    @Override
    protected byte[] doReadData(ID id) throws Exception {
        String idAsString = ObjectUtils.toString(id);
        ReadOptions options = new ReadOptions();
        return db.get(options, idAsString.getBytes());
    }

    @Override
    protected void doWriteData(ID id, byte[] data) throws Exception {
        String idAsString = ObjectUtils.toString(id);
        WriteOptions options = new WriteOptions();
        db.put(options, idAsString.getBytes(), data);
    }

    @Override
    protected void doFlush(AtomicLong count) {
        try {
            db.flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e) {
            LOGGER.warn("Failed to close the ");
        }
    }

    @Override
    protected void doClear(AtomicLong count) {
        RocksIterator iterator = db.newIterator();
        iterator.seekToFirst();
        while (iterator.isValid()) {
            count.incrementAndGet();
            try {
                db.delete(iterator.key());
            } catch (RocksDBException e) {
                METRICS_FAILURES.count(getName());
            }
            iterator.next();
        }
    }

    @Override
    protected void doClose() {
        try {
            if (!external) db.close();
        } catch (Exception e) {
            LOGGER.atWarn().setCause(e).log("Failed to close the store {}", getDirectory());
        }
    }

    @Override
    public Iterator<T> iterator() {
        return new IteratorImpl();
    }

    private class IteratorImpl extends AbstractIterator<T> {

        private final RocksIterator iterator;

        public IteratorImpl() {
            iterator = db.newIterator();
            iterator.seekToFirst();
        }

        @Override
        protected T computeNext() {
            return getTimer("Next", StoreImpl.this).record(() -> {
                if (!iterator.isValid()) {
                    endOfData();
                    return null;
                } else {
                    T value = deserialize(iterator.value());
                    iterator.next();
                    return value;
                }
            });
        }

    }
}
