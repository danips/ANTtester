package com.quantrity.anttester;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Insets;
import android.graphics.PorterDuff;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dsi.ant.AntService;
import com.dsi.ant.AntSupportChecker;
import com.dsi.ant.channel.AdapterInfo;
import com.dsi.ant.channel.ChannelNotAvailableException;
import com.dsi.ant.channel.ChannelNotAvailableReason;
import com.dsi.ant.channel.UnsupportedFeatureException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Lifecycle and view boundary for the ANT diagnostic screen. */
public class MainActivity extends Activity {
    private static final String TAG = "ANTtester";
    private static final String YES_TAG = "Y";
    private static final String NO_TAG = "N";
    private static final int ANT_HAL_PERMISSION_REQUEST = 1;
    private static final int MAX_TRANSIENT_SERVICE_RETRIES = 4;
    private static final long SERVICE_RETRY_DELAY_MS = 250L;
    private static final long SERVICE_QUERY_TIMEOUT_MS = 3000L;

    private ImageView antCapableIv;
    private TextView readinessTv;
    private TextView builtinAntDetectedTv;
    private TextView usbHostSupportTv;
    private TextView addonAdapterSupportTv;
    private TextView builtinFirmwareTv;
    private TextView externalAdaptersTv;
    private TextView unknownAdaptersTv;
    private View unknownAdaptersRow;
    private TextView antHalServiceTv;
    private TextView antRadioServiceTv;
    private TextView antUsbServiceTv;
    private View addonAdapterSupportRow;
    private View addonAdapterSupportDivider;
    private View builtinFirmwareRow;
    private View builtinFirmwareDivider;
    private View antHalServiceRow;
    private View antHalServiceDivider;
    private View antUsbServiceRow;
    private View antUsbServiceDivider;
    private TextView antPluginsTv;
    private ImageView builtinAntDetectedIv;
    private ImageView addonAdapterSupportIv;
    private ImageView builtinFirmwareIv;
    private ImageView antRadioServiceIv;
    private ImageView antRadioServiceLockIv;
    private ImageView antRadioServiceLock2Iv;
    private ImageView antUsbServiceIv;
    private ImageView antPluginsIv;
    private TextView usbDevicesTv1;
    private TextView usbDevicesTv2;
    private int statusErrorColor;
    private int statusSuccessColor;
    private int statusWarningColor;
    private int actionIconColor;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService probeExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService serviceExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService usbFirmwareExecutor = Executors.newSingleThreadExecutor();
    private PackageInspector packageInspector;
    private UsbInspector usbInspector;
    private BuiltInAntFirmwareProbe builtInFirmwareProbe;
    private RadioConnection radioConnection;
    private String usbPermissionAction;
    private PendingIntent usbPermissionRequest;
    private boolean usbReceiverRegistered;
    private boolean lifecycleActive;
    private boolean refreshActive;
    private boolean refreshAgain;
    private boolean usbAccessQueued;
    private long nextGeneration;
    private long activeGeneration;
    private int serviceRetryAttempts;
    private Future<?> probeFuture;
    private Future<?> serviceFuture;
    private Future<?> usbFirmwareFuture;
    private Runnable serviceRetryRunnable;
    private Runnable serviceTimeoutRunnable;
    private AntSupportSnapshot activeSnapshot;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            requestRefresh("USB broadcast");
        }
    };

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            usbPermissionRequest = null;
            requestRefresh("USB permission");
        }
    };

    private final class RadioConnection implements ServiceConnection {
        final long generation;
        AntService service;

        RadioConnection(long generation) { this.generation = generation; }

        private boolean isActive() {
            return radioConnection == this && isCurrent(generation);
        }

        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            if (!isActive()) return;
            try {
                this.service = new AntService(service);
                queryAdapters(this);
            } catch (RuntimeException e) {
                Log.w(TAG, "ANT service returned an invalid binder, generation=" + generation, e);
                applyServiceResult(generation, AntSupportSnapshot.ServiceState.QUERY_ERROR,
                        Collections.<AntAdapterSnapshot>emptyList(), "Invalid ANT service binder");
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            if (isActive()) {
                applyServiceResult(generation, AntSupportSnapshot.ServiceState.QUERY_ERROR,
                        Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service disconnected");
            }
        }

        @TargetApi(26)
        @Override public void onBindingDied(ComponentName name) {
            if (isActive()) {
                applyServiceResult(generation, AntSupportSnapshot.ServiceState.BIND_FAILED,
                        Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service binding died");
            }
        }

        @TargetApi(26)
        @Override public void onNullBinding(ComponentName name) {
            if (isActive()) {
                applyServiceResult(generation, AntSupportSnapshot.ServiceState.BIND_FAILED,
                        Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service returned no binding");
            }
        }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        packageInspector = new PackageInspector(this);
        usbInspector = new UsbInspector(this);
        builtInFirmwareProbe = new BuiltInAntFirmwareProbe(this);
        if (Build.VERSION.SDK_INT >= 35) Api35Insets.apply(findViewById(android.R.id.content));

        statusErrorColor = getResources().getColor(R.color.status_error);
        statusSuccessColor = getResources().getColor(R.color.status_success);
        statusWarningColor = getResources().getColor(R.color.status_warning);
        actionIconColor = getResources().getColor(R.color.action_icon);
        antCapableIv = findViewById(R.id.ant_capable_iv);
        readinessTv = findViewById(R.id.readiness_tv);
        builtinAntDetectedTv = findViewById(R.id.builtin_ant_detected_tv);
        usbHostSupportTv = findViewById(R.id.usb_host_support_tv);
        addonAdapterSupportTv = findViewById(R.id.addon_adapter_support_tv);
        builtinFirmwareTv = findViewById(R.id.builtin_firmware_tv);
        externalAdaptersTv = findViewById(R.id.external_adapters_tv);
        unknownAdaptersTv = findViewById(R.id.unknown_adapters_tv);
        unknownAdaptersRow = findViewById(R.id.unknown_adapters_row);
        antHalServiceTv = findViewById(R.id.ant_hal_service_tv);
        antRadioServiceTv = findViewById(R.id.ant_radio_service_tv);
        antUsbServiceTv = findViewById(R.id.ant_usb_service_tv);
        addonAdapterSupportRow = findViewById(R.id.addon_adapter_support_row);
        addonAdapterSupportDivider = findViewById(R.id.addon_adapter_support_divider);
        builtinFirmwareRow = findViewById(R.id.builtin_firmware_row);
        builtinFirmwareDivider = findViewById(R.id.builtin_firmware_divider);
        antHalServiceRow = findViewById(R.id.ant_hal_service_row);
        antHalServiceDivider = findViewById(R.id.ant_hal_service_divider);
        antUsbServiceRow = findViewById(R.id.ant_usb_service_tr);
        antUsbServiceDivider = findViewById(R.id.ant_usb_service_divider);
        antPluginsTv = findViewById(R.id.ant_plugins_tv);
        builtinAntDetectedIv = findViewById(R.id.builtin_ant_detected_iv);
        addonAdapterSupportIv = findViewById(R.id.addon_adapter_support_iv);
        builtinFirmwareIv = findViewById(R.id.builtin_firmware_iv);
        antRadioServiceIv = findViewById(R.id.ant_radio_service_iv);
        antRadioServiceLockIv = findViewById(R.id.ant_radio_service_lock_iv);
        antRadioServiceLock2Iv = findViewById(R.id.ant_radio_service_lock2_iv);
        antUsbServiceIv = findViewById(R.id.ant_usb_service_iv);
        antPluginsIv = findViewById(R.id.ant_plugins_iv);
        usbDevicesTv1 = findViewById(R.id.usb_devices_tv1);
        usbDevicesTv2 = findViewById(R.id.usb_devices_tv2);

        builtinAntDetectedIv.setColorFilter(statusErrorColor, PorterDuff.Mode.SRC_IN);
        antRadioServiceLockIv.setColorFilter(statusErrorColor, PorterDuff.Mode.SRC_IN);
        antRadioServiceLock2Iv.setColorFilter(statusErrorColor, PorterDuff.Mode.SRC_IN);
        ImageView refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { requestRefresh("manual", true); }
        });
        addonAdapterSupportIv.setOnClickListener(new PackageActionListener(AntPackages.USB_SERVICE));
        builtinFirmwareIv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (Build.VERSION.SDK_INT >= 23 && activeSnapshot != null
                        && activeSnapshot.firmware.builtInState == AntFirmwareFacts.BuiltInState.PERMISSION_REQUIRED) {
                    requestPermissions(new String[] {BuiltInAntFirmwareProbe.HAL_PERMISSION}, ANT_HAL_PERMISSION_REQUEST);
                } else {
                    openPackageAction(view, AntPackages.RADIO_SERVICE);
                }
            }
        });
        antRadioServiceIv.setOnClickListener(new PackageActionListener(AntPackages.RADIO_SERVICE));
        antUsbServiceIv.setOnClickListener(new PackageActionListener(AntPackages.USB_SERVICE));
        antPluginsIv.setOnClickListener(new PackageActionListener(AntPackages.PLUGINS_SERVICE));
        tintActionIcon(refreshButton);
        tintActionIcon(addonAdapterSupportIv);
        tintActionIcon(builtinFirmwareIv);
        tintActionIcon(antRadioServiceIv);
        tintActionIcon(antUsbServiceIv);
        tintActionIcon(antPluginsIv);
        render(AntSupportSnapshot.checking());
    }

    @Override protected void onResume() {
        super.onResume();
        lifecycleActive = true;
        registerUsbReceiver();
        requestRefresh("initial/resume");
    }

    @Override protected void onPause() {
        lifecycleActive = false;
        nextGeneration++;
        cancelProbeWork();
        doUnbindAntRadioService();
        unregisterUsbReceiver();
        super.onPause();
    }

    @TargetApi(23)
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == ANT_HAL_PERMISSION_REQUEST) requestRefresh("HAL permission");
    }

    private void requestRefresh(String reason) {
        requestRefresh(reason, false);
    }

    private void requestRefresh(String reason, final boolean requestUsbAccess) {
        if (!lifecycleActive) return;
        if (refreshActive) {
            refreshAgain = true;
            usbAccessQueued |= requestUsbAccess;
            return;
        }
        refreshActive = true;
        activeGeneration = ++nextGeneration;
        activeSnapshot = AntSupportSnapshot.checking();
        doUnbindAntRadioService();
        render(activeSnapshot);
        final long generation = activeGeneration;
        probeFuture = probeExecutor.submit(new Runnable() {
            @Override public void run() { collectLocalFacts(generation, requestUsbAccess); }
        });
        Log.d(TAG, "refresh started, generation=" + generation + ", reason=" + reason);
    }

    private void collectLocalFacts(final long generation, final boolean requestUsbAccess) {
        boolean builtin = false;
        boolean addon = false;
        boolean usbHost = false;
        ArrayList<String> issues = new ArrayList<String>();
        try { builtin = AntSupportChecker.hasAntFeature(getApplicationContext()); }
        catch (RuntimeException e) { issues.add("Built-in ANT probe failed"); }
        try { addon = AntSupportChecker.hasAntAddOn(getApplicationContext()); }
        catch (RuntimeException e) { issues.add("ANT add-on probe failed"); }
        usbHost = usbInspector.hasHostSupport();
        PackageState radio = packageInspector.inspect(AntPackages.RADIO_SERVICE);
        PackageState usb = packageInspector.inspect(AntPackages.USB_SERVICE);
        PackageState plugins = packageInspector.inspect(AntPackages.PLUGINS_SERVICE);
        PackageState hal = inspectHalService();
        PermissionState permission = packageInspector.inspectAntPermission();
        final UsbInspector.Scan usbScan = usbInspector.inspectDevices();
        AntSupportSnapshot.ServiceState serviceState = serviceStateForPackage(radio);
        final AntSupportSnapshot snapshot = new AntSupportSnapshot(
                issues.isEmpty() ? AntSupportSnapshot.ProbeStatus.COMPLETE : AntSupportSnapshot.ProbeStatus.UNKNOWN_ERROR,
                builtin, usbHost, addon, radio, usb, plugins, hal, permission, serviceState,
                Collections.<AntAdapterSnapshot>emptyList(), usbScan.devices, issues);
        mainHandler.post(new Runnable() {
            @Override public void run() { onLocalFacts(generation, snapshot, usbScan, requestUsbAccess); }
        });
    }

    private PackageState inspectHalService() {
        PackageState primary = packageInspector.inspect(AntPackages.HAL_SERVER);
        return primary.kind == PackageState.Kind.MISSING
                ? packageInspector.inspect(AntPackages.LEGACY_HAL_SERVICE) : primary;
    }

    private static AntSupportSnapshot.ServiceState serviceStateForPackage(PackageState state) {
        if (state == null) return AntSupportSnapshot.ServiceState.SERVICE_MISSING;
        if (state.kind == PackageState.Kind.MISSING || state.kind == PackageState.Kind.NOT_VISIBLE) {
            return AntSupportSnapshot.ServiceState.SERVICE_MISSING;
        }
        if (state.kind == PackageState.Kind.INSTALLED_DISABLED) {
            return AntSupportSnapshot.ServiceState.SERVICE_DISABLED;
        }
        if (state.kind == PackageState.Kind.VERSION_TOO_OLD) {
            return AntSupportSnapshot.ServiceState.SERVICE_TOO_OLD;
        }
        if (state.kind == PackageState.Kind.QUERY_ERROR) {
            return AntSupportSnapshot.ServiceState.QUERY_ERROR;
        }
        return AntSupportSnapshot.ServiceState.NOT_QUERIED;
    }

    private void onLocalFacts(long generation, AntSupportSnapshot snapshot, UsbInspector.Scan usbScan,
                              boolean requestUsbAccess) {
        if (!isCurrent(generation)) return;
        boolean canQuery = snapshot.radioService != null && snapshot.radioService.isInstalledEnabled()
                && snapshot.serviceState == AntSupportSnapshot.ServiceState.NOT_QUERIED
                && (snapshot.builtInFeatureDeclared || snapshot.usbHostSupported);
        activeSnapshot = canQuery ? snapshot.withService(AntSupportSnapshot.ServiceState.QUERYING,
                snapshot.adapters, null) : snapshot;
        startBuiltInFirmwareProbe(generation, !usbScan.devices.isEmpty());
        if (Build.VERSION.SDK_INT >= 12 && usbScan.manager != null) {
            startUsbFirmwareProbe(generation, usbScan, requestUsbAccess);
        }
        render(activeSnapshot);
        if (canQuery) beginServiceBinding(generation);
        finishRefresh(generation);
    }

    private void startBuiltInFirmwareProbe(final long generation, boolean usbDevicesPresent) {
        boolean legacy = activeSnapshot.allowsLegacyBuiltInProbe(Build.VERSION.SDK_INT, usbDevicesPresent);
        if (!legacy && !activeSnapshot.allowsDirectBuiltInProbe()) return;
        ComponentName service = legacy ? LegacyAntFirmwareQuery.findService(this)
                : BuiltInAntFirmwareProbe.findHalService(this);
        if (service == null) return;
        if (!legacy && !BuiltInAntFirmwareProbe.hasHalPermission(this)) {
            if (Build.VERSION.SDK_INT >= 23) {
                activeSnapshot = activeSnapshot.withFirmware(activeSnapshot.firmware.withBuiltIn(
                        AntFirmwareFacts.BuiltInState.PERMISSION_REQUIRED, null));
            }
            return;
        }
        activeSnapshot = activeSnapshot.withFirmware(activeSnapshot.firmware.withBuiltIn(
                AntFirmwareFacts.BuiltInState.CHECKING, null));
        BuiltInAntFirmwareProbe.Listener listener = new BuiltInAntFirmwareProbe.Listener() {
            @Override public void onResult(String version) {
                if (!isCurrent(generation)) return;
                activeSnapshot = activeSnapshot.withFirmware(activeSnapshot.firmware.withBuiltIn(
                        version == null ? AntFirmwareFacts.BuiltInState.FAILED
                                : AntFirmwareFacts.BuiltInState.VERIFIED, version));
                render(activeSnapshot);
                finishRefresh(generation);
            }
        };
        if (legacy) builtInFirmwareProbe.startLegacy(service, listener);
        else builtInFirmwareProbe.startHal(service, listener);
    }

    @TargetApi(12)
    private void startUsbFirmwareProbe(final long generation, final UsbInspector.Scan scan,
                                       boolean requestUsbAccess) {
        final Map<String, String> versions = new TreeMap<String, String>();
        final Map<String, UsbDevice> readable = new TreeMap<String, UsbDevice>();
        boolean allowDirect = activeSnapshot.radioService == null
                || !activeSnapshot.radioService.isInstalledEnabled() || !activeSnapshot.antAddOnSupported;
        for (Map.Entry<String, UsbDevice> device : scan.antAdapters.entrySet()) {
            versions.put(device.getKey(), null);
            if (!allowDirect) continue;
            try {
                if (scan.manager.hasPermission(device.getValue())) {
                    readable.put(device.getKey(), device.getValue());
                } else if (requestUsbAccess && usbPermissionRequest == null) {
                    requestUsbFirmwarePermission(scan.manager, device.getValue());
                    requestUsbAccess = false;
                }
            } catch (RuntimeException e) {
                Log.d(TAG, "USB adapter is no longer accessible", e);
            }
        }
        activeSnapshot = activeSnapshot.withFirmware(activeSnapshot.firmware.withUsb(versions, !readable.isEmpty()));
        if (readable.isEmpty()) return;

        usbFirmwareFuture = usbFirmwareExecutor.submit(new Runnable() {
            @Override public void run() {
                for (Map.Entry<String, UsbDevice> device : readable.entrySet()) {
                    if (Thread.currentThread().isInterrupted()) return;
                    versions.put(device.getKey(), AntUsbFirmwareReader.readVersion(scan.manager, device.getValue()));
                }
                mainHandler.post(new Runnable() {
                    @Override public void run() {
                        if (!isCurrent(generation)) return;
                        usbFirmwareFuture = null;
                        activeSnapshot = activeSnapshot.withFirmware(activeSnapshot.firmware.withUsb(versions, false));
                        render(activeSnapshot);
                        finishRefresh(generation);
                    }
                });
            }
        });
    }

    @TargetApi(12)
    private void requestUsbFirmwarePermission(UsbManager manager, UsbDevice device) {
        try {
            if (usbPermissionAction == null) {
                String action = getPackageName() + ".USB_PERMISSION." + UUID.randomUUID();
                IntentFilter filter = new IntentFilter(action);
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(usbPermissionReceiver, filter);
                }
                usbPermissionAction = action;
            }
            Intent intent = new Intent(usbPermissionAction).setPackage(getPackageName());
            int flags = PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            usbPermissionRequest = PendingIntent.getBroadcast(this, 0, intent, flags);
            manager.requestPermission(device, usbPermissionRequest);
        } catch (SecurityException | IllegalArgumentException e) {
            Log.d(TAG, "Unable to request access to the ANT USB adapter", e);
            if (usbPermissionRequest != null) usbPermissionRequest.cancel();
            usbPermissionRequest = null;
        }
    }

    private void beginServiceBinding(final long generation) {
        serviceRetryAttempts = 0;
        final RadioConnection connection = new RadioConnection(generation);
        radioConnection = connection;
        try {
            if (!AntService.bindService(this, connection)) {
                applyServiceResult(generation, AntSupportSnapshot.ServiceState.BIND_FAILED,
                        Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service rejected binding");
            } else {
                serviceTimeoutRunnable = new Runnable() {
                    @Override public void run() {
                        if (connection.isActive()) {
                            applyServiceResult(generation, AntSupportSnapshot.ServiceState.TIMED_OUT,
                                    Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service query timed out");
                        }
                    }
                };
                mainHandler.postDelayed(serviceTimeoutRunnable, SERVICE_QUERY_TIMEOUT_MS);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "ANT service bind denied, generation=" + generation, e);
            applyServiceResult(generation, AntSupportSnapshot.ServiceState.PERMISSION_BLOCKED,
                    Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service permission denied");
        } catch (RuntimeException e) {
            Log.w(TAG, "ANT service bind failed, generation=" + generation, e);
            applyServiceResult(generation, AntSupportSnapshot.ServiceState.BIND_FAILED,
                    Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service bind failed");
        }
    }

    private void queryAdapters(final RadioConnection connection) {
        if (!connection.isActive() || connection.service == null) return;
        final long generation = connection.generation;
        final AntService service = connection.service;
        serviceFuture = serviceExecutor.submit(new Runnable() {
            @Override public void run() {
                try {
                    Collection<AdapterInfo> infos = service.getAdapterProvider().getAdaptersInfo(getApplicationContext());
                    ArrayList<AntAdapterSnapshot> adapters = new ArrayList<AntAdapterSnapshot>();
                    if (infos != null) {
                        for (AdapterInfo info : infos) if (info != null) adapters.add(AntAdapterSnapshot.from(info));
                    }
                    adapters = new ArrayList<AntAdapterSnapshot>(AntAdapterSorter.sort(adapters));
                    postServiceResult(generation, adapters.isEmpty()
                                    ? AntSupportSnapshot.ServiceState.NO_ADAPTER : AntSupportSnapshot.ServiceState.AVAILABLE,
                            adapters, null);
                } catch (UnsupportedFeatureException e) {
                    postServiceResult(generation, AntSupportSnapshot.ServiceState.SERVICE_TOO_OLD,
                            Collections.<AntAdapterSnapshot>emptyList(), "ANT adapter API is not supported");
                } catch (SecurityException e) {
                    postServiceResult(generation, AntSupportSnapshot.ServiceState.PERMISSION_BLOCKED,
                            Collections.<AntAdapterSnapshot>emptyList(), "ANT service permission blocked");
                } catch (android.os.RemoteException e) {
                    postServiceResult(generation, AntSupportSnapshot.ServiceState.QUERY_ERROR,
                            Collections.<AntAdapterSnapshot>emptyList(), "ANT service remote query failed");
                } catch (ChannelNotAvailableException e) {
                    if (e.reasonCode == ChannelNotAvailableReason.SERVICE_INITIALIZING) {
                        postServiceResult(generation, AntSupportSnapshot.ServiceState.TIMED_OUT,
                                Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service is still initializing", true);
                    } else if (e.reasonCode == ChannelNotAvailableReason.NO_ADAPTERS_EXIST) {
                        postServiceResult(generation, AntSupportSnapshot.ServiceState.NO_ADAPTER,
                                Collections.<AntAdapterSnapshot>emptyList(), null);
                    } else {
                        postServiceResult(generation, AntSupportSnapshot.ServiceState.QUERY_ERROR,
                                Collections.<AntAdapterSnapshot>emptyList(), "ANT adapter is not available");
                    }
                } catch (RuntimeException e) {
                    Log.w(TAG, "ANT adapter query failed, generation=" + generation, e);
                    postServiceResult(generation, AntSupportSnapshot.ServiceState.QUERY_ERROR,
                            Collections.<AntAdapterSnapshot>emptyList(), "ANT adapter query failed");
                }
            }
        });
    }

    private void postServiceResult(final long generation, final AntSupportSnapshot.ServiceState state,
                                   final List<AntAdapterSnapshot> adapters, final String issue) {
        postServiceResult(generation, state, adapters, issue, state == AntSupportSnapshot.ServiceState.NO_ADAPTER);
    }

    private void postServiceResult(final long generation, final AntSupportSnapshot.ServiceState state,
                                   final List<AntAdapterSnapshot> adapters, final String issue,
                                   final boolean retryable) {
        mainHandler.post(new Runnable() {
            @Override public void run() {
                if (!isCurrent(generation) || radioConnection == null
                        || radioConnection.generation != generation) return;
                serviceFuture = null;
                if (retryable && serviceRetryAttempts++ < MAX_TRANSIENT_SERVICE_RETRIES - 1
                        && radioConnection.service != null) {
                    removeServiceRetry();
                    final RadioConnection connection = radioConnection;
                    serviceRetryRunnable = new Runnable() {
                        @Override public void run() { queryAdapters(connection); }
                    };
                    mainHandler.postDelayed(serviceRetryRunnable, SERVICE_RETRY_DELAY_MS);
                    return;
                }
                applyServiceResult(generation, state, adapters, issue);
            }
        });
    }

    private void applyServiceResult(long generation, AntSupportSnapshot.ServiceState state,
                                    List<AntAdapterSnapshot> adapters, String issue) {
        if (!isCurrent(generation) || activeSnapshot == null
                || activeSnapshot.serviceState != AntSupportSnapshot.ServiceState.QUERYING) return;
        activeSnapshot = activeSnapshot.withService(state, adapters, issue);
        doUnbindAntRadioService();
        render(activeSnapshot);
        finishRefresh(generation);
    }

    private void finishRefresh(long generation) {
        if (!isCurrent(generation) || activeSnapshot.serviceState == AntSupportSnapshot.ServiceState.QUERYING
                || activeSnapshot.firmware.builtInState == AntFirmwareFacts.BuiltInState.CHECKING
                || activeSnapshot.firmware.usbChecking) return;
        refreshActive = false;
        if (refreshAgain && lifecycleActive) {
            boolean requestUsbAccess = usbAccessQueued;
            refreshAgain = false;
            usbAccessQueued = false;
            requestRefresh("coalesced", requestUsbAccess);
        }
    }

    private boolean isCurrent(long generation) {
        return lifecycleActive && refreshActive && activeGeneration == generation;
    }

    private void cancelProbeWork() {
        if (probeFuture != null) probeFuture.cancel(true);
        probeFuture = null;
        if (usbFirmwareFuture != null) usbFirmwareFuture.cancel(true);
        usbFirmwareFuture = null;
        builtInFirmwareProbe.cancel();
        refreshActive = false;
        refreshAgain = false;
        usbAccessQueued = false;
    }

    private void removeServiceRetry() {
        if (serviceRetryRunnable != null) mainHandler.removeCallbacks(serviceRetryRunnable);
        serviceRetryRunnable = null;
    }

    private void removeServiceWork() {
        removeServiceRetry();
        if (serviceTimeoutRunnable != null) mainHandler.removeCallbacks(serviceTimeoutRunnable);
        serviceTimeoutRunnable = null;
    }

    private void doUnbindAntRadioService() {
        removeServiceWork();
        if (serviceFuture != null) serviceFuture.cancel(true);
        serviceFuture = null;
        RadioConnection connection = radioConnection;
        radioConnection = null;
        if (connection != null) {
            try {
                unbindService(connection);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "ANT service unbind state mismatch");
            }
        }
    }

    private void registerUsbReceiver() {
        if (Build.VERSION.SDK_INT >= 12 && !usbReceiverRegistered) {
            IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            registerReceiver(usbReceiver, filter);
            usbReceiverRegistered = true;
        }
    }

    private void unregisterUsbReceiver() {
        if (usbReceiverRegistered) {
            try { unregisterReceiver(usbReceiver); }
            catch (IllegalArgumentException e) { Log.w(TAG, "USB receiver state mismatch"); }
            usbReceiverRegistered = false;
        }
    }

    private void render(AntSupportSnapshot snapshot) {
        AntReadinessResult result = AntReadinessEvaluator.evaluate(snapshot);
        readinessTv.setText(readinessText(result.readiness));
        boolean verified = result.readiness == AntReadiness.READY_BUILT_IN
                || result.readiness == AntReadiness.READY_EXTERNAL_USB
                || result.readiness == AntReadiness.VERIFIED_BUILT_IN
                || result.readiness == AntReadiness.VERIFIED_EXTERNAL_USB;
        int color = verified ? statusSuccessColor
                : result.readiness == AntReadiness.CHECKING
                || result.readiness == AntReadiness.SETUP_REQUIRED_SERVICE
                || result.readiness == AntReadiness.SETUP_REQUIRED_ADAPTER
                || result.readiness == AntReadiness.DETECTED_EXTERNAL_USB ? statusWarningColor : statusErrorColor;
        readinessTv.setTextColor(color);
        antCapableIv.setImageResource(verified ? R.drawable.ic_ok_green_36dp : R.drawable.ic_nok_red_36dp);
        antCapableIv.setColorFilter(color, PorterDuff.Mode.SRC_IN);

        boolean checking = snapshot.probeStatus == AntSupportSnapshot.ProbeStatus.CHECKING;
        boolean builtIn = snapshot.hasBuiltInAnt();
        setBooleanStatus(builtinAntDetectedTv, checking ? null : builtIn);
        setBooleanStatus(usbHostSupportTv, checking ? null : snapshot.usbHostSupported);
        setRowVisibility(builtinFirmwareRow, builtinFirmwareDivider, builtIn);
        setRowVisibility(antHalServiceRow, antHalServiceDivider, builtIn
                || snapshot.halService != null && snapshot.halService.isInstalled());
        addonAdapterSupportRow.setVisibility(snapshot.usbHostSupported ? View.VISIBLE : View.GONE);
        addonAdapterSupportDivider.setVisibility(snapshot.usbHostSupported ? View.VISIBLE : View.GONE);
        setBooleanStatus(addonAdapterSupportTv, checking ? null
                : snapshot.usbService != null && snapshot.usbService.isInstalled());
        setPackageAction(addonAdapterSupportIv, snapshot.usbService);
        setPackageStatus(antRadioServiceTv, antRadioServiceIv, snapshot.radioService);
        setPackageStatus(antUsbServiceTv, antUsbServiceIv, snapshot.usbService);
        setPackageStatus(antPluginsTv, antPluginsIv, snapshot.pluginsService);
        setPackageStatus(antHalServiceTv, null, snapshot.halService);
        setRowVisibility(antUsbServiceRow, antUsbServiceDivider, snapshot.usbHostSupported);
        renderAdapters(snapshot);
        renderUsbDevices(snapshot.usbDevices);
        int permissionVisibility = snapshot.hasAntPermissionWarning() ? View.VISIBLE : View.INVISIBLE;
        antRadioServiceLockIv.setVisibility(permissionVisibility);
        antRadioServiceLock2Iv.setVisibility(permissionVisibility);
    }

    private void setRowVisibility(View row, View divider, boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        row.setVisibility(visibility);
        divider.setVisibility(visibility);
    }

    private void renderAdapters(AntSupportSnapshot snapshot) {
        ArrayList<AntAdapterSnapshot> builtin = new ArrayList<AntAdapterSnapshot>();
        ArrayList<String> external = new ArrayList<String>();
        ArrayList<AntAdapterSnapshot> unknown = new ArrayList<AntAdapterSnapshot>();
        for (AntAdapterSnapshot adapter : snapshot.adapters) {
            switch (adapter.kind) {
                case BUILT_IN:
                    builtin.add(adapter);
                    break;
                case EXTERNAL:
                    external.add(adapter.knownFirmwareVersion());
                    break;
                case UNKNOWN:
                    unknown.add(adapter);
                    break;
            }
        }
        unknownAdaptersRow.setVisibility(unknown.isEmpty() ? View.GONE : View.VISIBLE);
        unknownAdaptersTv.setText(joinFirmware(unknown));
        builtinAntDetectedIv.setVisibility(View.INVISIBLE);
        builtinFirmwareIv.setVisibility(View.INVISIBLE);
        builtinFirmwareIv.setContentDescription(getString(R.string.ant_radio_service));
        builtinFirmwareTv.setTextColor(statusWarningColor);
        if (!builtin.isEmpty()) {
            builtinFirmwareTv.setText(joinFirmware(builtin));
            builtinFirmwareTv.setTextColor(statusSuccessColor);
        } else if (snapshot.firmware.builtInVersion != null) {
            builtinFirmwareTv.setText(snapshot.firmware.builtInVersion);
            builtinFirmwareTv.setTextColor(statusSuccessColor);
        } else if (snapshot.firmware.builtInState == AntFirmwareFacts.BuiltInState.PERMISSION_REQUIRED) {
            builtinFirmwareTv.setText(R.string.allow_ant_access);
            builtinFirmwareIv.setContentDescription(getString(R.string.allow_ant_access));
            setActionIcon(builtinFirmwareIv, R.drawable.ic_info_outline_white_24dp);
            builtinFirmwareIv.setVisibility(View.VISIBLE);
        } else if (snapshot.firmware.builtInState == AntFirmwareFacts.BuiltInState.CHECKING
                || snapshot.serviceState == AntSupportSnapshot.ServiceState.QUERYING) {
            builtinFirmwareTv.setText(R.string.checking);
        } else if (snapshot.firmware.builtInState == AntFirmwareFacts.BuiltInState.FAILED) {
            builtinFirmwareTv.setText(R.string.firmware_unreadable);
        } else if (snapshot.builtInFeatureDeclared
                && snapshot.serviceState == AntSupportSnapshot.ServiceState.SERVICE_TOO_OLD) {
            builtinFirmwareTv.setText(R.string.update_ars);
            builtinFirmwareIv.setContentDescription(getString(R.string.update_ant_radio_service));
            setActionIcon(builtinFirmwareIv, R.drawable.ic_file_download_black_24dp);
            builtinFirmwareIv.setTag(NO_TAG);
            builtinFirmwareIv.setVisibility(View.VISIBLE);
        } else if (snapshot.builtInFeatureDeclared) {
            if (snapshot.radioService != null && snapshot.radioService.kind == PackageState.Kind.INSTALLED_DISABLED) {
                builtinFirmwareTv.setText(R.string.enable_ars);
                builtinFirmwareIv.setContentDescription(getString(R.string.open_ant_radio_service_settings));
            } else if (snapshot.radioService == null || !snapshot.radioService.isInstalledEnabled()) {
                builtinFirmwareTv.setText(R.string.requires_ars);
            } else {
                builtinFirmwareTv.setText(R.string.firmware_unreadable);
                builtinAntDetectedIv.setVisibility(View.VISIBLE);
            }
            setPackageAction(builtinFirmwareIv, snapshot.radioService);
        } else {
            builtinFirmwareTv.setText(R.string.not_available);
            builtinFirmwareTv.setTextColor(statusErrorColor);
        }

        String externalText = ExternalAdapterReadings.format(external, snapshot.firmware.usbVersions.values(),
                getString(R.string.ant_radio_service), getString(R.string.usb_devices),
                getString(R.string.firmware_unknown), getString(R.string.detected_via_usb));
        if (externalText.length() > 0) {
            externalAdaptersTv.setText(externalText);
            externalAdaptersTv.setTextColor(statusSuccessColor);
        } else if (snapshot.probeStatus == AntSupportSnapshot.ProbeStatus.CHECKING
                || snapshot.serviceState == AntSupportSnapshot.ServiceState.QUERYING) {
            externalAdaptersTv.setText(R.string.checking);
            externalAdaptersTv.setTextColor(statusWarningColor);
        } else {
            externalAdaptersTv.setText(snapshot.serviceState == AntSupportSnapshot.ServiceState.AVAILABLE
                    || snapshot.serviceState == AntSupportSnapshot.ServiceState.NO_ADAPTER
                    ? R.string.no : R.string.not_available);
            externalAdaptersTv.setTextColor(statusErrorColor);
        }
    }

    private String joinFirmware(List<AntAdapterSnapshot> adapters) {
        StringBuilder value = new StringBuilder();
        for (AntAdapterSnapshot adapter : adapters) {
            if (value.length() > 0) value.append(", ");
            String version = adapter.knownFirmwareVersion();
            value.append(version != null ? version : getString(R.string.firmware_unknown));
        }
        return value.toString();
    }

    private void renderUsbDevices(List<UsbDeviceSnapshot> devices) {
        if (devices == null || devices.isEmpty()) {
            usbDevicesTv1.setVisibility(View.GONE);
            usbDevicesTv2.setVisibility(View.GONE);
            return;
        }
        StringBuilder text = new StringBuilder();
        for (UsbDeviceSnapshot device : devices) {
            if (text.length() > 0) text.append("\n\n");
            text.append(device.format());
        }
        usbDevicesTv2.setText(text.toString());
        usbDevicesTv1.setVisibility(View.VISIBLE);
        usbDevicesTv2.setVisibility(View.VISIBLE);
    }

    private void setBooleanStatus(TextView view, Boolean value) {
        if (value == null) {
            view.setText(R.string.checking);
            view.setTextColor(statusWarningColor);
        } else {
            view.setText(value ? R.string.yes : R.string.no);
            view.setTextColor(value ? statusSuccessColor : statusErrorColor);
        }
    }

    private void setPackageStatus(TextView text, ImageView icon, PackageState state) {
        if (icon != null) setPackageAction(icon, state);
        if (state == null) {
            text.setText(R.string.checking);
            text.setTextColor(statusWarningColor);
            return;
        }
        if (state.isInstalledEnabled() || state.kind == PackageState.Kind.VERSION_TOO_OLD) {
            text.setText(state.versionName);
            text.setTextColor(state.isInstalledEnabled() ? statusSuccessColor : statusWarningColor);
        } else {
            text.setText(state.kind == PackageState.Kind.INSTALLED_DISABLED ? R.string.disabled : R.string.not_available);
            text.setTextColor(state.kind == PackageState.Kind.INSTALLED_DISABLED ? statusWarningColor : statusErrorColor);
        }
    }

    private void setPackageAction(ImageView icon, PackageState state) {
        icon.setVisibility(state == null ? View.INVISIBLE : View.VISIBLE);
        boolean installed = state != null && state.isInstalled();
        setActionIcon(icon, installed ? R.drawable.ic_info_outline_white_24dp
                : R.drawable.ic_file_download_black_24dp);
        icon.setTag(installed ? YES_TAG : NO_TAG);
    }

    private int readinessText(AntReadiness readiness) {
        switch (readiness) {
            case READY_BUILT_IN: return R.string.ready_built_in;
            case READY_EXTERNAL_USB: return R.string.ready_external_usb;
            case VERIFIED_BUILT_IN: return R.string.verified_built_in;
            case VERIFIED_EXTERNAL_USB: return R.string.verified_external_usb;
            case DETECTED_EXTERNAL_USB: return R.string.detected_via_usb;
            case SETUP_REQUIRED_SERVICE: return R.string.setup_required_service;
            case SETUP_REQUIRED_ADAPTER: return R.string.setup_required_adapter;
            case BLOCKED_PERMISSION: return R.string.blocked_permission;
            case UNSUPPORTED: return R.string.unsupported;
            case UNKNOWN_ERROR: return R.string.unknown_error;
            default: return R.string.checking;
        }
    }

    private void setActionIcon(ImageView imageView, int drawableResource) {
        imageView.setImageResource(drawableResource);
        tintActionIcon(imageView);
    }

    private void tintActionIcon(ImageView imageView) {
        imageView.setColorFilter(actionIconColor, PorterDuff.Mode.SRC_IN);
    }

    @Override protected void onDestroy() {
        cancelProbeWork();
        doUnbindAntRadioService();
        probeExecutor.shutdownNow();
        serviceExecutor.shutdownNow();
        usbFirmwareExecutor.shutdownNow();
        if (usbPermissionRequest != null) usbPermissionRequest.cancel();
        if (usbPermissionAction != null) unregisterReceiver(usbPermissionReceiver);
        super.onDestroy();
    }

    public void statusIconClick(View view) {
        if (view.getId() == R.id.builtin_ant_detected_iv) {
            Toast.makeText(this, builtinFirmwareTv.getText(), Toast.LENGTH_SHORT).show();
        } else if (view.getId() == R.id.ant_radio_service_lock_iv
                || view.getId() == R.id.ant_radio_service_lock2_iv) {
            new AlertDialog.Builder(this).setIcon(R.drawable.ic_lock_red_24dp)
                    .setTitle(R.string.error_title).setMessage(R.string.use_ant_hardware_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            showInstalledAppDetails(MainActivity.this, AntPackages.RADIO_SERVICE);
                        }
                    }).show();
        }
    }

    private final class PackageActionListener implements View.OnClickListener {
        private final String packageName;
        PackageActionListener(String packageName) { this.packageName = packageName; }

        @Override public void onClick(View view) {
            openPackageAction(view, packageName);
        }
    }

    private void openPackageAction(View view, String packageName) {
        if (YES_TAG.equals(view.getTag())) showInstalledAppDetails(this, packageName);
        else openPackageListing(packageName);
    }

    private void openPackageListing(String packageName) {
        Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName));
        try {
            startActivity(market);
            return;
        } catch (ActivityNotFoundException | SecurityException e) {
            // A browser may still be available.
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
            return;
        } catch (ActivityNotFoundException e) {
            // Fall through to a visible explanation.
        } catch (SecurityException e) {
            // Fall through to a visible explanation.
        }
        Toast.makeText(this, R.string.no_app_for_action, Toast.LENGTH_SHORT).show();
    }

    private static void showInstalledAppDetails(Context context, String packageName) {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= 9) {
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", packageName, null));
        } else {
            intent.setAction(Intent.ACTION_VIEW);
            intent.setClassName("com.android.settings", "com.android.settings.InstalledAppDetails");
            intent.putExtra(Build.VERSION.SDK_INT == 8 ? "pkg" : "com.android.settings.ApplicationPkgName", packageName);
        }
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_app_for_action, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(context, R.string.no_app_for_action, Toast.LENGTH_SHORT).show();
        }
    }

    /** Keeps content clear of mandatory edge-to-edge system bars on Android 15+. */
    @SuppressLint("UseRequiresApi")
    @TargetApi(35)
    private static class Api35Insets {
        static void apply(final View content) {
            final int initialLeft = content.getPaddingLeft();
            final int initialTop = content.getPaddingTop();
            final int initialRight = content.getPaddingRight();
            final int initialBottom = content.getPaddingBottom();
            content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                    Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                    view.setPadding(initialLeft + bars.left, initialTop + bars.top,
                            initialRight + bars.right, initialBottom + bars.bottom);
                    return insets;
                }
            });
            content.requestApplyInsets();
        }
    }
}
