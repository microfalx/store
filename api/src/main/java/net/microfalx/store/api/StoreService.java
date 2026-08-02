package net.microfalx.store.api;

import net.microfalx.lang.Identifiable;
import net.microfalx.lang.service.Service;
import net.microfalx.resource.Resource;

import java.util.Collection;

/**
 * A manager of {@link Store stores}.
 */
public interface StoreService extends Service {

    /**
     * Returns the store factory.
     *
     * @return a non-null instance
     */
    StoreFactory<?, ?> getStoreFactory();

    /**
     * Returns the directory where the stores will persist their data.
     *
     * @return a non-null instance
     */
    Resource getDirectory();

    /**
     * Changes the directory where the store will persist their data.
     *
     * @param directory the directory resource
     */
    void setResource(Resource directory);

    /**
     * Returns registered stores.
     *
     * @return a non-null instance
     */
    Collection<Store<?, ?>> getStores();

    /**
     * Returns a store with a given identifier.
     *
     * @param id   the store identifier
     * @param <ID> the identifier type
     * @param <T>  the item type
     * @return a non-null instance
     */
    <T extends Identifiable<ID>, ID> Store<T, ID> getStore(String id);

    /**
     * Registers a new store.
     *
     * @param options the options
     */
    <T extends Identifiable<ID>, ID> Store<T, ID> register(Store.Options options);

    /**
     * Registers an external store.
     *
     * @param store the store to register
     */
    <T extends Identifiable<ID>, ID> Store<T, ID> register(Store<T, ID> store);

    /**
     * Flushes all stores to disk.
     */
    void flush();
}
