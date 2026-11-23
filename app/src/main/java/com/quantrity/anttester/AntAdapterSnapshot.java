package com.quantrity.anttester;

import com.dsi.ant.channel.AdapterInfo;

/** Vendor-neutral representation of one ANT adapter. */
final class AntAdapterSnapshot {
    enum Kind { BUILT_IN, EXTERNAL, UNKNOWN }

    interface BuiltInCheck {
        boolean isBuiltIn();
    }

    private static final String UNKNOWN = "Unknown";
    final Kind kind;
    final String firmwareVersion;
    final String adapterKey;

    AntAdapterSnapshot(Kind kind, String firmwareVersion, String adapterKey) {
        this.kind = kind;
        this.firmwareVersion = valueOrUnknown(firmwareVersion);
        this.adapterKey = valueOrUnknown(adapterKey);
    }

    static AntAdapterSnapshot from(AdapterInfo adapter) {
        if (adapter == null) return new AntAdapterSnapshot(Kind.UNKNOWN, null, null);
        String key = null;
        String firmware = null;
        try { if (adapter.getAdapterId() != null) key = adapter.getAdapterId().toString(); }
        catch (RuntimeException ignored) { }
        try { firmware = adapter.getVersionString(); } catch (RuntimeException ignored) { }
        return new AntAdapterSnapshot(readKind(adapter::isBuiltIn), firmware, key);
    }

    static Kind readKind(BuiltInCheck check) {
        try {
            return check.isBuiltIn() ? Kind.BUILT_IN : Kind.EXTERNAL;
        } catch (RuntimeException e) {
            return Kind.UNKNOWN;
        }
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.trim().length() == 0 ? UNKNOWN : value.trim();
    }

    String knownFirmwareVersion() {
        return UNKNOWN.equalsIgnoreCase(firmwareVersion) ? null : firmwareVersion;
    }
}
