package com.quantrity.anttester;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class UsbInspector {
    static final class Scan {
        final List<UsbDeviceSnapshot> devices;
        final Map<String, UsbDevice> antAdapters;
        final UsbManager manager;

        Scan(List<UsbDeviceSnapshot> devices, Map<String, UsbDevice> antAdapters, UsbManager manager) {
            this.devices = devices;
            this.antAdapters = antAdapters;
            this.manager = manager;
        }
    }

    private final Context context;

    UsbInspector(Context context) {
        this.context = context.getApplicationContext();
    }

    boolean hasHostSupport() {
        if (Build.VERSION.SDK_INT >= 12) {
            try {
                return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        File featureFile = new File("/system/etc/permissions/android.hardware.usb.host.xml");
        if (!featureFile.isFile()) return false;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(featureFile));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("<feature name=\"android.hardware.usb.host\"")) return true;
            }
        } catch (IOException ignored) {
            return false;
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) { }
        }
        return false;
    }

    Scan inspectDevices() {
        ArrayList<UsbDeviceSnapshot> result = new ArrayList<UsbDeviceSnapshot>();
        Map<String, UsbDevice> antAdapters = new TreeMap<String, UsbDevice>();
        UsbManager manager = null;
        if (Build.VERSION.SDK_INT < 12) return new Scan(result, antAdapters, null);
        try {
            manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (manager == null) return new Scan(result, antAdapters, null);
            HashMap<String, UsbDevice> devices = manager.getDeviceList();
            if (devices == null) return new Scan(result, antAdapters, manager);
            for (Map.Entry<String, UsbDevice> entry : devices.entrySet()) {
                try {
                    UsbDeviceSnapshot snapshot = UsbDeviceSnapshot.from(entry.getValue());
                    result.add(snapshot);
                    if (AntUsbFirmwareReader.isSupported(snapshot.vendorId, snapshot.productId)) {
                        antAdapters.put(entry.getKey(), entry.getValue());
                    }
                } catch (RuntimeException e) {
                    result.add(new UsbDeviceSnapshot(-1, -1, -1, null, null, null, null,
                            "device inspection failed"));
                }
            }
        } catch (SecurityException e) {
            result.add(new UsbDeviceSnapshot(-1, -1, -1, null, null, null, null,
                    "USB access denied"));
        } catch (RuntimeException e) {
            result.add(new UsbDeviceSnapshot(-1, -1, -1, null, null, null, null,
                    "USB enumeration failed"));
        }
        return new Scan(sortDevices(result), antAdapters, manager);
    }

    static List<UsbDeviceSnapshot> sortDevices(List<UsbDeviceSnapshot> input) {
        ArrayList<UsbDeviceSnapshot> result = new ArrayList<UsbDeviceSnapshot>(input == null
                ? Collections.<UsbDeviceSnapshot>emptyList() : input);
        Collections.sort(result, new Comparator<UsbDeviceSnapshot>() {
            @Override public int compare(UsbDeviceSnapshot left, UsbDeviceSnapshot right) {
                int value = compareInt(left.vendorId, right.vendorId);
                if (value != 0) return value;
                value = compareInt(left.productId, right.productId);
                if (value != 0) return value;
                value = left.productName.compareToIgnoreCase(right.productName);
                if (value != 0) return value;
                value = compareInt(left.deviceId, right.deviceId);
                if (value != 0) return value;
                return left.deviceName.compareToIgnoreCase(right.deviceName);
            }
        });
        return result;
    }

    private static int compareInt(int left, int right) {
        return left < right ? -1 : left == right ? 0 : 1;
    }
}
