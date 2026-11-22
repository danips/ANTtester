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
        assertEquals("USB Devices\nAJK2.05RAF\nANT Radio Service:\nUnknown",
                format(Collections.<String>singletonList(null), Collections.singletonList("AJK2.05RAF")));
    }

    @Test public void retainsACompactValueWhenThereIsOnlyOneSource() {
        assertEquals("AJK2.05RAF",
                format(Collections.singletonList("AJK2.05RAF"), Collections.<String>emptyList()));
    }

    private static String format(List<String> service, List<String> usb) {
        return ExternalAdapterReadings.format(service, usb, "ANT Radio Service:", "USB Devices",
                "Unknown", "Detected via USB");
    }
}
