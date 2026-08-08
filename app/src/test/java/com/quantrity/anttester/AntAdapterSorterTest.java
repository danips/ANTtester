package com.quantrity.anttester;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class AntAdapterSorterTest {
    @Test public void builtInComesBeforeExternalAndExternalKeysBreakTies() {
        java.util.List<AntAdapterSnapshot> sorted = AntAdapterSorter.sort(Arrays.asList(
                new AntAdapterSnapshot("External", "2", false, "z"),
                new AntAdapterSnapshot("Built-in", "1", true, "b"),
                new AntAdapterSnapshot("External", "1", false, "a")));
        assertTrue(sorted.get(0).builtIn);
        assertEquals("a", sorted.get(1).adapterKey);
        assertEquals("z", sorted.get(2).adapterKey);
    }

    @Test public void missingMetadataIsSafe() {
        AntAdapterSnapshot snapshot = AntAdapterSnapshot.from(null);
        assertEquals("Unknown", snapshot.firmwareVersion);
        assertEquals("Unknown", snapshot.adapterKey);
    }
}
