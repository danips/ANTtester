package com.quantrity.anttester;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

final class PackageInspector {
    private final Context context;

    PackageInspector(Context context) {
        this.context = context.getApplicationContext();
    }

    PackageState inspect(String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            ApplicationInfo app = info.applicationInfo;
            PackageState.Kind kind = app != null && !app.enabled
                    ? PackageState.Kind.INSTALLED_DISABLED : PackageState.Kind.INSTALLED_ENABLED;
            if (kind == PackageState.Kind.INSTALLED_ENABLED
                    && AntPackages.RADIO_SERVICE.equals(packageName)
                    && info.versionCode > 0
                    && info.versionCode < AntPackages.MIN_ADAPTER_PROVIDER_SERVICE_VERSION) {
                kind = PackageState.Kind.VERSION_TOO_OLD;
            }
            return new PackageState(packageName, kind, safeVersion(info.versionName), info.versionCode, null);
        } catch (PackageManager.NameNotFoundException e) {
            return new PackageState(packageName, PackageState.Kind.MISSING, null, 0, "package not installed");
        } catch (SecurityException e) {
            return new PackageState(packageName, PackageState.Kind.NOT_VISIBLE, null, 0, "package access denied");
        } catch (RuntimeException e) {
            return new PackageState(packageName, PackageState.Kind.QUERY_ERROR, null, 0,
                    e.getClass().getSimpleName());
        }
    }

    PermissionState inspectAntPermission() {
        try {
            if (Build.VERSION.SDK_INT < 16) {
                return new PermissionState(PermissionState.Kind.NOT_DECLARED,
                        PermissionInspector.ANT_HARDWARE_PERMISSION);
            }
            PackageInfo info = context.getPackageManager().getPackageInfo(AntPackages.RADIO_SERVICE,
                    PackageManager.GET_PERMISSIONS);
            return PermissionInspector.inspect(info);
        } catch (PackageManager.NameNotFoundException e) {
            return new PermissionState(PermissionState.Kind.NOT_DECLARED,
                    PermissionInspector.ANT_HARDWARE_PERMISSION);
        } catch (SecurityException e) {
            return new PermissionState(PermissionState.Kind.QUERY_ERROR,
                    PermissionInspector.ANT_HARDWARE_PERMISSION);
        } catch (RuntimeException e) {
            return new PermissionState(PermissionState.Kind.QUERY_ERROR,
                    PermissionInspector.ANT_HARDWARE_PERMISSION);
        }
    }

    private static String safeVersion(String version) {
        return version == null || version.trim().length() == 0 ? "Unknown" : version;
    }
}
