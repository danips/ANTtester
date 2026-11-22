package com.quantrity.anttester;

import android.annotation.TargetApi;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.SystemClock;
import android.util.Log;

@TargetApi(12)
final class AntUsbFirmwareReader {
    private static final int DYNASTREAM_USB_VENDOR_ID = 0x0fcf;
    private static final int ANT_USB2_PRODUCT_ID = 0x1008;
    private static final int ANT_USBM_PRODUCT_ID = 0x1009;
    private static final int TRANSFER_TIMEOUT_MS = 750;
    private static final int MAX_RESPONSE_BYTES = 4096;

    private AntUsbFirmwareReader() { }

    static boolean isSupported(int vendorId, int productId) {
        return vendorId == DYNASTREAM_USB_VENDOR_ID
                && (productId == ANT_USB2_PRODUCT_ID || productId == ANT_USBM_PRODUCT_ID);
    }

    static String readVersion(UsbManager manager, UsbDevice device) {
        try {
            if (!manager.hasPermission(device) || Thread.currentThread().isInterrupted()) return null;
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface usbInterface = device.getInterface(i);
                UsbEndpoint input = findBulkEndpoint(usbInterface, UsbConstants.USB_DIR_IN);
                UsbEndpoint output = findBulkEndpoint(usbInterface, UsbConstants.USB_DIR_OUT);
                if (input != null && output != null) {
                    return queryVersion(manager, device, usbInterface, input, output);
                }
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Log.d("AntUsbFirmwareReader", "Unable to access the ANT USB adapter", e);
        }
        return null;
    }

    private static UsbEndpoint findBulkEndpoint(UsbInterface usbInterface, int direction) {
        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = usbInterface.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && endpoint.getDirection() == direction) {
                return endpoint;
            }
        }
        return null;
    }

    private static String queryVersion(UsbManager manager, UsbDevice device, UsbInterface usbInterface,
                                       UsbEndpoint input, UsbEndpoint output) {
        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) return null;
        try {
            // Leave an existing driver or another app's claim in place.
            if (!connection.claimInterface(usbInterface, false)) return null;
            try {
                if (Thread.currentThread().isInterrupted()) return null;
                byte[] request = AntVersionMessage.request();
                int sent = connection.bulkTransfer(output, request, request.length, TRANSFER_TIMEOUT_MS);
                return sent == request.length ? receiveVersion(connection, input) : null;
            } finally {
                connection.releaseInterface(usbInterface);
            }
        } finally {
            connection.close();
        }
    }

    private static String receiveVersion(UsbDeviceConnection connection, UsbEndpoint input) {
        byte[] response = new byte[MAX_RESPONSE_BYTES];
        byte[] chunk = new byte[512];
        long deadline = SystemClock.elapsedRealtime() + TRANSFER_TIMEOUT_MS;
        int length = 0;
        while (length < response.length && !Thread.currentThread().isInterrupted()) {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0) break;
            int count = connection.bulkTransfer(input, chunk,
                    Math.min(chunk.length, response.length - length), (int) remaining);
            if (count < 0) break;
            if (count == 0) continue;
            System.arraycopy(chunk, 0, response, length, count);
            length += count;
            String version = AntVersionMessage.findVersion(response, length);
            if (version != null) return version;
        }
        return null;
    }
}
