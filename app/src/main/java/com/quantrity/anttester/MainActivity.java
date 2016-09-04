package com.quantrity.anttester;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Iterator;

import com.dsi.ant.AntService;
import com.dsi.ant.AntSupportChecker;
import com.dsi.ant.IAnt_6;


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
    TextView ant_plugins_tv;
    ImageView addon_adapter_support_iv;
    ImageView builtin_firmware_iv;
    ImageView ant_radio_service_iv;
    ImageView ant_usb_service_iv;
    ImageView ant_plugins_iv;
    TextView usb_devices_tv1;
    TextView usb_devices_tv2;

    /** Class for interacting with the ANT interface. */
    private ServiceConnection sIAntConnection;

    /** Inter-process communication with the ANT Radio Proxy Service. */
    private static IAnt_6 sAntReceiver = null;

    private myOnClickListener ant_usb_service_ocl = new myOnClickListener("com.dsi.ant.usbservice");
    private myOnClickListener ant_radio_service_ocl = new myOnClickListener("com.dsi.ant.service.socket");
    private myOnClickListener ant_plugins_service_ocl = new myOnClickListener("com.dsi.ant.plugins.antplus");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        ant_capable_iv = (ImageView) findViewById(R.id.ant_capable_iv);
        builtin_ant_detected_tv = (TextView) findViewById(R.id.builtin_ant_detected_tv);
        usb_host_support_tv = (TextView) findViewById(R.id.usb_host_support_tv);
        addon_adapter_support_label_tv = (TextView) findViewById(R.id.addon_adapter_support_label_tv);
        addon_adapter_support_tv = (TextView) findViewById(R.id.addon_adapter_support_tv);
        builtin_firmware_tv = (TextView) findViewById(R.id.builtin_firmware_tv);
        ant_hal_service_tv = (TextView) findViewById(R.id.ant_hal_service_tv);
        ant_radio_service_tv = (TextView) findViewById(R.id.ant_radio_service_tv);
        ant_usb_service_tv = (TextView) findViewById(R.id.ant_usb_service_tv);
        ant_plugins_tv = (TextView) findViewById(R.id.ant_plugins_tv);

        addon_adapter_support_iv = (ImageView) findViewById(R.id.addon_adapter_support_iv);
        builtin_firmware_iv = (ImageView) findViewById(R.id.builtin_firmware_iv);
        builtin_firmware_iv.setTag(NO_TAG);
        ant_radio_service_iv = (ImageView) findViewById(R.id.ant_radio_service_iv);
        ant_usb_service_iv = (ImageView) findViewById(R.id.ant_usb_service_iv);
        ant_plugins_iv = (ImageView) findViewById(R.id.ant_plugins_iv);

        addon_adapter_support_iv.setOnClickListener(ant_usb_service_ocl);
        builtin_firmware_iv.setOnClickListener(ant_radio_service_ocl);
        ant_radio_service_iv.setTag(NO_TAG);
        ant_radio_service_iv.setOnClickListener(ant_radio_service_ocl);
        ant_usb_service_iv.setTag(NO_TAG);
        ant_usb_service_iv.setOnClickListener(ant_usb_service_ocl);
        ant_plugins_iv.setTag(NO_TAG);
        ant_plugins_iv.setOnClickListener(ant_plugins_service_ocl);

        usb_devices_tv1 = (TextView) findViewById(R.id.usb_devices_tv1);
        usb_devices_tv2 = (TextView) findViewById(R.id.usb_devices_tv2);

        doBindAntRadioService();
    }

    private ServiceConnection mAntRadioServiceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            Log.v(TAG, "mAntRadioServiceConnection onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name)
        {
            Log.v(TAG, "mAntRadioServiceConnection onServiceConnected");
        }

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

//        Log.v(TAG, "A " + AntLibVersionInfo.ANTLIB_VERSION_STRING + " .... " + AntLibVersionInfo.ANTLIB_VERSION_CODE);
//        Log.v(TAG, "B " + AntSupportChecker.hasAntAddOn(this) + " ... " + AntSupportChecker.hasAntFeature(this));
//        Log.v(TAG, "C " + AntService.getVersionName(this) + " ... "
//                + AntService.hasAdapterProviderSupport() + " ... " +AntService.hasAdapterWideConfigurationSupport()
//                + " ... " + AntService.hasContinuousScanSupport() + " ... " + AntService.requiresBundle());

    }

    private void testANTSupport() {
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
            } catch (Exception ignored) { }
        }



        setTextView(usb_host_support_tv, R.string.yes, R.string.no, usb_host_support);

        boolean has_ant_addon_support = AntSupportChecker.hasAntAddOn(this);
        if (usb_host_support) {
            setTextView(addon_adapter_support_tv, R.string.yes, R.string.no, has_ant_addon_support);
            if (has_ant_addon_support) {
                addon_adapter_support_iv.setImageResource(R.mipmap.ic_action_about);
                addon_adapter_support_iv.setTag(YES_TAG);
            } else {
                addon_adapter_support_iv.setImageResource(R.mipmap.ic_action_download);
                addon_adapter_support_iv.setTag(NO_TAG);
            }
        } else {
            addon_adapter_support_tv.setVisibility(View.GONE);
            addon_adapter_support_label_tv.setVisibility(View.GONE);
            addon_adapter_support_iv.setVisibility(View.GONE);
        }

        ant_capable_iv.setImageResource((has_builtin_library || usb_host_support) ? R.mipmap.ic_ok : R.mipmap.ic_nok);

        try {
            String version = getPackageManager().getPackageInfo("com.dsi.ant.service.socket", PackageManager.GET_META_DATA).versionName;
            ant_radio_service_tv.setText(version);
            ant_radio_service_tv.setTextColor(GREEN);
            ant_radio_service_iv.setImageResource(R.mipmap.ic_action_about);
            ant_radio_service_iv.setTag(YES_TAG);

            //if (firmware_version == null) {
                sIAntConnection = new ServiceConnection() {
                    // Flag to know if the ANT App was interrupted
                    //private boolean antInterrupted = false;

                    public void onServiceConnected(ComponentName pClassName, IBinder pService) {
                        //Log.v(TAG, "sIAntConnection onServiceConnected()");
                        sAntReceiver = IAnt_6.Stub.asInterface(pService);
                        if (sAntReceiver == null) {
                            //Log.e(TAG, "Failed to get ANT Receiver");
                            return;
                        }

                        try {
                            if (!sAntReceiver.claimInterface()) {
                                //Log.e(TAG, "Failed to claim ANT interface");
                                return;
                            }

                            sAntReceiver.ANTResetSystem();

                            //Log.i(TAG, "resetting ANT OK");
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }

                        registerForAntIntents();
                    }

                    public void onServiceDisconnected(ComponentName pClassName) {
                        //Log.v(TAG, "sIAntConnection onServiceDisconnected()");
                        sAntReceiver = null;
                    }
                };

                bindService(new Intent("com.dsi.ant.IAnt_6"), sIAntConnection, Context.BIND_AUTO_CREATE);
                //Log.i(TAG, "initService(): Bound with ANT service: " + ret);
            //}

        } catch (Exception e) {
            ant_radio_service_tv.setText(R.string.not_available);
            ant_radio_service_tv.setTextColor(RED);
            ant_radio_service_iv.setImageResource(R.mipmap.ic_action_download);
            ant_radio_service_tv.setTag(NO_TAG);
            e.printStackTrace();
        }

        if (has_builtin_library) {
            builtin_firmware_tv.setText(R.string.requires_ars);
            builtin_firmware_tv.setTextColor(YELLOW);
            builtin_firmware_iv.setVisibility(View.VISIBLE);
        } else {
            builtin_firmware_tv.setText(R.string.no);
            builtin_firmware_tv.setTextColor(RED);
            builtin_firmware_iv.setVisibility(View.GONE);
        }

        //getPackageVersion(ant_radio_service_tv, "com.dsi.ant.service.socket");
        getPackageVersion(ant_usb_service_tv, "com.dsi.ant.usbservice", ant_usb_service_iv);
        getPackageVersion(ant_plugins_tv, "com.dsi.ant.plugins.antplus", ant_plugins_iv);

        String version;
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


        Boolean usb_devices = false;
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
                    sb.append("DeviceID:  " + device.getDeviceId() + "\n" +
                            "VendorID:  0x" + device.getVendorId() + "\n" +
                            "ProductID: 0x" + device.getProductId() + "\n");
                    if (Build.VERSION.SDK_INT >= 21) {
                        sb.append("\n" +
                                "ManufacturerName: " + device.getManufacturerName() + "\n" +
                                "ProductName: " + device.getProductName() + "\n" +
                                "SerialNumber: " + device.getSerialNumber() + "\n" +
                                "DeviceName: " + device.getDeviceName() + "\n");
                    }
                    if (Build.VERSION.SDK_INT >= 23) {
                        sb.append("\nVersion: " + device.getVersion() + "\n");
                    }
                    sb.append("\n");
                }
                if (sb.length() != 0) sb.setLength(sb.length() - 1);
                usb_devices_tv2.setText(sb);
            }
        }
        usb_devices_tv1.setVisibility(usb_devices ? View.VISIBLE: View.GONE);
        usb_devices_tv2.setVisibility(usb_devices ? View.VISIBLE: View.GONE);

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
            iv.setImageResource(R.mipmap.ic_action_about);
            iv.setTag(YES_TAG);
        } catch (Exception e) {
            tv.setText(R.string.not_available);
            tv.setTextColor(RED);
            iv.setImageResource(R.mipmap.ic_action_download);
            iv.setTag(NO_TAG);
        }
    }

    public void registerForAntIntents() {
        //Log.i(TAG, "Registering for ant intents.");
        // Register for ANT intent broadcasts.
        IntentFilter statusIntentFilter = new IntentFilter();
        statusIntentFilter.addAction("com.dsi.ant.intent.action.ANT_ENABLED");
        statusIntentFilter.addAction("com.dsi.ant.intent.action.ANT_DISABLED");
        statusIntentFilter.addAction("com.dsi.ant.intent.action.ANT_INTERFACE_CLAIMED_ACTION");
        statusIntentFilter.addAction("com.dsi.ant.intent.action.ANT_RESET");
        registerReceiver(statusReceiver, statusIntentFilter);

        IntentFilter dataIntentFilter = new IntentFilter();
        dataIntentFilter.addAction("com.dsi.ant.intent.action.ANT_RX_MESSAGE_ACTION");
        registerReceiver(dataReceiver, dataIntentFilter);
    }

    /** Receives and logs all status ANT intents. */
    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //String antAction = intent.getAction();
            //Log.i(TAG, "enter status onReceive" + antAction);

            byte msg[] = {(byte) 0x03, (byte) 0x4D, (byte) 0x00, (byte) 0x3E};
            try {
                sAntReceiver.ANTTxMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    /*private String messageToString(byte[] message) {
        StringBuilder out = new StringBuilder();
        for (byte b : message) {
            out.append(String.format("%s%02x", (out.length() == 0 ? "" : " "), b));
        }
        return out.toString();
    }*/

    /** Receives all data ANT intents. */
    private final BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String antAction = intent.getAction();

            if (antAction.equals("com.dsi.ant.intent.action.ANT_RX_MESSAGE_ACTION")) {
                byte[] antMessage = intent.getByteArrayExtra("com.dsi.ant.intent.ANT_MESSAGE");
                //Log.v(TAG, "dataReceiver onReceive " + messageToString(antMessage));

                if (antMessage[1] == 0x3e) {
                    byte[] version = new byte[antMessage[0] - 1];
                    System.arraycopy(antMessage, 2, version, 0, version.length);
                    builtin_firmware_tv.setText(new String(version));
                    builtin_firmware_tv.setTextColor(GREEN);
                    builtin_firmware_iv.setVisibility(View.GONE);

                    //finalizar all
                    unregisterReceiver(statusReceiver);
                    unregisterReceiver(dataReceiver);
                    unbindService(sIAntConnection);
                    sIAntConnection = null;
                    //Log.v(TAG, "releaseService() unbound.");
                }
                /*int len = antMessage[0];
                if (len != antMessage.length - 2 || antMessage.length <= 2) {
                    Log.e(TAG, "Invalid message: " + messageToString(antMessage));
                    return;
                }
                 Log.v(TAG, "dataReceiver onReceive " + messageToString(antMessage));*/
            }
        }
    };

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
                settingsDialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
                View v = getLayoutInflater().inflate(R.layout.about_dialog, null);
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
