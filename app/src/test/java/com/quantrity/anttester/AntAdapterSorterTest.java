package com.quantrity.anttester;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import static com.quantrity.anttester.AntAdapterSnapshot.Kind.BUILT_IN;
import static com.quantrity.anttester.AntAdapterSnapshot.Kind.EXTERNAL;
import static com.quantrity.anttester.AntAdapterSnapshot.Kind.UNKNOWN;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AntAdapterSorterTest {
    @Test public void groupsAdaptersByTypeAndSortsWithinEachGroup() {
        AntAdapterSnapshot unknown = new AntAdapterSnapshot(UNKNOWN, "4", "a");
        AntAdapterSnapshot externalLater = new AntAdapterSnapshot(EXTERNAL, "2", "z");
        AntAdapterSnapshot externalEarlier = new AntAdapterSnapshot(EXTERNAL, "1", "a");
        AntAdapterSnapshot builtInLater = new AntAdapterSnapshot(BUILT_IN, "3", "b");
        AntAdapterSnapshot builtInEarlier = new AntAdapterSnapshot(BUILT_IN, "5", "a");

        assertEquals(Arrays.asList(builtInEarlier, builtInLater, externalEarlier, externalLater, unknown),
                AntAdapterSorter.sort(Arrays.asList(unknown, externalLater, builtInLater, externalEarlier, builtInEarlier)));
    }

    @Test public void firmwareBreaksTiesForIdenticalAdapterKeys() {
        AntAdapterSnapshot first = new AntAdapterSnapshot(BUILT_IN, "AUM1.03B00", "adapter");
        AntAdapterSnapshot second = new AntAdapterSnapshot(BUILT_IN, "AUM1.04B22", "ADAPTER");
        AntAdapterSnapshot missing = new AntAdapterSnapshot(BUILT_IN, null, "adapter");
        List<AntAdapterSnapshot> expected = Arrays.asList(first, second, missing);

        assertEquals(expected, AntAdapterSorter.sort(Arrays.asList(second, missing, first)));
        assertEquals(expected, AntAdapterSorter.sort(Arrays.asList(missing, first, second)));
    }

    @Test public void aFailedClassificationLeavesTheAdapterTypeUnknown() {
        assertEquals(BUILT_IN, AntAdapterSnapshot.readKind(() -> true));
        assertEquals(EXTERNAL, AntAdapterSnapshot.readKind(() -> false));
        assertEquals(UNKNOWN, AntAdapterSnapshot.readKind(() -> {
            throw new IllegalStateException("Unable to classify adapter");
        }));
    }

    @Test public void missingMetadataIsSafe() {
        AntAdapterSnapshot snapshot = AntAdapterSnapshot.from(null);
        assertEquals(UNKNOWN, snapshot.kind);
        assertNull(snapshot.knownFirmwareVersion());
        assertEquals("Unknown", snapshot.adapterKey);
    }
}
