package com.quantrity.anttester;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ExternalAdapterReadingsTest {
    @Test public void keepsUsbDetectionForASecondStickAfterReadingTheFirst() {
        assertEquals("AJK2.05RAF\nDetected via USB",
                format(Collections.<String>emptyList(), Arrays.asList("AJK2.05RAF", null)));
    }

    @Test public void keepsUsbFirmwareVisibleWhenTheServiceHasNoVersion() {
        assertEquals("AJK2.05RAF",
                format(Collections.<String>singletonList(null), Collections.singletonList("AJK2.05RAF")));
    }

    @Test public void displaysTheSameVersionWithOrWithoutTheServices() {
        List<String> version = Collections.singletonList("AJK2.05RAF");
        assertEquals("AJK2.05RAF",
                format(version, Collections.<String>singletonList(null)));
        assertEquals("AJK2.05RAF", format(Collections.<String>emptyList(), version));
    }

    @Test public void showsASingleValueWhenBothSourcesReportTheSameVersion() {
        List<String> version = Collections.singletonList("AJK2.05RAF");
        assertEquals("AJK2.05RAF", format(version, version));
    }

    @Test public void showsUsbDetectionWhenNeitherSourceHasAVersion() {
        List<String> unknownVersion = Collections.singletonList(null);
        assertEquals("Detected via USB", format(unknownVersion, unknownVersion));
    }

    @Test public void preservesConflictingVersionsWithTheirSources() {
        assertEquals("USB Devices\nAJK2.05RAF\nANT Radio Service:\nAUM1.03B00",
                format(Collections.singletonList("AUM1.03B00"), Collections.singletonList("AJK2.05RAF")));
    }

    @Test public void keepsSourceLabelsWhenThereAreMultipleAdapters() {
        assertEquals("USB Devices\nDetected via USB\nDetected via USB\nANT Radio Service:\nAJK2.05RAF",
                format(Collections.singletonList("AJK2.05RAF"), Arrays.asList(null, null)));
    }

    private static String format(List<String> service, List<String> usb) {
        return ExternalAdapterReadings.format(service, usb, "ANT Radio Service:", "USB Devices",
                "Unknown", "Detected via USB");
    }
}
