package seemoo.fitbit.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.security.*;

import seemoo.fitbit.R;
import seemoo.fitbit.miscellaneous.ConstantValues;
import seemoo.fitbit.miscellaneous.InternalStorage;
import seemoo.fitbit.miscellaneous.Messenger;

/**
 * The main menu.
 */
public class MainActivity extends RequestPermissionsActivity {

    private final String TAG = this.getClass().getSimpleName();

    private static final int REQUEST_ENABLE_BLUETOOTH = 1;

    private BluetoothAdapter mBluetoothAdapter;
    private Activity activity;
    private Button scanButton;
    private TextView textView;
    private ListView lastDevices;
    ArrayList<String> lastDevicesString = new ArrayList<>();
    private FloatingActionButton clearLastDevicesButton;


    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Initializes objects and requests needed permissions.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initialize();
        requestPermissionsLocation();
        enableBluetooth();
        checkLastDeviceIsSet();
        startService(new Intent(this,MyService.class));
    }

    /**
     * {@inheritDoc}
     * Reinitializes several objects.
     */
    @Override
    protected void onRestart() {
        super.onRestart();
        initialize();
    }

    /**
     * Initializes several objects and shows the last device list, if is is not empty.
     */
    private void initialize() {
        activity = this;
        scanButton = (Button) findViewById(R.id.button_scanFitbit);
        scanButton.setVisibility(View.VISIBLE);
        clearLastDevicesButton = (FloatingActionButton) findViewById(R.id.clear_lastDevices);
        // Initializes Bluetooth adapter.
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        textView = (TextView) findViewById(R.id.textLastDevices);
        if (InternalStorage.loadLastDevices(activity) != null) {
            lastDevicesString.clear();
            lastDevicesString.addAll(InternalStorage.loadLastDevices(activity));
        }
        ArrayAdapter<String> mArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, lastDevicesString);
        lastDevices = (ListView) findViewById(R.id.lastDevices);
        lastDevices.setAdapter(mArrayAdapter);
        if (lastDevicesString == null || lastDevicesString.size() <= 0) {
            textView.setVisibility(View.GONE);
            lastDevices.setVisibility(View.GONE);
            clearLastDevicesButton.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            lastDevices.setVisibility(View.VISIBLE);
            clearLastDevicesButton.setVisibility(View.VISIBLE);
        }
        lastDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                autoScan(position);
            }
        });
    }

    /**
     * Called when the user taps the 'Scan for Fitbit devices' button. Scans for the Fitbit devices.
     *
     * @param view View object that was clicked by the user.
     */
    public void fitbitScan(View view) {
        if (enableBluetooth()) {
            Intent intent = new Intent(this, ScanActivity.class);
            intent.addFlags(ConstantValues.FLAG_SCAN);
            startActivity(intent);
        } else {
            Log.e(TAG, "Error: MainActivity.fitbitScan, Bluetooth not enabled");
        }
    }

    /**
     * Scans for the device with position 'position' in 'last devices list'.
     *
     * @param position The position of the device to scan.
     */
    public void autoScan(int position) {
        if (enableBluetooth()) {
            Intent intent = new Intent(this, ScanActivity.class);
            intent.addFlags(position);
            int index = lastDevicesString.get(position).lastIndexOf(": ");
            String macAddress = lastDevicesString.get(position).substring(index + 2);
            intent.putExtra("macAddress", macAddress);
            String name = lastDevicesString.get(position).substring(0, index);
            intent.putExtra("name", name);
            startActivity(intent);
        } else {
            Log.e(TAG, "Error: MainActivity.autoScan, Bluetooth not enabled");
        }
    }

    /**
     * Ensures Bluetooth (LE) is available on the device and it is enabled.
     * If not, displays a dialog requesting user permission to enable Bluetooth.
     *
     * @return False, if Bluetooth (LE) is not supported by the device, else true.
     */
    private boolean enableBluetooth() {
        //Ensures Bluetooth is available on the device.
        //If not, a message is shown to the user.
        if (mBluetoothAdapter == null) {
            Messenger.message(activity, android.R.drawable.ic_dialog_alert, R.string.error,
                    R.string.b_not_supported, R.string.cancel);
            Log.e(TAG, getString(R.string.b_not_supported));
            return false;
        }
        //Ensures Bluetooth LE is available on the device.
        //If not, a message is shown to the user.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Messenger.message(activity, android.R.drawable.ic_dialog_alert, R.string.error,
                    R.string.ble_not_supported, R.string.cancel);
            Log.e(TAG, getString(R.string.ble_not_supported));
            return false;
        }
        //Ensures Bluetooth is enabled on the device.
        //If not, displays a dialog requesting user permission to enable Bluetooth.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, REQUEST_ENABLE_BLUETOOTH);
        }
        return true;
    }

    /**
     * Called when the user tapps the 'clear last devices' button. Clears the last devices list.
     *
     * @param view View object that was clicked by the user.
     */
    public void clearLastDevices(View view) {
        InternalStorage.clearLastDevices(activity);
        Toast.makeText(activity, "Last devices cleared.", Toast.LENGTH_SHORT).show();
        Log.e(TAG, "Last devices cleared.");
        lastDevicesString.clear();
        textView.setVisibility(View.GONE);
        lastDevices.setVisibility(View.GONE);
        clearLastDevicesButton.setVisibility(View.GONE);
        lastDevices.invalidateViews();
    }

    public void decrypttest(View view) throws UnsupportedEncodingException{

        //Crypto.decrypttest_fw_update(activity);
    }


    private void checkLastDeviceIsSet(){
        String lastDevice = InternalStorage.loadLastDevice(activity);

        if(!"".equals(lastDevice) && lastDevice != null){
            scanForLastDevice();
        }
    }

    private void scanForLastDevice(){
        if (enableBluetooth()) {
            String currentDevice = InternalStorage.loadLastDevice(activity);

            Intent intent = new Intent(this, ScanActivity.class);
            intent.addFlags(9999);
            int index = currentDevice.lastIndexOf(": ");
            String macAddress = currentDevice.substring(index + 2);
            intent.putExtra("macAddress", macAddress);
            String name = currentDevice.substring(0, index);
            intent.putExtra("name", name);
            startActivity(intent);
        } else {
            Log.e(TAG, "Error: MainActivity.fitbitScan, Bluetooth not enabled");
        }
    }
}