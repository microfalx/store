package net.microfalx.store.mapdb;

import net.microfalx.lang.JvmUtils;
import net.microfalx.resource.Resource;
import net.microfalx.store.api.Query;
import net.microfalx.store.api.Store;
import net.microfalx.store.api.StoreService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class StoreImplTest {

    private StoreFactoryImpl<TestItem, String> storeFactory;
    private Store<TestItem, String> store;

    @BeforeEach
    void setup() {
        storeFactory = new StoreFactoryImpl<>();
        storeFactory.initialize();
        File directory = new File(JvmUtils.getTemporaryDirectory(), "mapdb_" + Long.toHexString(System.currentTimeMillis()));
        store = storeFactory.create(Store.Options.create("test"), Resource.directory(directory));
    }

    @Test
    void service() {
        Store<TestItem, String> store = StoreService.getInstance().register(Store.Options.create("mapdb"));
        assertNull(store.find("1"));
        store.add(new TestItem("1", "John", "Doe", 30));
        assertNotNull(store.find("1"));
    }

    @Test
    void initialization() {
        assertNotNull(storeFactory);
        assertNotNull(store.getResource());
        assertEquals(0, store.count(Store.Location.MEMORY));
        assertEquals(0, store.count(Store.Location.DISK));
        Assertions.assertThat(store.size(Store.Location.MEMORY)).isGreaterThanOrEqualTo(0);
        Assertions.assertThat(store.size(Store.Location.DISK)).isGreaterThanOrEqualTo(1000);
    }

    @Test
    void get() {
        assertNull(store.find("1"));
        store.add(new TestItem("1", "John", "Doe", 30));
        assertNotNull(store.find("1"));
    }

    @Test
    void remove() {
        assertNull(store.find("1"));
        store.add(new TestItem("1", "John", "Doe", 30));
        assertNotNull(store.find("1"));
        store.remove("1");
        assertNull(store.find("1"));
    }

    @Test
    void list() {
        assertEquals(0, store.list(Query.<TestItem>builder().build()).size());
        store.add(new TestItem("1", "John", "Doe", 30));
        assertEquals(1, store.list(Query.<TestItem>builder().build()).size());
    }

}