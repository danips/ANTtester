package com.quantrity.anttester;

final class PackageState {
    enum Kind { INSTALLED_ENABLED, INSTALLED_DISABLED, MISSING, NOT_VISIBLE, VERSION_TOO_OLD, QUERY_ERROR }
    final String packageName;
    final Kind kind;
    final String versionName;
    final int versionCode;
    final String detail;

    PackageState(String packageName, Kind kind, String versionName, int versionCode, String detail) {
        this.packageName = packageName;
        this.kind = kind;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.detail = detail;
    }

    boolean isInstalledEnabled() { return kind == Kind.INSTALLED_ENABLED; }

    boolean isInstalled() {
        return isInstalledEnabled() || kind == Kind.INSTALLED_DISABLED || kind == Kind.VERSION_TOO_OLD;
    }
}
