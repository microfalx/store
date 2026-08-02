package net.microfalx.store.core;

import net.microfalx.lang.Identifiable;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.resource.Resource;
import net.microfalx.store.api.Store;
import net.microfalx.store.api.StoreFactory;

@Provider
public class TestStoreFactory<T extends Identifiable<ID>, ID> implements StoreFactory<T, ID> {

    @Override
    public Store<T, ID> create(Store.Options options, Resource directory) {
        return new TestStore<>(options, directory);
    }
}
