package net.microfalx.store.core;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.lang.*;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.resource.Resource;
import net.microfalx.store.api.Store;
import net.microfalx.store.api.StoreException;
import net.microfalx.store.api.StoreFactory;
import net.microfalx.store.api.StoreService;
import net.microfalx.threadpool.AbstractRunnable;
import net.microfalx.threadpool.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.unmodifiableCollection;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.FormatterUtils.formatDuration;
import static net.microfalx.lang.StringUtils.joinNames;
import static net.microfalx.lang.StringUtils.toIdentifier;

/**
 * A service responsible for managing a collection of {@link Store}.
 */
@Provider
@Slf4j
public class StoreServiceImpl implements StoreService, Initializable, Releasable {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreServiceImpl.class);

    private StoreSettings properties = new StoreSettings();

    private ThreadPool threadPool = ThreadPool.get();

    private StoreFactory<?, ?> storeFactory;
    private volatile Resource directory;
    private final Map<String, Store<?, ?>> stores = new ConcurrentHashMap<>();

    public ThreadPool getThreadPool() {
        if (threadPool == null) threadPool = ThreadPool.get();
        return threadPool;
    }

    @Override
    public StoreFactory<?, ?> getStoreFactory() {
        return storeFactory;
    }

    @Override
    public Resource getDirectory() {
        if (directory == null) {
            directory = Resource.directory(JvmUtils.getVariableDirectory("store"));
        }
        return directory;
    }

    @Override
    public void setResource(Resource directory) {
        requireNonNull(directory);
        LOGGER.info("Change storage directory to {}", directory);
        this.directory = directory;
    }

    /**
     * Registers a new store.
     *
     * @param options the options
     */
    @SuppressWarnings("unchecked")
    public <T extends Identifiable<ID>, ID> Store<T, ID> register(Store.Options options) {
        requireNonNull(options);
        LOGGER.info("Register store '{}', retention '{}'", options.getName(), formatDuration(options.getRetention()));
        Resource resource = getDirectory().resolve(options.getId(), Resource.Type.DIRECTORY);
        Store<T, ID> store = (Store<T, ID>) storeFactory.create(options, resource);
        stores.put(options.getId(), store);
        return store;
    }

    @Override
    public <T extends Identifiable<ID>, ID> Store<T, ID> register(Store<T, ID> store) {
        requireNonNull(store);
        LOGGER.info("Register external store '{}', retention '{}'", store.getName(), formatDuration(store.getOptions().getRetention()));
        stores.putIfAbsent(store.getId(), store);
        return store;
    }

    /**
     * Returns a store with a given identifier.
     *
     * @param id   the store identifier
     * @param <ID> the identifier type
     * @param <T>  the item type
     * @return a non-null instance
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends Identifiable<ID>, ID> Store<T, ID> getStore(String id) {
        requireNonNull(id);
        Store store = stores.get(toIdentifier(id));
        if (store == null) throw new StoreException("A store with identifier '" + id + "' is not registered");
        return store;
    }

    /**
     * Flushes all stores to disk.
     */
    public void flush() {
        LOGGER.info("Flush stores");
        for (Store<?, ?> store : stores.values()) {
            try {
                store.flush();
            } catch (Exception e) {
                LOGGER.atError().setCause(e).log("Failed to flush store '{}'", store.getName());
            }
        }
    }

    /**
     * Returns registered stores.
     *
     * @return a non-null instance
     */
    public Collection<Store<?, ?>> getStores() {
        return unmodifiableCollection(stores.values());
    }

    @Override
    public void initialize(Object... context) {
        initTasks();
        discoverStoreFactory();
    }

    @Override
    public void release() {
        LOGGER.info("Shutdown stores:");
        for (Store<?, ?> store : stores.values()) {
            LOGGER.info(" - {}", store.getName());
            try {
                ((AbstractStore<?, ?>) store).close();
            } catch (Exception e) {
                LOGGER.error("Failed to close store '{}'", store.getName());
            }
        }
    }

    private void initTasks() {
        threadPool.scheduleAtFixedRate(new MaintenanceTask(), Duration.ofSeconds(5));
        threadPool.scheduleAtFixedRate(new CleanupTask(), Duration.ofHours(1));
    }

    @SuppressWarnings("rawtypes")
    private void discoverStoreFactory() {
        Collection<StoreFactory> storeFactories = ClassUtils.resolveProviderInstances(StoreFactory.class);
        if (storeFactories.isEmpty()) {
            LOGGER.error("Not store factory was detected in the classpath");
        } else {
            storeFactory = storeFactories.iterator().next();
            LOGGER.info("Register store factory {}", ClassUtils.getName(storeFactory));
        }
    }


    class MaintenanceTask extends AbstractRunnable {

        public MaintenanceTask() {
            setName(joinNames("Store", "Maintenance"));
        }

        @Override
        public void run() {
            for (Store<?, ?> store : stores.values()) {
                try {
                    if (store.size(Store.Location.MEMORY) > properties.getMaximumMemorySize()) {
                        store.flush();
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to flush store '{}'", store.getName());
                }
            }
        }
    }

    class CleanupTask extends AbstractRunnable {

        public CleanupTask() {
            setName(joinNames("Store", "Cleanup"));
        }

        @Override
        public void run() {
            for (Store<?, ?> store : stores.values()) {
                try {
                    store.purge();
                } catch (Exception e) {
                    LOGGER.error("Failed to purge store '{}'", store.getName());
                }
            }
        }
    }
}
