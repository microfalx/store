package net.microfalx.store.mapdb;

import com.google.common.base.MoreObjects;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.lang.Identifiable;
import net.microfalx.lang.Initializable;
import net.microfalx.lang.annotation.Order;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.resource.Resource;
import net.microfalx.store.api.Store;
import net.microfalx.store.api.StoreFactory;

@Slf4j
@Provider
@Order(Order.NORMAL + 20)
public class StoreFactoryImpl<T extends Identifiable<ID>, ID> implements StoreFactory<T, ID>, Initializable {

    @Override
    public Store<T, ID> create(Store.Options options, Resource directory) {
        return new StoreImpl<>(options, directory);
    }

    @Override
    public void initialize(Object... context) {
    }

    @Override
    public String toString() {
        return "MapDB Store Factory";
    }
}
