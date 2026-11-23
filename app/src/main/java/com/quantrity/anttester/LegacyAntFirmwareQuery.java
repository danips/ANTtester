package com.quantrity.anttester;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.dsi.ant.IAnt_6;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Reads the built-in chip version through the pre-adapter-provider ANT Radio Service API. */
final class LegacyAntFirmwareQuery {
    static final String ACTION = "com.dsi.ant.IAnt_6";
    private static final String TAG = "LegacyAntFirmwareQuery";
    private static final String ADMIN_PERMISSION = "com.dsi.ant.permission.ANT_ADMIN";
    private static final String RX_ACTION = "com.dsi.ant.intent.action.ANT_RX_MESSAGE_ACTION";
    private static final String MESSAGE_EXTRA = "com.dsi.ant.intent.ANT_MESSAGE";
    private static final int STARTUP_TIMEOUT_MS = 5000;
    private static final int VERSION_TIMEOUT_MS = 1500;
    private static final int RESTORE_TIMEOUT_MS = 2000;
    private static final int POLL_MS = 100;

    private LegacyAntFirmwareQuery() { }

    static boolean hasPermissions(Context context) {
        return Build.VERSION.SDK_INT < 23
                && context.checkCallingOrSelfPermission(ADMIN_PERMISSION) == PackageManager.PERMISSION_GRANTED
                && context.checkCallingOrSelfPermission(PermissionInspector.ANT_HARDWARE_PERMISSION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    static ComponentName findService(Context context) {
        if (!hasPermissions(context)) return null;
        Intent intent = new Intent(ACTION).setPackage(AntPackages.RADIO_SERVICE);
        ResolveInfo match = context.getPackageManager().resolveService(intent, 0);
        ServiceInfo service = match == null ? null : match.serviceInfo;
        if (service == null || !service.exported || !AntPackages.RADIO_SERVICE.equals(service.packageName)
                || service.permission != null && context.checkCallingOrSelfPermission(service.permission)
                        != PackageManager.PERMISSION_GRANTED) return null;
        return new ComponentName(service.packageName, service.name);
    }

    // This method returns before receiver registration on API 23+.
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    static String read(Context context, IAnt_6 service, boolean mayEnableRadio)
            throws RemoteException, InterruptedException {
        if (Build.VERSION.SDK_INT >= 23 || Thread.currentThread().isInterrupted()
                || !service.claimInterface()) return null;

        final CountDownLatch replied = new CountDownLatch(1);
        final AtomicReference<String> version = new AtomicReference<String>();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!RX_ACTION.equals(intent.getAction())) return;
                String received = AntVersionMessage.readVersionPacket(intent.getByteArrayExtra(MESSAGE_EXTRA));
                if (received != null && version.compareAndSet(null, received)) replied.countDown();
            }
        };
        boolean registered = false;
        boolean enabledByProbe = false;
        boolean ready = false;
        try {
            if (Thread.currentThread().isInterrupted()) return null;
            if (!service.isEnabled()) {
                if (!mayEnableRadio || !service.enable()) return null;
                enabledByProbe = true;
            }
            long deadline = SystemClock.elapsedRealtime() + STARTUP_TIMEOUT_MS;
            while (!service.isEnabled()) {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) return null;
                Thread.sleep(Math.min(POLL_MS, remaining));
            }
            ready = true;
            if (Thread.currentThread().isInterrupted()) return null;
            context.registerReceiver(receiver, new IntentFilter(RX_ACTION), ADMIN_PERMISSION, null);
            registered = true;
            // ARS 3.0.0 on the Xperia throws from ANTRequestMessage; the raw version query succeeds.
            if (!service.ANTTxMessage(AntVersionMessage.requestPacket())) return null;
            return replied.await(VERSION_TIMEOUT_MS, TimeUnit.MILLISECONDS) ? version.get() : null;
        } finally {
            try {
                if (registered) context.unregisterReceiver(receiver);
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to unregister the legacy ANT receiver", e);
            }
            try {
                if (enabledByProbe) restorePower(service, ready);
            } catch (RemoteException | RuntimeException e) {
                Log.w(TAG, "Unable to restore the legacy ANT radio state", e);
            }
            try {
                if (!service.releaseInterface()) Log.w(TAG, "Legacy ANT interface release was rejected");
            } catch (RemoteException | RuntimeException e) {
                Log.w(TAG, "Unable to release the legacy ANT interface", e);
            }
        }
    }

    private static void restorePower(IAnt_6 service, boolean ready) throws RemoteException {
        long deadline = SystemClock.elapsedRealtime() + RESTORE_TIMEOUT_MS;
        // An accepted enable can take effect after cancellation. Give it time to settle first.
        while (!ready && !service.isEnabled() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(POLL_MS);
        }
        boolean accepted = service.disable();
        while (SystemClock.elapsedRealtime() < deadline) {
            if (accepted && !service.isEnabled()) return;
            SystemClock.sleep(POLL_MS);
            if (!accepted) accepted = service.disable();
        }
        if (!accepted || service.isEnabled()) Log.w(TAG, "Timed out restoring legacy ANT radio power");
    }
}
