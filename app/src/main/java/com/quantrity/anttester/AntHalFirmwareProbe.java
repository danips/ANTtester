package com.quantrity.anttester;

import static com.quantrity.anttester.AntHalPowerRestore.STATE_DISABLED;
import static com.quantrity.anttester.AntHalPowerRestore.STATE_ENABLED;
import static com.quantrity.anttester.AntHalPowerRestore.STATE_ENABLING;
import static com.quantrity.anttester.AntHalPowerRestore.SUCCESS;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import com.dsi.ant.server.IAntHal;
import com.dsi.ant.server.IAntHalCallback;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class AntHalFirmwareProbe {
    static final String PERMISSION = "com.dsi.ant.permission.ANTRADIO";
    private static final String TAG = "AntHalFirmwareProbe";
    private static final String SERVICE_ACTION = "com.dsi.ant.server.IAntHal";
    private static final String HAL_PACKAGE = "com.dsi.ant.server";
    private static final int CONNECT_TIMEOUT_MS = 2500;
    private static final int ENABLE_TIMEOUT_MS = 3000;
    private static final int VERSION_TIMEOUT_MS = 1500;
    private static final int RESTORE_TIMEOUT_MS = 3000;
    private static final int RESTORE_POLL_MS = 50;
    // The platform HAL shares one callback and radio across activity instances.
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    interface Listener {
        void onResult(String version);
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Future<?> task;
    private int generation;

    AntHalFirmwareProbe(Context context) {
        this.context = context.getApplicationContext();
    }

    static ComponentName findService(Context context) {
        Intent intent = new Intent(SERVICE_ACTION).setPackage(HAL_PACKAGE);
        ResolveInfo match = context.getPackageManager().resolveService(intent, 0);
        ServiceInfo service = match == null ? null : match.serviceInfo;
        if (service == null || !service.exported || !PERMISSION.equals(service.permission)
                || (service.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
            return null;
        }
        return new ComponentName(service.packageName, service.name);
    }

    static boolean hasPermission(Context context) {
        return context.checkCallingOrSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    void start(final ComponentName component, final Listener listener) {
        if (task != null) return;
        final int requestedGeneration = generation;
        task = WORKER.submit(new Runnable() {
            @Override public void run() {
                final String version = readBoundService(component);
                main.post(new Runnable() {
                    @Override public void run() {
                        if (requestedGeneration != generation) return;
                        task = null;
                        listener.onResult(version);
                    }
                });
            }
        });
    }

    void cancel() {
        generation++;
        if (task != null) {
            task.cancel(true);
            task = null;
        }
    }

    private String readBoundService(ComponentName component) {
        final CountDownLatch connected = new CountDownLatch(1);
        final AtomicReference<IBinder> binder = new AtomicReference<IBinder>();
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                binder.set(service);
                connected.countDown();
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                binder.set(null);
                connected.countDown();
            }

            @Override public void onNullBinding(ComponentName name) {
                connected.countDown();
            }

            @Override public void onBindingDied(ComponentName name) {
                onServiceDisconnected(name);
            }
        };

        boolean bindRequested = false;
        try {
            if (Thread.currentThread().isInterrupted() || !hasPermission(context)) return null;
            bindRequested = true;
            Intent intent = new Intent(SERVICE_ACTION).setComponent(component);
            if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    || !connected.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.d(TAG, "ANT HAL service did not connect");
                return null;
            }
            IBinder service = binder.get();
            if (service == null || !SERVICE_ACTION.equals(service.getInterfaceDescriptor())) return null;
            return query(IAntHal.Stub.asInterface(service));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RemoteException | RuntimeException e) {
            Log.d(TAG, "Unable to read built-in ANT firmware", e);
        } finally {
            if (bindRequested) {
                try {
                    context.unbindService(connection);
                } catch (IllegalArgumentException e) {
                    // The system rejected the bind before registering the connection.
                }
            }
        }
        return null;
    }

    private String query(IAntHal hal) throws RemoteException, InterruptedException {
        final CountDownLatch enabled = new CountDownLatch(1);
        final CountDownLatch replied = new CountDownLatch(1);
        final AtomicReference<String> version = new AtomicReference<String>();
        IAntHalCallback callback = new IAntHalCallback.Stub() {
            @Override public void antHalStateChanged(int state) {
                if (state == STATE_ENABLED) enabled.countDown();
            }

            @Override public void antHalRxMessage(byte[] packet) {
                String received = AntVersionMessage.readHalVersion(packet);
                if (received != null && version.compareAndSet(null, received)) replied.countDown();
            }
        };

        boolean registered = false;
        boolean enabledByProbe = false;
        boolean radioBecameReady = false;
        try {
            int state = hal.getAntState();
            if (Thread.currentThread().isInterrupted()
                    || (state == STATE_DISABLED && airplaneModeOn())) return null;
            registered = hal.registerAntHalCallback(callback) == SUCCESS;
            if (!registered || Thread.currentThread().isInterrupted()) return null;
            if (state == STATE_DISABLED) {
                enabledByProbe = hal.setAntState(STATE_ENABLED) == SUCCESS;
                if (!enabledByProbe) {
                    Log.d(TAG, "ANT HAL rejected the enable request");
                    return null;
                }
            } else if (state != STATE_ENABLED && state != STATE_ENABLING) {
                return null;
            }
            if (hal.getAntState() != STATE_ENABLED
                    && !enabled.await(ENABLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.d(TAG, "Built-in ANT radio did not become ready");
                return null;
            }
            radioBecameReady = true;
            if (Thread.currentThread().isInterrupted()) return null;
            if (hal.ANTTxMessage(AntVersionMessage.halRequest()) != SUCCESS
                    || !replied.await(VERSION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.d(TAG, "No ANT version reply from the built-in radio");
                return null;
            }
            return version.get();
        } finally {
            if (enabledByProbe) {
                try {
                    restoreRadioPower(hal, radioBecameReady || enabled.getCount() == 0);
                } catch (RemoteException | RuntimeException e) {
                    Log.d(TAG, "Unable to restore the ANT radio state", e);
                }
            }
            if (registered) {
                try {
                    hal.unregisterAntHalCallback(callback);
                } catch (RemoteException | RuntimeException e) {
                    Log.d(TAG, "Unable to unregister the ANT HAL callback", e);
                }
            }
        }
    }

    private void restoreRadioPower(IAntHal hal, boolean sawEnable) throws RemoteException {
        final long deadline = SystemClock.elapsedRealtime() + RESTORE_TIMEOUT_MS;
        boolean restored = AntHalPowerRestore.restore(hal, sawEnable, new AntHalPowerRestore.WaitForTransition() {
            @Override public boolean awaitNextPoll() {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) return false;
                // Lifecycle cancellation stops the read, but must still let restoration finish.
                SystemClock.sleep(Math.min(remaining, RESTORE_POLL_MS));
                return true;
            }
        });
        if (!restored) Log.w(TAG, "Timed out restoring the built-in ANT radio state");
    }

    @TargetApi(17)
    private boolean airplaneModeOn() {
        if (Build.VERSION.SDK_INT >= 17) {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        }
        return Settings.System.getInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }
}
