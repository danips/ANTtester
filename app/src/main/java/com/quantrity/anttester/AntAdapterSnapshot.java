package com.quantrity.anttester;

import com.dsi.ant.channel.AdapterInfo;

/** Vendor-neutral representation of one ANT adapter. */
final class AntAdapterSnapshot {
    final String displayName;
    final String firmwareVersion;
    final boolean builtIn;
    final String adapterKey;

    AntAdapterSnapshot(String displayName, String firmwareVersion, boolean builtIn, String adapterKey) {
        this.displayName = valueOrUnknown(displayName);
        this.firmwareVersion = valueOrUnknown(firmwareVersion);
        this.builtIn = builtIn;
        this.adapterKey = valueOrUnknown(adapterKey);
    }

    static AntAdapterSnapshot from(AdapterInfo adapter) {
        if (adapter == null) return new AntAdapterSnapshot("Unknown ANT adapter", null, false, null);
        String key = null;
        String firmware = null;
        boolean builtIn = false;
        try { if (adapter.getAdapterId() != null) key = adapter.getAdapterId().toString(); }
        catch (RuntimeException ignored) { }
        try { builtIn = adapter.isBuiltIn(); } catch (RuntimeException ignored) { }
        try { firmware = adapter.getVersionString(); } catch (RuntimeException ignored) { }
        return new AntAdapterSnapshot(builtIn ? "Built-in ANT adapter" : "External ANT adapter",
                firmware, builtIn, key);
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.trim().length() == 0 ? "Unknown" : value.trim();
    }
}
