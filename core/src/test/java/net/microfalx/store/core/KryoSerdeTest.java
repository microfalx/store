package net.microfalx.store.core;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class KryoSerdeTest {

    @Test
    void serializeAndDeserializeObject() {
        TestItem item = new TestItem("1", "John", "Doe", 30);

        byte[] bytes = KryoSerde.serialize(item);
        TestItem deserialized = (TestItem) KryoSerde.deserialize(bytes);

        assertNotNull(bytes);
        assertNotSame(item, deserialized);
        assertEquals(item, deserialized);
    }

    @Test
    void serializeAndDeserializeNull() {
        byte[] bytes = KryoSerde.serialize(null);

        assertNull(bytes);
        assertNull(KryoSerde.deserialize(bytes));
    }

    @Test
    void serializeAndDeserializeBuiltInTypes() {
        assertEquals("text", KryoSerde.deserialize(KryoSerde.serialize("text")));
        assertEquals("", KryoSerde.deserialize(KryoSerde.serialize("")));
        assertEquals(42L, (long)KryoSerde.deserialize(KryoSerde.serialize(42L)));
        assertEquals(Long.MAX_VALUE, (long)KryoSerde.deserialize(KryoSerde.serialize(Long.MAX_VALUE)));
        assertEquals(3.14d, KryoSerde.deserialize(KryoSerde.serialize(3.14d)));
        assertEquals(Boolean.TRUE, KryoSerde.deserialize(KryoSerde.serialize(true)));
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) KryoSerde.deserialize(KryoSerde.serialize(new byte[]{1, 2, 3})));
    }

    @Test
    void serializeAndDeserializeCollections() {
        List<TestItem> list = new ArrayList<>();
        list.add(new TestItem("1", "John", "Doe", 30));
        list.add(new TestItem("2", "Jane", "Doe", 25));
        Map<String, Object> map = new HashMap<>();
        map.put("items", list);
        map.put("count", 2);
        map.put("empty", null);

        Object deserialized = KryoSerde.deserialize(KryoSerde.serialize(map));

        assertInstanceOf(HashMap.class, deserialized);
        assertEquals(map, deserialized);
    }

    @Test
    void serializeDoesNotTrackReferences() {
        TestItem item = new TestItem("1", "John", "Doe", 30);
        List<TestItem> list = new ArrayList<>(List.of(item, item));

        @SuppressWarnings("unchecked")
        List<TestItem> deserialized = (List<TestItem>) KryoSerde.deserialize(KryoSerde.serialize(list));

        assertEquals(2, deserialized.size());
        assertEquals(deserialized.get(0), deserialized.get(1));
        assertNotSame(deserialized.get(0), deserialized.get(1));
    }

    @Test
    void unregisteredClassIsWrittenByName() {
        byte[] bytes = KryoSerde.serialize(new UnregisteredItem("a"));

        assertTrue(containsClassName(bytes, UnregisteredItem.class));
        assertEquals(new UnregisteredItem("a"), KryoSerde.deserialize(bytes));
    }

    @Test
    void registerWithIdWritesIdInsteadOfName() {
        IdItem item = new IdItem("a");
        byte[] before = KryoSerde.serialize(item);

        KryoSerde.registerSerializer(IdItem.class, 1001);
        byte[] after = KryoSerde.serialize(item);

        assertTrue(containsClassName(before, IdItem.class));
        assertFalse(containsClassName(after, IdItem.class));
        assertTrue(after.length < before.length);
        assertEquals(item, KryoSerde.deserialize(after));
    }

    @Test
    void registerCustomSerializer() {
        PointSerializer serializer = new PointSerializer();
        KryoSerde.registerSerializer(Point.class, serializer);

        Point deserialized = (Point) KryoSerde.deserialize(KryoSerde.serialize(new Point(3, 4)));

        assertEquals(new Point(3, 4), deserialized);
        assertEquals(1, serializer.writes.get());
        assertEquals(1, serializer.reads.get());
    }

    @Test
    void registerCustomSerializerWithId() {
        Point3DSerializer serializer = new Point3DSerializer();
        KryoSerde.registerSerializer(Point3D.class, serializer, 1002);

        byte[] bytes = KryoSerde.serialize(new Point3D(1, 2, 3));

        assertFalse(containsClassName(bytes, Point3D.class));
        assertEquals(new Point3D(1, 2, 3), KryoSerde.deserialize(bytes));
        assertEquals(1, serializer.writes.get());
    }

    @Test
    void registerRejectsNullArguments() {
        assertThrows(IllegalArgumentException.class, () -> KryoSerde.registerSerializer(null, 1003));
        assertThrows(IllegalArgumentException.class, () -> KryoSerde.registerSerializer(null, new PointSerializer()));
        assertThrows(IllegalArgumentException.class, () -> KryoSerde.registerSerializer(Point.class, null, 1004));
    }

    @Test
    void deserializeRejectsNull() {
        assertNull(KryoSerde.deserialize(null));
    }

    @Test
    void deserializeInvalidBytes() {
        assertThrows(IllegalArgumentException.class, () -> KryoSerde.deserialize(new byte[0]));
        assertThrows(KryoException.class, () -> KryoSerde.deserialize(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}));
    }

    @Test
    void serializeFromMultipleThreads() throws Exception {
        int threads = 8;
        int iterations = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int thread = t;
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        TestItem item = new TestItem(thread + ":" + i, "John", "Doe", i);
                        assertEquals(item, KryoSerde.deserialize(KryoSerde.serialize(item)));
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean containsClassName(byte[] bytes, Class<?> clazz) {
        // Kryo flags the last character of an ASCII string with the high bit, so match all but the last one
        String name = clazz.getName();
        return new String(bytes, StandardCharsets.ISO_8859_1).contains(name.substring(0, name.length() - 1));
    }

    record UnregisteredItem(String value) {
    }

    record IdItem(String value) {
    }

    record Point(int x, int y) {
    }

    record Point3D(int x, int y, int z) {
    }

    static class PointSerializer extends Serializer<Point> {

        private final AtomicInteger writes = new AtomicInteger();
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public void write(Kryo kryo, Output output, Point point) {
            writes.incrementAndGet();
            output.writeInt(point.x());
            output.writeInt(point.y());
        }

        @Override
        public Point read(Kryo kryo, Input input, Class<? extends Point> type) {
            reads.incrementAndGet();
            return new Point(input.readInt(), input.readInt());
        }
    }

    static class Point3DSerializer extends Serializer<Point3D> {

        private final AtomicInteger writes = new AtomicInteger();

        @Override
        public void write(Kryo kryo, Output output, Point3D point) {
            writes.incrementAndGet();
            output.writeInt(point.x());
            output.writeInt(point.y());
            output.writeInt(point.z());
        }

        @Override
        public Point3D read(Kryo kryo, Input input, Class<? extends Point3D> type) {
            return new Point3D(input.readInt(), input.readInt(), input.readInt());
        }
    }
}
