package com.quantrity.anttester;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Direct firmware observations, independent of the ANT Radio Service adapter inventory. */
final class AntFirmwareFacts {
    enum BuiltInState { UNAVAILABLE, PERMISSION_REQUIRED, CHECKING, FAILED, VERIFIED }

    final BuiltInState builtInState;
    final String builtInVersion;
    final Map<String, String> usbVersions;
    final boolean usbChecking;

    AntFirmwareFacts(BuiltInState builtInState, String builtInVersion, Map<String, String> usbVersions,
                     boolean usbChecking) {
        this.builtInState = builtInState;
        this.builtInVersion = builtInVersion;
        this.usbVersions = Collections.unmodifiableMap(new TreeMap<String, String>(usbVersions));
        this.usbChecking = usbChecking;
    }

    static AntFirmwareFacts empty() {
        return new AntFirmwareFacts(BuiltInState.UNAVAILABLE, null,
                Collections.<String, String>emptyMap(), false);
    }

    AntFirmwareFacts withBuiltIn(BuiltInState state, String version) {
        return new AntFirmwareFacts(state, version, usbVersions, usbChecking);
    }

    AntFirmwareFacts withUsb(Map<String, String> versions, boolean checking) {
        return new AntFirmwareFacts(builtInState, builtInVersion, versions, checking);
    }

    boolean hasVerifiedUsb() {
        for (String version : usbVersions.values()) if (version != null) return true;
        return false;
    }
}
