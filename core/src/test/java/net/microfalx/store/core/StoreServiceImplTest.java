package net.microfalx.store.core;

import net.microfalx.store.api.Query;
import net.microfalx.store.api.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StoreServiceImplTest {

    private StoreServiceImpl storeService;
    private Store<TestItem, String> store;

    @BeforeEach
    void setup() {
        storeService = new StoreServiceImpl();
        storeService.initialize();
        storeService.start();
        store = storeService.register(Store.Options.create("test"));
    }

    @Test
    void initialization() {
        assertNotNull(storeService.getDirectory());
        assertNotNull(storeService.getStoreFactory());
        assertEquals(1, storeService.getStores().size());
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
        assertEquals(0,store.list(Query.<TestItem>builder().build()).size());
        store.add(new TestItem("1", "John", "Doe", 30));
        assertEquals(1,store.list(Query.<TestItem>builder().build()).size());
    }

}