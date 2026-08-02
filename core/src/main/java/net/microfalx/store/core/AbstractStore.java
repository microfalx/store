package net.microfalx.store.core;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.AbstractIterator;
import net.microfalx.lang.Identifiable;
import net.microfalx.lang.ObjectUtils;
import net.microfalx.lang.TimeUtils;
import net.microfalx.lang.Timestampable;
import net.microfalx.resource.Resource;
import net.microfalx.store.api.Query;
import net.microfalx.store.api.Store;
import net.microfalx.store.api.StoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.store.core.StoreUtils.*;

public abstract class AbstractStore<T extends Identifiable<ID>, ID> implements Store<T, ID> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStore.class);

    static private final ThreadLocal<Kryo> KRYOS = new ThreadLocal<Kryo>() {
        protected Kryo initialValue() {
            Kryo kryo = new Kryo();
            kryo.setRegistrationRequired(false);
            return kryo;
        }
    };

    private final Resource resource;
    private final Store.Options options;
    protected boolean external;

    public AbstractStore(Options options, Resource resource) {
        requireNonNull(options);
        requireNonNull(resource);
        this.options = options;
        this.resource = resource;
    }

    @Override
    public String getId() {
        return options.getId();
    }

    public String getName() {
        return options.getName();
    }

    @Override
    public Resource getDirectory() {
        return resource;
    }

    @Override
    public Options getOptions() {
        return options;
    }

    @Override
    public void add(T item) {
        if (item == null) return;
        getTimer(StoreUtils.ADD_ACTION, this).record(() -> {
            byte[] data = serialize(item);
            writeContent(item.getId(), data);
        });
    }

    @Override
    public void remove(T item) {
        if (item == null) return;
        remove(item.getId());
    }

    @Override
    public void remove(ID id) {
        requireNonNull(id);
        getTimer(StoreUtils.REMOVE_ACTION, this).record(() -> {
            doRemove(id);

        });
    }

    @Override
    public T find(ID id) {
        requireNonNull(id);
        return getTimer(StoreUtils.FIND_ACTION, this).record(() -> {
            byte[] data = readData(id);
            if (data == null) {
                return null;
            } else {
                return deserialize(data);
            }
        });
    }

    @Override
    public Collection<T> list(Query<T> query) {
        Collection<T> objects = new ArrayList<>();
        walk(query, t -> {
            objects.add(t);
            return true;
        });
        return objects;
    }

    @Override
    public void walk(Query<T> query, Function<T, Boolean> callback) {
        requireNonNull(query);
        requireNonNull(callback);
        LocalDateTime start = query.getStart();
        LocalDateTime end = query.getEnd();
        Predicate<T> filter = query.getFilter();
        getTimer(StoreUtils.WALK_ACTION, this).record(() -> {
            Iterator<T> iterator = iterator();
            while (iterator.hasNext()) {
                T object = iterator.next();
                if (start != null && end != null && !isBetween(object, start, end)) continue;
                if (filter != null && !filter.test(object)) continue;
                if (!callback.apply(object)) break;
            }
        });
    }

    @Override
    public void update(Query<T> query, Function<T, Boolean> callback) {
        walk(query, t -> {
            Boolean changed = callback.apply(t);
            if (Boolean.TRUE.equals(changed)) {
                add(t);
            } else if (Boolean.FALSE.equals(changed)) {
                return false;
            }
            return true;
        });
    }


    @Override
    public long clear() {
        flush();
        AtomicLong count = new AtomicLong();
        getTimer(CLEAR_ACTION, this).record((t) -> {
            doClear(count);
        });
        return count.get();
    }

    @Override
    public void purge() {

    }

    @Override
    public void flush() {
        AtomicLong count = new AtomicLong();
        getTimer(FLUSH_ACTION, this).record((t) -> {
            doFlush(count);
        });
    }

    @SuppressWarnings("unchecked")
    protected final T deserialize(byte[] data) {
        if (data == null) return null;
        Kryo kryo = KRYOS.get();
        ByteArrayInputStream buffer = new ByteArrayInputStream(data);
        Input input = new Input(buffer);
        return (T) kryo.readClassAndObject(input);
    }

    protected final byte[] serialize(T item) {
        if (item == null) return null;
        Kryo kryo = KRYOS.get();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Output output = new Output(buffer);
        kryo.writeClassAndObject(output, item);
        output.close();
        return buffer.toByteArray();
    }

    protected abstract byte[] doReadData(ID id) throws Exception;

    protected abstract void doWriteData(ID id, byte[] data) throws Exception;

    protected abstract void doRemove(ID id);

    protected abstract void doFlush(AtomicLong count);

    protected abstract void doClear(AtomicLong count);

    protected abstract void doClose();

    void close() {
        flush();
        doClose();
    }

    void cleanup() {
    }


    private boolean isBetween(T object, LocalDateTime start, LocalDateTime end) {
        if (!(object instanceof Timestampable)) return true;
        Timestampable<? extends Temporal> timestampable = (Timestampable<? extends Temporal>) object;
        return TimeUtils.isBetween(timestampable.getModifiedAt(), start, end);
    }

    private byte[] readData(ID id) {
        try {
            return doReadData(id);
        } catch (Exception e) {
            throw new StoreException("Failed to read item " + id + "'", e);
        }
    }

    private void writeContent(ID id, byte[] data) {
        try {
            doWriteData(id, data);
        } catch (Exception e) {
            throw new StoreException("Failed to write item " + id + "'", e);
        }
    }


}
