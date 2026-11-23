package com.quantrity.anttester;

import java.util.Collection;

final class ExternalAdapterReadings {
    private ExternalAdapterReadings() { }

    static String format(Collection<String> serviceVersions, Collection<String> usbVersions,
                         String serviceHeading, String usbHeading, String unknown, String detectedViaUsb) {
        // With one reading per source, show a single value if the readings agree or one lacks a version.
        if (serviceVersions.size() == 1 && usbVersions.size() == 1) {
            String serviceVersion = serviceVersions.iterator().next();
            String usbVersion = usbVersions.iterator().next();
            if (usbVersion == null) return serviceVersion == null ? detectedViaUsb : serviceVersion;
            if (serviceVersion == null || usbVersion.equals(serviceVersion)) return usbVersion;
        }

        // ANTLib UUIDs and Android USB device names do not provide a common adapter ID.
        boolean showSource = !serviceVersions.isEmpty() && !usbVersions.isEmpty();
        StringBuilder text = new StringBuilder();
        appendGroup(text, usbVersions, showSource ? usbHeading : null, detectedViaUsb);
        appendGroup(text, serviceVersions, showSource ? serviceHeading : null, unknown);
        return text.toString();
    }

    private static void appendGroup(StringBuilder text, Collection<String> versions,
                                    String heading, String fallback) {
        if (versions.isEmpty()) return;
        if (heading != null) appendLine(text, heading);
        for (String version : versions) {
            appendLine(text, version == null ? fallback : version);
        }
    }

    private static void appendLine(StringBuilder text, String line) {
        if (text.length() > 0) text.append('\n');
        text.append(line);
    }
}
