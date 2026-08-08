package com.quantrity.anttester;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
import com.dsi.ant.channel.UnsupportedFeatureException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lifecycle and view boundary for the ANT diagnostic screen. */
public class MainActivity extends Activity {
    private static final String TAG = "ANTtester";
    private static final String YES_TAG = "Y";
    private static final String NO_TAG = "N";
    private static final int MIN_ADAPTER_PROVIDER_SERVICE_VERSION = 40702;
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
    private TextView antHalServiceTv;
    private TextView antRadioServiceTv;
    private TextView antUsbServiceTv;
    private View addonAdapterSupportRow;
    private View addonAdapterSupportDivider;
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
    private PackageInspector packageInspector;
    private UsbInspector usbInspector;
    private AntService antRadioService;
    private boolean antRadioServiceBindRequested;
    private boolean antRadioServiceConnected;
    private boolean usbReceiverRegistered;
    private boolean lifecycleActive;
    private boolean refreshActive;
    private boolean refreshAgain;
    private long nextGeneration;
    private long activeGeneration;
    private int serviceRetryAttempts;
    private java.util.concurrent.Future<?> probeFuture;
    private Runnable serviceRetryRunnable;
    private Runnable serviceTimeoutRunnable;
    private AntSupportSnapshot activeSnapshot;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            requestRefresh("USB broadcast");
        }
    };

    private final ServiceConnection antRadioServiceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            if (!isCurrent(activeGeneration)) return;
            try {
                antRadioService = new AntService(service);
                antRadioServiceConnected = true;
                queryAdapters(activeGeneration);
            } catch (RuntimeException e) {
                Log.w(TAG, "ANT service returned an invalid binder, generation=" + activeGeneration, e);
                applyServiceResult(activeGeneration, AntSupportSnapshot.ServiceState.QUERY_ERROR,
                        Collections.<AntAdapterSnapshot>emptyList(), "Invalid ANT service binder");
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            antRadioServiceConnected = false;
            antRadioService = null;
            removeServiceWork();
            if (isCurrent(activeGeneration) && refreshActive) {
                applyServiceResult(activeGeneration, AntSupportSnapshot.ServiceState.QUERY_ERROR,
                        Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service disconnected");
            }
        }

        @TargetApi(26)
        @Override public void onBindingDied(ComponentName name) {
            antRadioServiceBindRequested = false;
            antRadioServiceConnected = false;
            antRadioService = null;
            removeServiceWork();
            if (isCurrent(activeGeneration) && refreshActive) {
                applyServiceResult(activeGeneration, AntSupportSnapshot.ServiceState.BIND_FAILED,
                        Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service binding died");
            }
        }

        @TargetApi(26)
        @Override public void onNullBinding(ComponentName name) {
            antRadioServiceBindRequested = false;
            antRadioServiceConnected = false;
            removeServiceWork();
            if (isCurrent(activeGeneration) && refreshActive) {
                applyServiceResult(activeGeneration, AntSupportSnapshot.ServiceState.BIND_FAILED,
                        Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service returned no binding");
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        packageInspector = new PackageInspector(this);
        usbInspector = new UsbInspector(this);
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
        antHalServiceTv = findViewById(R.id.ant_hal_service_tv);
        antRadioServiceTv = findViewById(R.id.ant_radio_service_tv);
        antUsbServiceTv = findViewById(R.id.ant_usb_service_tv);
        addonAdapterSupportRow = findViewById(R.id.addon_adapter_support_row);
        addonAdapterSupportDivider = findViewById(R.id.addon_adapter_support_divider);
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
            @Override public void onClick(View view) { requestRefresh("manual"); }
        });
        addonAdapterSupportIv.setOnClickListener(new PackageActionListener(AntPackages.USB_SERVICE));
        builtinFirmwareIv.setOnClickListener(new PackageActionListener(AntPackages.RADIO_SERVICE));
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

    @Override protected void onStart() {
        super.onStart();
        lifecycleActive = true;
        registerUsbReceiver();
        requestRefresh("initial/resume");
    }

    @Override protected void onStop() {
        lifecycleActive = false;
        nextGeneration++;
        cancelProbeWork();
        removeServiceRetry();
        doUnbindAntRadioService();
        unregisterUsbReceiver();
        super.onStop();
    }

    private void requestRefresh(String reason) {
        if (!lifecycleActive) return;
        if (refreshActive) {
            refreshAgain = true;
            return;
        }
        refreshActive = true;
        activeGeneration = ++nextGeneration;
        activeSnapshot = AntSupportSnapshot.checking();
        doUnbindAntRadioService();
        render(activeSnapshot);
        final long generation = activeGeneration;
        probeFuture = probeExecutor.submit(new Runnable() {
            @Override public void run() { collectLocalFacts(generation); }
        });
        Log.d(TAG, "refresh started, generation=" + generation + ", reason=" + reason);
    }

    private void collectLocalFacts(final long generation) {
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
        List<UsbDeviceSnapshot> devices = usbInspector.inspectDevices();
        AntSupportSnapshot.ServiceState serviceState = serviceStateForPackage(radio);
        if (radio != null && radio.isInstalledEnabled() && radio.versionCode > 0
                && radio.versionCode < MIN_ADAPTER_PROVIDER_SERVICE_VERSION) {
            serviceState = AntSupportSnapshot.ServiceState.SERVICE_TOO_OLD;
        }
        final AntSupportSnapshot snapshot = new AntSupportSnapshot(
                issues.isEmpty() ? AntSupportSnapshot.ProbeStatus.COMPLETE : AntSupportSnapshot.ProbeStatus.UNKNOWN_ERROR,
                builtin, usbHost, addon, radio, usb, plugins, hal, permission, serviceState,
                Collections.<AntAdapterSnapshot>emptyList(), devices, issues);
        mainHandler.post(new Runnable() {
            @Override public void run() { onLocalFacts(generation, snapshot); }
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
        if (state.kind == PackageState.Kind.QUERY_ERROR) {
            return AntSupportSnapshot.ServiceState.QUERY_ERROR;
        }
        return AntSupportSnapshot.ServiceState.NOT_QUERIED;
    }

    private void onLocalFacts(long generation, AntSupportSnapshot snapshot) {
        if (!isCurrent(generation)) return;
        activeSnapshot = snapshot;
        render(snapshot);
        boolean canQuery = snapshot.radioService != null && snapshot.radioService.isInstalledEnabled()
                && snapshot.serviceState == AntSupportSnapshot.ServiceState.NOT_QUERIED
                && snapshot.antPermission.isGranted()
                && (snapshot.builtInFeatureDeclared || snapshot.usbHostSupported);
        if (canQuery) beginServiceBinding(generation);
        else finishRefresh(generation);
    }

    private void beginServiceBinding(long generation) {
        serviceRetryAttempts = 0;
        try {
            antRadioServiceBindRequested = AntService.bindService(this, antRadioServiceConnection);
            if (!antRadioServiceBindRequested) {
                applyServiceResult(generation, AntSupportSnapshot.ServiceState.BIND_FAILED,
                        Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service rejected binding");
            } else {
                serviceTimeoutRunnable = new Runnable() {
                    @Override public void run() {
                        if (isCurrent(generation)) {
                            applyServiceResult(generation, AntSupportSnapshot.ServiceState.TIMED_OUT,
                                    Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service query timed out");
                        }
                    }
                };
                mainHandler.postDelayed(serviceTimeoutRunnable, SERVICE_QUERY_TIMEOUT_MS);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "ANT service bind denied, generation=" + generation, e);
            applyServiceResult(generation, AntSupportSnapshot.ServiceState.BIND_FAILED,
                    Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service permission denied");
        } catch (RuntimeException e) {
            Log.w(TAG, "ANT service bind failed, generation=" + generation, e);
            applyServiceResult(generation, AntSupportSnapshot.ServiceState.BIND_FAILED,
                    Collections.<AntAdapterSnapshot>emptyList(), "ANT Radio Service bind failed");
        }
    }

    private void queryAdapters(final long generation) {
        if (!isCurrent(generation) || antRadioService == null) return;
        final AntService service = antRadioService;
        probeExecutor.submit(new Runnable() {
            @Override public void run() {
                try {
                    Collection<AdapterInfo> infos = service.getAdapterProvider().getAdaptersInfo(getApplicationContext());
                    ArrayList<AntAdapterSnapshot> adapters = new ArrayList<AntAdapterSnapshot>();
                    if (infos != null) for (AdapterInfo info : infos) adapters.add(AntAdapterSnapshot.from(info));
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
                    postServiceResult(generation, AntSupportSnapshot.ServiceState.QUERY_ERROR,
                            Collections.<AntAdapterSnapshot>emptyList(), "ANT adapter is not available");
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
        mainHandler.post(new Runnable() {
            @Override public void run() {
                if (!isCurrent(generation)) return;
                if (state == AntSupportSnapshot.ServiceState.NO_ADAPTER
                        && serviceRetryAttempts++ < MAX_TRANSIENT_SERVICE_RETRIES - 1
                        && antRadioServiceBindRequested && antRadioServiceConnected) {
                    removeServiceRetry();
                    serviceRetryRunnable = new Runnable() {
                        @Override public void run() { queryAdapters(generation); }
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
        if (!isCurrent(generation) || activeSnapshot == null) return;
        activeSnapshot = activeSnapshot.withService(state, adapters, issue);
        render(activeSnapshot);
        finishRefresh(generation);
    }

    private void finishRefresh(long generation) {
        if (!isCurrent(generation)) return;
        doUnbindAntRadioService();
        refreshActive = false;
        if (refreshAgain && lifecycleActive) {
            refreshAgain = false;
            requestRefresh("coalesced");
        }
    }

    private boolean isCurrent(long generation) {
        return lifecycleActive && refreshActive && activeGeneration == generation;
    }

    private void cancelProbeWork() {
        if (probeFuture != null) probeFuture.cancel(true);
        probeFuture = null;
        refreshActive = false;
        refreshAgain = false;
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
        antRadioService = null;
        antRadioServiceConnected = false;
        if (antRadioServiceBindRequested) {
            try {
                unbindService(antRadioServiceConnection);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "ANT service unbind state mismatch");
            } finally {
                antRadioServiceBindRequested = false;
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
        int color = result.readiness == AntReadiness.READY_BUILT_IN
                || result.readiness == AntReadiness.READY_EXTERNAL_USB ? statusSuccessColor
                : result.readiness == AntReadiness.CHECKING
                || result.readiness == AntReadiness.SETUP_REQUIRED_SERVICE
                || result.readiness == AntReadiness.SETUP_REQUIRED_ADAPTER ? statusWarningColor : statusErrorColor;
        readinessTv.setTextColor(color);
        antCapableIv.setImageResource(result.readiness == AntReadiness.READY_BUILT_IN
                || result.readiness == AntReadiness.READY_EXTERNAL_USB
                ? R.drawable.ic_ok_green_36dp : R.drawable.ic_nok_red_36dp);
        antCapableIv.setColorFilter(color, PorterDuff.Mode.SRC_IN);

        setBooleanStatus(builtinAntDetectedTv, snapshot.probeStatus == AntSupportSnapshot.ProbeStatus.CHECKING
                ? null : snapshot.builtInFeatureDeclared);
        setBooleanStatus(usbHostSupportTv, snapshot.probeStatus == AntSupportSnapshot.ProbeStatus.CHECKING
                ? null : snapshot.usbHostSupported);
        addonAdapterSupportRow.setVisibility(snapshot.usbHostSupported ? View.VISIBLE : View.GONE);
        addonAdapterSupportDivider.setVisibility(snapshot.usbHostSupported ? View.VISIBLE : View.GONE);
        setBooleanStatus(addonAdapterSupportTv, snapshot.probeStatus == AntSupportSnapshot.ProbeStatus.CHECKING
                ? null : snapshot.antAddOnSupported);
        setPackageStatus(antRadioServiceTv, antRadioServiceIv, snapshot.radioService);
        setPackageStatus(antUsbServiceTv, antUsbServiceIv, snapshot.usbService);
        setPackageStatus(antPluginsTv, antPluginsIv, snapshot.pluginsService);
        if (snapshot.halService != null && snapshot.halService.isInstalledEnabled()) {
            antHalServiceTv.setText(snapshot.halService.versionName);
            antHalServiceTv.setTextColor(statusSuccessColor);
        } else {
            antHalServiceTv.setText(snapshot.probeStatus == AntSupportSnapshot.ProbeStatus.CHECKING
                    ? R.string.checking : R.string.not_found);
            antHalServiceTv.setTextColor(statusErrorColor);
        }
        antUsbServiceRow.setVisibility(snapshot.usbHostSupported ? View.VISIBLE : View.GONE);
        antUsbServiceDivider.setVisibility(snapshot.usbHostSupported ? View.VISIBLE : View.GONE);
        renderAdapters(snapshot);
        renderUsbDevices(snapshot.usbDevices);
        boolean permissionOkay = snapshot.antPermission.isGranted();
        boolean permissionRelevant = snapshot.probeStatus != AntSupportSnapshot.ProbeStatus.CHECKING
                && snapshot.radioService != null && snapshot.radioService.isInstalledEnabled();
        int permissionVisibility = permissionRelevant && !permissionOkay ? View.VISIBLE : View.INVISIBLE;
        antRadioServiceLockIv.setVisibility(permissionVisibility);
        antRadioServiceLock2Iv.setVisibility(permissionVisibility);
    }

    private void renderAdapters(AntSupportSnapshot snapshot) {
        if (snapshot.probeStatus == AntSupportSnapshot.ProbeStatus.CHECKING) {
            builtinFirmwareTv.setText(R.string.checking);
            externalAdaptersTv.setVisibility(View.GONE);
            builtinAntDetectedIv.setVisibility(View.INVISIBLE);
            return;
        }
        ArrayList<AntAdapterSnapshot> builtin = new ArrayList<AntAdapterSnapshot>();
        ArrayList<AntAdapterSnapshot> external = new ArrayList<AntAdapterSnapshot>();
        for (AntAdapterSnapshot adapter : snapshot.adapters) {
            (adapter.builtIn ? builtin : external).add(adapter);
        }
        if (!builtin.isEmpty()) {
            builtinFirmwareTv.setText(joinFirmware(builtin));
            builtinFirmwareTv.setTextColor(statusSuccessColor);
            builtinAntDetectedIv.setVisibility(View.INVISIBLE);
        } else if (snapshot.builtInFeatureDeclared) {
            builtinFirmwareTv.setText(serviceMessage(snapshot.serviceState, R.string.requires_ars));
            builtinFirmwareTv.setTextColor(statusWarningColor);
            builtinAntDetectedIv.setVisibility(View.VISIBLE);
        } else {
            builtinFirmwareTv.setText(R.string.not_available);
            builtinFirmwareTv.setTextColor(statusErrorColor);
            builtinAntDetectedIv.setVisibility(View.INVISIBLE);
        }
        if (external.isEmpty()) {
            externalAdaptersTv.setVisibility(View.GONE);
        } else {
            StringBuilder value = new StringBuilder();
            for (AntAdapterSnapshot adapter : external) {
                if (value.length() > 0) value.append('\n');
                value.append(adapter.displayName).append(": ").append(adapter.firmwareVersion);
            }
            externalAdaptersTv.setText(value.toString());
            externalAdaptersTv.setTextColor(statusSuccessColor);
            externalAdaptersTv.setVisibility(View.VISIBLE);
        }
    }

    private String joinFirmware(List<AntAdapterSnapshot> adapters) {
        StringBuilder value = new StringBuilder();
        for (AntAdapterSnapshot adapter : adapters) {
            if (value.length() > 0) value.append(", ");
            value.append(adapter.firmwareVersion);
        }
        return value.toString();
    }

    private int serviceMessage(AntSupportSnapshot.ServiceState state, int fallback) {
        return state == AntSupportSnapshot.ServiceState.SERVICE_MISSING
                || state == AntSupportSnapshot.ServiceState.SERVICE_DISABLED
                || state == AntSupportSnapshot.ServiceState.SERVICE_TOO_OLD ? R.string.requires_ars : fallback;
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
        if (state == null) {
            text.setText(R.string.checking);
            text.setTextColor(statusWarningColor);
            return;
        }
        if (state.isInstalledEnabled()) {
            text.setText(state.versionName);
            text.setTextColor(statusSuccessColor);
            setActionIcon(icon, R.drawable.ic_info_outline_white_24dp);
            icon.setTag(YES_TAG);
        } else {
            text.setText(state.kind == PackageState.Kind.INSTALLED_DISABLED ? R.string.disabled : R.string.not_available);
            text.setTextColor(state.kind == PackageState.Kind.INSTALLED_DISABLED ? statusWarningColor : statusErrorColor);
            setActionIcon(icon, R.drawable.ic_file_download_black_24dp);
            icon.setTag(NO_TAG);
        }
    }

    private int readinessText(AntReadiness readiness) {
        switch (readiness) {
            case READY_BUILT_IN: return R.string.ready_built_in;
            case READY_EXTERNAL_USB: return R.string.ready_external_usb;
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
        probeExecutor.shutdownNow();
        super.onDestroy();
    }

    public void statusIconClick(View view) {
        if (view.getId() == R.id.builtin_ant_detected_iv) {
            Toast.makeText(this, R.string.requires_ars, Toast.LENGTH_SHORT).show();
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
            Object tag = view.getTag();
            if (YES_TAG.equals(tag)) showInstalledAppDetails(MainActivity.this, packageName);
            else openPackageListing(packageName);
        }
    }

    private void openPackageListing(String packageName) {
        Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName));
        try {
            if (getPackageManager().resolveActivity(market, 0) != null) { startActivity(market); return; }
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName));
            if (getPackageManager().resolveActivity(web, 0) != null) { startActivity(web); return; }
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
            if (context.getPackageManager().resolveActivity(intent, 0) != null) context.startActivity(intent);
            else Toast.makeText(context, R.string.no_app_for_action, Toast.LENGTH_SHORT).show();
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
