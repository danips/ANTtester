package com.quantrity.anttester;

import android.annotation.TargetApi;
import android.hardware.usb.UsbDevice;
import android.os.Build;

/** Safe USB metadata used by the diagnostic screen. Serial numbers are intentionally excluded. */
final class UsbDeviceSnapshot {
    final int deviceId;
    final int vendorId;
    final int productId;
    final String manufacturerName;
    final String productName;
    final String deviceName;
    final String version;
    final String issue;

    UsbDeviceSnapshot(int deviceId, int vendorId, int productId, String manufacturerName,
                      String productName, String deviceName, String version, String issue) {
        this.deviceId = deviceId;
        this.vendorId = vendorId;
        this.productId = productId;
        this.manufacturerName = displayValue(manufacturerName);
        this.productName = displayValue(productName);
        this.deviceName = displayValue(deviceName);
        this.version = displayValue(version);
        this.issue = issue;
    }

    @TargetApi(12)
    static UsbDeviceSnapshot from(UsbDevice device) {
        int deviceId = -1;
        int vendorId = -1;
        int productId = -1;
        String manufacturer = null;
        String product = null;
        String name = null;
        String version = null;
        String issue = null;
        if (device == null) {
            return new UsbDeviceSnapshot(-1, -1, -1, null, null, null, null, "null device");
        }
        try { deviceId = device.getDeviceId(); } catch (RuntimeException e) { issue = "device id unavailable"; }
        try { vendorId = device.getVendorId(); } catch (RuntimeException e) { issue = appendIssue(issue, "vendor id unavailable"); }
        try { productId = device.getProductId(); } catch (RuntimeException e) { issue = appendIssue(issue, "product id unavailable"); }
        if (Build.VERSION.SDK_INT >= 21) {
            try { manufacturer = device.getManufacturerName(); } catch (RuntimeException e) { issue = appendIssue(issue, "manufacturer unavailable"); }
            try { product = device.getProductName(); } catch (RuntimeException e) { issue = appendIssue(issue, "product unavailable"); }
        }
        if (Build.VERSION.SDK_INT >= 12) {
            try { name = device.getDeviceName(); } catch (RuntimeException e) { issue = appendIssue(issue, "device name unavailable"); }
        }
        if (Build.VERSION.SDK_INT >= 23) {
            try { version = device.getVersion(); } catch (RuntimeException e) { issue = appendIssue(issue, "version unavailable"); }
        }
        return new UsbDeviceSnapshot(deviceId, vendorId, productId, manufacturer, product, name, version, issue);
    }

    String format() {
        StringBuilder result = new StringBuilder();
        result.append("Device ID: ").append(deviceId).append('\n')
                .append("Vendor ID: ").append(formatId(vendorId)).append('\n')
                .append("Product ID: ").append(formatId(productId)).append('\n')
                .append("Manufacturer: ").append(manufacturerName).append('\n')
                .append("Product: ").append(productName).append('\n')
                .append("Device name: ").append(deviceName);
        if (!"Unknown".equals(version)) {
            result.append('\n').append("Version: ").append(version);
        }
        if (issue != null) {
            result.append('\n').append("Note: ").append(issue);
        }
        return result.toString();
    }

    private static String formatId(int id) {
        return id < 0 ? "Unknown" : String.format(java.util.Locale.US, "%d (0x%04X)", id, id);
    }

    private static String displayValue(String value) {
        return value == null || value.trim().length() == 0 ? "Unknown" : value.trim();
    }

    private static String appendIssue(String current, String next) {
        return current == null ? next : current + ", " + next;
    }
}
