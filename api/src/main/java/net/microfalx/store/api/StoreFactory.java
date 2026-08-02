package net.microfalx.store.api;

import net.microfalx.lang.Identifiable;
import net.microfalx.resource.Resource;

/**
 * A factory for {@link Store} instances.
 *
 * @param <T>  the type of object
 * @param <ID> the type of the object identifier
 */
public interface StoreFactory<T extends Identifiable<ID>, ID> {

    /**
     * Creates a store implementation.
     *
     * @param options   the options
     * @param directory the directory
     * @return a non-null instance
     */
    Store<T, ID> create(Store.Options options, Resource directory);
}
