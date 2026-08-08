package com.quantrity.anttester;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class UsbDeviceSnapshotTest {
    @Test public void devicesAreSortedByStableHardwareFields() {
        UsbDeviceSnapshot later = device(9, 0x1234, 0x0002, "Zed", "usb-9");
        UsbDeviceSnapshot earlier = device(3, 0x1234, 0x0001, "Alpha", "usb-3");
        List<UsbDeviceSnapshot> sorted = UsbInspector.sortDevices(Arrays.asList(later, earlier));
        assertEquals(earlier.deviceId, sorted.get(0).deviceId);
        assertEquals(later.deviceId, sorted.get(1).deviceId);
    }

    @Test public void formattingDoesNotExposeSerialNumbersOrJavaNull() {
        UsbDeviceSnapshot snapshot = device(1, 0x1234, 0x5678, null, null);
        String formatted = snapshot.format();
        assertFalse(formatted.contains("SerialNumber"));
        assertFalse(formatted.contains("null"));
        assertTrueContains(formatted, "Unknown");
    }

    private static UsbDeviceSnapshot device(int id, int vendor, int product, String name, String path) {
        return new UsbDeviceSnapshot(id, vendor, product, null, name, path, null, null);
    }

    private static void assertTrueContains(String value, String expected) {
        if (!value.contains(expected)) throw new AssertionError("Expected " + expected + " in " + value);
    }
}
