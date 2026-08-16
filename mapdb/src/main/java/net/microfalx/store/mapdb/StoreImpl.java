package net.microfalx.store.mapdb;

import com.google.common.collect.AbstractIterator;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.lang.Identifiable;
import net.microfalx.lang.ObjectUtils;
import net.microfalx.resource.Resource;
import net.microfalx.resource.ResourceUtils;
import net.microfalx.store.api.StoreException;
import net.microfalx.store.core.AbstractStore;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.store.core.StoreUtils.getTimer;

@Slf4j
public class StoreImpl<T extends Identifiable<ID>, ID> extends AbstractStore<T, ID> {

    private static final String DEFAULT_MAP = "default";

    private final DB db;
    private final Map<String, byte[]> map;

    public StoreImpl(Options options, Resource resource) {
        super(options, resource);
        this.db = DBMaker.fileDB(ResourceUtils.toFile(resource))
                .fileMmapEnableIfSupported()
                .fileMmapPreclearDisable()
                .transactionEnable()
                .make();
        this.map = initDefaultMap();
    }

    public StoreImpl(Options options, Resource resource, DB db) {
        super(options, resource);
        requireNonNull(db);
        this.db = db;
        this.map = initDefaultMap();
    }

    @Override
    protected void doRemove(ID id) {
        try {
            String idAsString = ObjectUtils.toString(id);
            this.map.remove(idAsString);
        } catch (Exception e) {
            throw new StoreException("Failed to remove item " + id + "'", e);
        }
    }

    @Override
    public long count(Location location) {
        requireNonNull(location);
        return switch (location) {
            case MEMORY -> 0L;
            case DISK -> this.map.size();
        };
    }

    @Override
    public long size(Location location) {
        requireNonNull(location);
        return switch (location) {
            case MEMORY -> 0L;
            case DISK -> {
                try {
                    yield getResource().length();
                } catch (Exception e) {
                    yield -1L;
                }
            }
        };
    }

    @Override
    protected byte[] doReadData(ID id) throws Exception {
        String idAsString = ObjectUtils.toString(id);
        return this.map.get(idAsString);
    }

    @Override
    protected void doWriteData(ID id, byte[] data) throws Exception {
        String idAsString = ObjectUtils.toString(id);
        this.map.put(idAsString, data);
    }

    @Override
    protected void doFlush(AtomicLong count) {
        try {
            // no flush needed for mapdb
        } catch (Exception e) {
            LOGGER.warn("Failed to close the ");
        }
    }

    @Override
    protected void doClear(AtomicLong count) {
        count.addAndGet(this.map.size());
        this.map.clear();
    }

    @Override
    protected void doClose() {
        try {
            if (!external) db.close();
        } catch (Exception e) {
            LOGGER.atWarn().setCause(e).log("Failed to close the store {}", getResource());
        }
    }

    @Override
    public Iterator<T> iterator() {
        return new IteratorImpl();
    }

    private Map<String, byte[]> initDefaultMap() {
        DB.HashMapMaker<String, byte[]> mapMaker = db.hashMap(DEFAULT_MAP)
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.BYTE_ARRAY)
                .counterEnable();
        return mapMaker.createOrOpen();
    }

    private class IteratorImpl extends AbstractIterator<T> {

        private final Iterator<byte[]> iterator;

        public IteratorImpl() {
            iterator = map.values().iterator();
        }

        @Override
        protected T computeNext() {
            return getTimer("Next", StoreImpl.this).record(() -> {
                if (!iterator.hasNext()) {
                    endOfData();
                    return null;
                } else {
                    return deserialize(iterator.next());
                }
            });
        }

    }
}
