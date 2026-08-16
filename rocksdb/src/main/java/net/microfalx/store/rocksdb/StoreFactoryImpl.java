package net.microfalx.store.rocksdb;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.lang.Hashing;
import net.microfalx.lang.Identifiable;
import net.microfalx.lang.Initializable;
import net.microfalx.lang.StringUtils;
import net.microfalx.lang.annotation.Order;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.resource.Resource;
import net.microfalx.resource.rocksdb.RocksDbManager;
import net.microfalx.store.api.Store;
import net.microfalx.store.api.StoreFactory;
import net.microfalx.threadpool.AbstractRunnable;
import net.microfalx.threadpool.ThreadPool;
import org.apache.commons.lang3.ArrayUtils;
import org.rocksdb.RocksDB;

import java.io.File;
import java.time.Duration;
import java.util.Collection;

import static net.microfalx.lang.StringUtils.capitalizeWords;
import static net.microfalx.lang.StringUtils.joinNames;

@Slf4j
@Provider
@Order(Order.NORMAL + 10)
public class StoreFactoryImpl<T extends Identifiable<ID>, ID> implements StoreFactory<T, ID>, Initializable {

    @Override
    public Store<T, ID> create(Store.Options options, Resource directory) {
        return new StoreImpl<>(options, directory);
    }

    @Override
    public void initialize(Object... context) {
        ThreadPool threadPool = ThreadPool.get();
        threadPool.execute(new DiscoverTask());
        threadPool.scheduleAtFixedRate(new DiscoverTask(), Duration.ofMinutes(5));
    }

    @Override
    public String toString() {
        return "RocksDB Store Factory";
    }

    class DiscoverTask extends AbstractRunnable {

        public DiscoverTask() {
            setName(joinNames("Store", "Discover"));
        }

        private void register(File file, RocksDB rocksDB) {
            String id = "resource_" + Hashing.hash(file.getAbsolutePath());
            LOGGER.info("Register resource store from " + file);
            String[] names = StringUtils.split(capitalizeWords(file.getName()), " ");
            names = ArrayUtils.insert(0, names, "Resource");
        }

        @Override
        public void run() {
            Collection<RocksDB> dbs = RocksDbManager.getInstance().list();
            for (RocksDB db : dbs) {
                File file = new File(db.getName());
                register(file, db);
            }
        }
    }
}
