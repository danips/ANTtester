package com.quantrity.anttester;

/** Immutable USB device record; platform-specific collection is added by P1-06. */
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
        this.manufacturerName = manufacturerName;
        this.productName = productName;
        this.deviceName = deviceName;
        this.version = version;
        this.issue = issue;
    }
}
