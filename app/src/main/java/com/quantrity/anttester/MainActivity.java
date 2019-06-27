package com.quantrity.anttester;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Iterator;

import com.dsi.ant.AntService;
import com.dsi.ant.AntSupportChecker;
import com.dsi.ant.channel.AdapterInfo;


public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static int RED = Color.parseColor("#cc0000");
    private static int GREEN = Color.parseColor("#009900");
    private static int YELLOW = Color.parseColor("#999900");
    private static final String YES_TAG = "Y";
    private static final String NO_TAG = "N";

    ImageView ant_capable_iv;
    TextView builtin_ant_detected_tv;
    TextView usb_host_support_tv;
    TextView addon_adapter_support_label_tv;
    TextView addon_adapter_support_tv;
    TextView builtin_firmware_tv;
    TextView ant_hal_service_tv;
    TextView ant_radio_service_tv;
    TextView ant_usb_service_tv;
    TableRow ant_usb_service_tr;
    TextView ant_plugins_tv;
    ImageView builtin_ant_detected_iv;
    ImageView addon_adapter_support_iv;
    ImageView builtin_firmware_iv;
    ImageView ant_radio_service_iv;
    ImageView ant_radio_service_lock_iv, ant_radio_service_lock2_iv;
    ImageView ant_usb_service_iv;
    ImageView ant_plugins_iv;
    TextView usb_devices_tv1;
    TextView usb_devices_tv2;

    private myOnClickListener ant_usb_service_ocl = new myOnClickListener("com.dsi.ant.usbservice");
    private myOnClickListener ant_radio_service_ocl = new myOnClickListener("com.dsi.ant.service.socket");
    private myOnClickListener ant_plugins_service_ocl = new myOnClickListener("com.dsi.ant.plugins.antplus");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        ant_capable_iv = findViewById(R.id.ant_capable_iv);
        builtin_ant_detected_tv = findViewById(R.id.builtin_ant_detected_tv);
        usb_host_support_tv = findViewById(R.id.usb_host_support_tv);
        addon_adapter_support_label_tv = findViewById(R.id.addon_adapter_support_label_tv);
        addon_adapter_support_tv = findViewById(R.id.addon_adapter_support_tv);
        builtin_firmware_tv = findViewById(R.id.builtin_firmware_tv);
        ant_hal_service_tv = findViewById(R.id.ant_hal_service_tv);
        ant_radio_service_tv = findViewById(R.id.ant_radio_service_tv);
        ant_usb_service_tv = findViewById(R.id.ant_usb_service_tv);
        ant_usb_service_tr = findViewById(R.id.ant_usb_service_tr);
        ant_plugins_tv = findViewById(R.id.ant_plugins_tv);

        builtin_ant_detected_iv = findViewById(R.id.builtin_ant_detected_iv);
        addon_adapter_support_iv = findViewById(R.id.addon_adapter_support_iv);
        builtin_firmware_iv = findViewById(R.id.builtin_firmware_iv);
        builtin_firmware_iv.setTag(NO_TAG);
        ant_radio_service_iv = findViewById(R.id.ant_radio_service_iv);
        ant_radio_service_lock_iv = findViewById(R.id.ant_radio_service_lock_iv);
        ant_radio_service_lock2_iv = findViewById(R.id.ant_radio_service_lock2_iv);
        ant_usb_service_iv = findViewById(R.id.ant_usb_service_iv);
        ant_plugins_iv = findViewById(R.id.ant_plugins_iv);

        addon_adapter_support_iv.setOnClickListener(ant_usb_service_ocl);
        builtin_firmware_iv.setOnClickListener(ant_radio_service_ocl);
        ant_radio_service_iv.setTag(NO_TAG);
        ant_radio_service_iv.setOnClickListener(ant_radio_service_ocl);
        ant_usb_service_iv.setTag(NO_TAG);
        ant_usb_service_iv.setOnClickListener(ant_usb_service_ocl);
        ant_plugins_iv.setTag(NO_TAG);
        ant_plugins_iv.setOnClickListener(ant_plugins_service_ocl);

        usb_devices_tv1 = findViewById(R.id.usb_devices_tv1);
        usb_devices_tv2 = findViewById(R.id.usb_devices_tv2);

        testANTSupport();
    }

    private class MyRunnable implements Runnable
    {
        int countdown;
        AntService as;
        MyRunnable(int countdown, AntService as)
        {
            this.countdown = countdown;
            this.as = as;
        }

        @Override
        public void run() {
            if (countdown == 0) return;

            countdown--;
            try
            {
                Iterator i = as.getAdapterProvider().getAdaptersInfo(MainActivity.this).iterator();
                if (i.hasNext()) {
                    AdapterInfo ai = (AdapterInfo) i.next();
                    builtin_firmware_tv.setText(ai.getVersionString());
                    builtin_firmware_tv.setTextColor(GREEN);
                    builtin_firmware_iv.setVisibility(View.GONE);
                    builtin_ant_detected_iv.setVisibility(View.GONE);
                }
            }
            catch (Exception e)
            {
                Log.v(TAG, "MyRunnable run " + countdown);
                final Handler handler = new Handler();
                handler.postDelayed(new MyRunnable(countdown, as), 250);
            }
        }
    }

    private ServiceConnection mAntRadioServiceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            final AntService as = new AntService(service);
            final Handler handler = new Handler();
            int countdown = 20;
            handler.postDelayed(new MyRunnable(countdown, as), 250);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    private boolean mAntRadioServiceBound;
    private void doBindAntRadioService()
    {
        // Start listing for channel available intents
        //registerReceiver(mChannelProviderStateChangedReceiver, new IntentFilter(AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED));

//        Log.v(TAG, "doBindAntRadioService");
        mAntRadioServiceBound = AntService.bindService(this, mAntRadioServiceConnection);
    }

    private void doUnbindAntRadioService()
    {
        // Stop listing for channel available intents
        //try{
        //    unregisterReceiver(mChannelProviderStateChangedReceiver);
        //} catch (IllegalArgumentException exception) {
        //    if(BuildConfig.DEBUG) Log.d(TAG, "Attempting to unregister a never registered Channel Provider State Changed receiver.");
        //}

        if(mAntRadioServiceBound)
        {
            try
            {
                unbindService(mAntRadioServiceConnection);
            }
            catch(IllegalArgumentException e)
            {
                // Not bound, that's what we want anyway
            }

            mAntRadioServiceBound = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        testANTSupport();
    }

    private void testANTSupport() {
        doUnbindAntRadioService();
        boolean has_builtin_library = AntSupportChecker.hasAntFeature(this);
        setTextView(builtin_ant_detected_tv, R.string.yes, R.string.no, has_builtin_library);


        boolean usb_host_support = false;


        if (Build.VERSION.SDK_INT >= 12)
        {
            usb_host_support = this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
        }
        else
        {
            try {
                File f = new File("/system/etc/permissions/android.hardware.usb.host.xml");
                if (f.exists() && f.isFile()) {
                    BufferedReader rd = new BufferedReader(new FileReader(f));
                    String line;
                    while ((line = rd.readLine()) != null) {
                        if (line.contains("<feature name=\"android.hardware.usb.host\"")) {
                            usb_host_support = true;
                            break;
                        }
                    }
                    rd.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }



        setTextView(usb_host_support_tv, R.string.yes, R.string.no, usb_host_support);

        boolean has_ant_addon_support = AntSupportChecker.hasAntAddOn(this);
        if (usb_host_support) {
            setTextView(addon_adapter_support_tv, R.string.yes, R.string.no, has_ant_addon_support);
            if (has_ant_addon_support) {
                addon_adapter_support_iv.setImageResource(R.drawable.ic_info_outline_white_24dp);
                addon_adapter_support_iv.setColorFilter(Color.DKGRAY, PorterDuff.Mode.MULTIPLY);
                addon_adapter_support_iv.setTag(YES_TAG);
            } else {
                addon_adapter_support_iv.setImageResource(R.drawable.ic_file_download_black_24dp);
                addon_adapter_support_iv.setTag(NO_TAG);
            }
        } else {
            addon_adapter_support_tv.setVisibility(View.GONE);
            addon_adapter_support_label_tv.setVisibility(View.GONE);
            addon_adapter_support_iv.setVisibility(View.GONE);
        }

        ant_capable_iv.setImageResource((has_builtin_library || usb_host_support) ? R.drawable.ic_ok_green_36dp : R.drawable.ic_nok_red_36dp);

        boolean has_ARS = false;
        String version = null;
        try {
            version = getPackageManager().getPackageInfo("com.dsi.ant.service.socket", PackageManager.GET_META_DATA).versionName;
            ant_radio_service_tv.setText(version);
            ant_radio_service_tv.setTextColor(GREEN);
            ant_radio_service_iv.setImageResource(R.drawable.ic_info_outline_white_24dp);
            ant_radio_service_iv.setColorFilter(Color.DKGRAY, PorterDuff.Mode.MULTIPLY);
            ant_radio_service_iv.setTag(YES_TAG);

            has_ARS = true;

            builtin_firmware_iv.setTag(YES_TAG);
            builtin_firmware_iv.setImageResource(R.drawable.ic_settings_black_24dp);

            //Check ANT Radio Service permissions
            if (Build.VERSION.SDK_INT >= 16) {
                try {
                    PackageInfo pi = getApplicationContext().getPackageManager().getPackageInfo("com.dsi.ant.service.socket", PackageManager.GET_PERMISSIONS);
                    final String[] requestedPermissions = pi.requestedPermissions;
                    boolean enabled = false;
                    for (int i = 0, len = requestedPermissions.length; i < len; i++) {
                        if (requestedPermissions[i].startsWith("com.dsi.ant.permission.ANT") &&
                                ((pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0))
                        {
                            enabled = true;
                            break;
                        }
                    }
                    ant_radio_service_lock_iv.setVisibility(enabled ? View.GONE : View.VISIBLE);
                    ant_radio_service_lock2_iv.setVisibility(enabled ? View.GONE : View.VISIBLE);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            if (version == null) {
                ant_radio_service_tv.setText(R.string.not_available);
                ant_radio_service_tv.setTextColor(RED);
                ant_radio_service_iv.setImageResource(R.drawable.ic_file_download_black_24dp);
                ant_radio_service_tv.setTag(NO_TAG);
                ant_radio_service_lock_iv.setVisibility(View.GONE);
                ant_radio_service_lock2_iv.setVisibility(View.GONE);
            }
            e.printStackTrace();
        }

        if (has_builtin_library) {
            if (has_ARS)
            {
                builtin_firmware_tv.setText(R.string.no_service);
                builtin_firmware_tv.setTextColor(RED);
                builtin_ant_detected_iv.setVisibility(View.VISIBLE);
                builtin_firmware_iv.setTag(YES_TAG);
            }
            else {
                builtin_firmware_tv.setText(R.string.requires_ars);
                builtin_firmware_tv.setTextColor(YELLOW);
                builtin_ant_detected_iv.setVisibility(View.GONE);
                builtin_firmware_iv.setTag(NO_TAG);
            }
            builtin_firmware_iv.setVisibility(View.VISIBLE);
        } else {
            builtin_firmware_tv.setText(R.string.no);
            builtin_firmware_tv.setTextColor(RED);
            builtin_firmware_iv.setVisibility(View.GONE);
            builtin_ant_detected_iv.setVisibility(View.GONE);
        }

        if (!usb_host_support)
        {
            ant_usb_service_tr.setVisibility(View.GONE);
        }
        else
        {
            ant_usb_service_tr.setVisibility(View.VISIBLE);
            getPackageVersion(ant_usb_service_tv, "com.dsi.ant.usbservice", ant_usb_service_iv);
        }
        getPackageVersion(ant_plugins_tv, "com.dsi.ant.plugins.antplus", ant_plugins_iv);

        try {
            version = getPackageManager().getPackageInfo("com.dsi.ant.server", PackageManager.GET_META_DATA).versionName;
            ant_hal_service_tv.setText(version);
            ant_hal_service_tv.setTextColor(GREEN);
        } catch (Exception e1) {
            try {
                version = getPackageManager().getPackageInfo("com.sonyericsson.anthal.service", PackageManager.GET_META_DATA).versionName;
                ant_hal_service_tv.setText(version);
                ant_hal_service_tv.setTextColor(GREEN);
            } catch (Exception e2) {
                ant_hal_service_tv.setText(R.string.not_found);
                ant_hal_service_tv.setTextColor(RED);
            }
        }


        boolean usb_devices = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1)
        {
            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (manager != null) {
                HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
                Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

                StringBuilder sb = new StringBuilder();
                usb_devices = !deviceList.isEmpty();
                while (deviceIterator.hasNext()) {
                    UsbDevice device = deviceIterator.next();
                    sb.append("DeviceID:  ").append(device.getDeviceId()).append("\n").append("VendorID:  ").append(device.getVendorId()).append(" (0x").append(Integer.toHexString(device.getVendorId())).append(")\n").append("ProductID: ").append(device.getProductId()).append(" (0x").append(Integer.toHexString(device.getProductId())).append(")\n");
                    if (Build.VERSION.SDK_INT >= 21) {
                        sb.append("\n" + "ManufacturerName: ").append(device.getManufacturerName()).append("\n").append("ProductName: ").append(device.getProductName()).append("\n").append("SerialNumber: ").append(device.getSerialNumber()).append("\n").append("DeviceName: ").append(device.getDeviceName()).append("\n");
                    }
                    if (Build.VERSION.SDK_INT >= 23) {
                        sb.append("\nVersion: ").append(device.getVersion()).append("\n");
                    }
                    sb.append("\n");
                }
                if (sb.length() != 0) sb.setLength(sb.length() - 1);
                usb_devices_tv2.setText(sb);
            }
        }
        usb_devices_tv1.setVisibility(usb_devices ? View.VISIBLE: View.GONE);
        usb_devices_tv2.setVisibility(usb_devices ? View.VISIBLE: View.GONE);

        doBindAntRadioService();
    }

    public void setTextView(TextView tv, int text_true, int text_false, boolean bool) {
        tv.setText((bool) ? text_true : text_false);
        tv.setTextColor((bool) ? GREEN : RED);
    }

    void getPackageVersion(TextView tv, String name, ImageView iv) {
        try {
            String version = getPackageManager().getPackageInfo(name, PackageManager.GET_META_DATA).versionName;
            tv.setText(version);
            tv.setTextColor(GREEN);
            iv.setImageResource(R.drawable.ic_info_outline_white_24dp);
            iv.setColorFilter(Color.DKGRAY, PorterDuff.Mode.MULTIPLY);
            iv.setTag(YES_TAG);
        } catch (Exception e) {
            tv.setText(R.string.not_available);
            tv.setTextColor(RED);
            iv.setImageResource(R.drawable.ic_file_download_black_24dp);
            iv.setTag(NO_TAG);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onDestroy() {
        doUnbindAntRadioService();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_test: testANTSupport();
                return true;
            case R.id.action_about:
                Dialog settingsDialog = new Dialog(this);
                if (settingsDialog.getWindow() != null)
                {
                    settingsDialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
                }
                ViewGroup viewGroup = (ViewGroup) ((ViewGroup) (findViewById(android.R.id.content))).getChildAt(0);
                View v = getLayoutInflater().inflate(R.layout.about_dialog, viewGroup,false);
                settingsDialog.setContentView(v);
                settingsDialog.show();
                return true;
            default: return super.onOptionsItemSelected(item);
        }
    }

    public void aboutClick(View view) {
        switch (view.getId()) {
            case R.id.rate_button:
                Uri uri = Uri.parse("market://details?id=" + getPackageName());
                Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
                try {
                    startActivity(goToMarket);
                } catch (ActivityNotFoundException e) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + getPackageName())));
                }
                break;
            case R.id.translate_button:
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://localize.quantrity.com/projects/3"));
                    startActivity(browserIntent);
                } catch (ActivityNotFoundException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.builtin_ant_detected_iv:
                Toast.makeText(getApplicationContext(), R.string.no_service, Toast.LENGTH_SHORT).show();
                break;
            case R.id.ant_radio_service_lock_iv:
            case R.id.ant_radio_service_lock2_iv:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);

                builder.setIcon(R.drawable.ic_lock_red_24dp)
                        .setTitle(R.string.error_title)
                        .setMessage(R.string.use_ant_harware_permission)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                showInstalledAppDetails(MainActivity.this, "com.dsi.ant.service.socket");
                            }
                        }).create().show();
                break;
            default: Log.v(TAG, "Unknown " + view.toString());
        }
    }

    class myOnClickListener implements View.OnClickListener {
        private String pkg;

        myOnClickListener(String pkg) {
            this.pkg = pkg;
        }

        @Override
        public void onClick(View view) {
            ImageView iv = (ImageView)view;
            String tag = (String)iv.getTag();
            if ((tag != null) && (tag.equals(YES_TAG))) {
                showInstalledAppDetails(MainActivity.this, pkg);
            } else {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
                } catch (android.content.ActivityNotFoundException e) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + pkg)));
                }
            }
        }
    }

    private static final String SCHEME = "package";
    private static final String APP_PKG_NAME_21 = "com.android.settings.ApplicationPkgName";
    private static final String APP_PKG_NAME_22 = "pkg";
    private static final String APP_DETAILS_PACKAGE_NAME = "com.android.settings";
    private static final String APP_DETAILS_CLASS_NAME = "com.android.settings.InstalledAppDetails";
    public static void showInstalledAppDetails(Context context, String packageName) {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= 9) { // above 2.3
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts(SCHEME, packageName, null);
            intent.setData(uri);
        } else { // below 2.3
            final String appPkgName = (Build.VERSION.SDK_INT == 8 ? APP_PKG_NAME_22 : APP_PKG_NAME_21);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setClassName(APP_DETAILS_PACKAGE_NAME, APP_DETAILS_CLASS_NAME);
            intent.putExtra(appPkgName, packageName);
        }
        context.startActivity(intent);
    }
}
