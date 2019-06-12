package seemoo.fitbit.HeartRateTransmitter;

import android.app.Activity;
import android.app.Dialog;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashMap;

import seemoo.fitbit.R;
import seemoo.fitbit.activities.MainActivity;
import seemoo.fitbit.activities.WorkActivity;
import seemoo.fitbit.commands.Commands;
import seemoo.fitbit.events.TransferProgressEvent;
import seemoo.fitbit.information.Alarm;
import seemoo.fitbit.information.Information;
import seemoo.fitbit.information.InformationList;
import seemoo.fitbit.interactions.Interactions;
import seemoo.fitbit.miscellaneous.ConstantValues;
import seemoo.fitbit.miscellaneous.FitbitDevice;
import seemoo.fitbit.miscellaneous.InfoListItem;
import seemoo.fitbit.miscellaneous.InternalStorage;
import seemoo.fitbit.miscellaneous.Utilities;
import seemoo.fitbit.tasks.Tasks;

public class WearableController extends Service implements IWearableController {

    private final String TAG = this.getClass().getSimpleName();
    private final IBinder binder = new LocalBinder();
    private BluetoothManager mBluetoothManager;
    private long lastHRrecived=0;
    private GattServer server;
    private boolean running;

    private BluetoothGatt mBluetoothGatt;
    private BluetoothDevice device;
    private ArrayList<BluetoothGattService> services = new ArrayList<>();
    private Commands commands;
    private Interactions interactions;
    private Tasks tasks;

    private Object interactionData;
//    private Toast toast_short;
//    private Toast toast_long;
    private int alarmIndex = -1;
    private String currentInformationList;
    private BluetoothConnectionState bluetoothConnectionState = BluetoothConnectionState.UNKNOWN;
    private HashMap<String, InformationList> information = new HashMap<>();


    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {

        /**
         * {@inheritDoc}
         * Logs aconnection state change and tries to reconnect, if connection is lost.
         */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                bluetoothConnectionState = BluetoothConnectionState.DISCONNECTED;
                services.clear();
                commands.close();
//                showConnectionLostDialog();
                Log.e(TAG, "Connection lost. Trying to reconnect.");
            } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                bluetoothConnectionState = BluetoothConnectionState.CONNECTING;
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothConnectionState = BluetoothConnectionState.CONNECTED;
                destroyConnectionLostDialog();

                TransferProgressEvent event = new TransferProgressEvent(TransferProgressEvent.EVENT_TYPE_FW);
                event.setTransferState(TransferProgressEvent.STATE_REBOOT_FIN);
                EventBus.getDefault().post(event);

                commands.comDiscoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                bluetoothConnectionState = BluetoothConnectionState.DISCONNECTING;
            }
            Log.e(TAG, "onConnectionStateChange: " + bluetoothConnectionState);
        }


        /**
         * {@inheritDoc}
         * Logs a service discovery and finishes the corresponding command.
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.e(TAG, "onServicesDiscovered");
            services.addAll(gatt.getServices());
            commands.commandFinished();
        }

        /**
         * {@inheritDoc}
         * Logs a characteristic read and finishes the corresponding command.
         * If the device is in live mode, the data is stored in 'information' and shown to the user.
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.e(TAG, "onCharacteristicRead(): " + characteristic.getUuid() + ", " + Utilities.byteArrayToHexString(characteristic.getValue()));
                interactions.setAccelReadoutActive(Utilities.checkLiveModeReadout(characteristic.getValue()));
                information.put(interactions.getCurrentInteraction(), Utilities.translate(characteristic.getValue()));
                InformationList info = information.get(interactions.getCurrentInteraction());
                currentInformationList = "LiveMode";
                sendHRintent(info);
                commands.commandFinished();
        }

        private void sendHRintent(InformationList info){
            if (info.size() > 6) {
                String[] data = info.get(6).toString().split(" ");

                if (data.length == 2) {
                    Log.d(TAG, "run: sendHRintent livemode HR " + data[1]);
                    int heartRate = Integer.parseInt(data[1]);
//                    Intent intent = new Intent();
//                    intent.putExtra("heartRate", heartRate);
//                    intent.setAction("HRdata");
//                    getContext().sendBroadcast(intent);
                    server.setCurrentHeartrate(heartRate);
                    server.notifyRegisteredDevices();
                    Log.d(TAG, "run: broadcast intent sent:: " + " " + heartRate);
                }
            }
        }
        /**
         * {@inheritDoc}
         * Logs a characteristic write and finishes the corresponding command.
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.e(TAG, "onCharacteristicWrite(): " + characteristic.getUuid() + ", " + Utilities.byteArrayToHexString(characteristic.getValue()));
            commands.commandFinished();
        }

        /**
         * {@inheritDoc}
         * Logs a characteristic change and finishes the corresponding command.
         * If the new value is a negative acknowledgement it reconnects to the device to avoid subsequent errors.
         * If there is any relevant data in the new value it is stored in 'information' and shown to the user, if necessary.
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            Log.e(TAG, "onCharacteristicChanged(): " + characteristic.getUuid() + ", " + Utilities.byteArrayToHexString(characteristic.getValue()));

            if(characteristic.getUuid().toString().equals( "558dfa01-4fa8-4105-9f02-4eaa93e62980")){
                InformationList infoValue = Utilities.translate(characteristic.getValue());
                sendHRintent(infoValue);
                return;
            }
            if (Utilities.byteArrayToHexString(characteristic.getValue()) == "c01301000") {
                //Command
                Log.e(TAG, "Error: " + Utilities.getError(Utilities.byteArrayToHexString(characteristic.getValue())));
            }

            if (Utilities.byteArrayToHexString(characteristic.getValue()).length() >= 4 && Utilities.byteArrayToHexString(characteristic.getValue()).substring(0, 4).equals(ConstantValues.NEG_ACKNOWLEDGEMENT)) {
                Log.e(TAG, "Error: " + Utilities.getError(Utilities.byteArrayToHexString(characteristic.getValue())));
                services.clear();
                commands.close();
//                showConnectionLostDialog();
                Log.e(TAG, "Disconnected. Trying to reconnect...");
            } else {
                Log.e(TAG, "Getting interaction response.");
                byte[] value = characteristic.getValue();
                //Log.e(TAG, "Response value: " + Utilities.byteArrayToHexString(value));
                interactionData = interactions.interact(value);
                //Log.e(TAG, "Interaction called.");
                if (interactions.isFinished()) {
                    interactionData = interactions.interactionFinished();
                }
                if (interactionData != null) {
                    currentInformationList = ((InformationList) interactionData).getName();
                    information.put(currentInformationList, (InformationList) interactionData);
                }
            }
        }


        /**
         * {@inheritDoc}
         * Logs a descriptor read and finishes the corresponding command.
         */
        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.e(TAG, "onDescriptorRead(): " + descriptor.getCharacteristic().getUuid() + ", " + descriptor.getUuid() + ", " + Utilities.byteArrayToHexString(descriptor.getValue()));
            commands.commandFinished();
        }

        /**
         * {@inheritDoc}
         * Logs a descriptor write and finishes the corresponding command.
         */
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.e(TAG, "onDescriptorWrite(): " + descriptor.getCharacteristic().getUuid() + ", " + descriptor.getUuid() + ", " + Utilities.byteArrayToHexString(descriptor.getValue()));
            commands.commandFinished();
        }

    };

    public Service getActivity() {
        return this;
    }

    public Context getContext() {
        return this;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class LocalBinder extends Binder {
        WearableController getService() {
            // Return this instance of LocalService so clients can call public methods
            return WearableController.this;
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate: loop ");
//        toast_short = Toast.makeText(this, "", Toast.LENGTH_SHORT);
//        toast_long = Toast.makeText(this, "", Toast.LENGTH_LONG);
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        server = new GattServer(mBluetoothManager,this);
        registerReceiver(server.mBluetoothReceiver, filter);
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
        } else {
            Log.d(TAG, "Bluetooth enabled...starting services");

            server.startAdvertising();
            server.startServer();
        }
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        Log.d(TAG, "onStartCommand: loop" + intent);
        if (intent == null || intent.getExtras().get(WorkActivity.ARG_EXTRA_DEVICE) == null) {
            return START_NOT_STICKY;
        }
        device = (BluetoothDevice) intent.getExtras().get(WorkActivity.ARG_EXTRA_DEVICE);

//        collectBasicInformation();

        running = true;
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
        //todo need to close this service
        Thread t = new Thread() {
            @Override
            public void run() {
                super.run();
                connect();
                commands.comLiveModeFirstValues();

                while (running) {
                    try {
                        Log.d(TAG, "run: loop fetch");

                        Thread.sleep(10000);
//                        liveModeFavButton();
                        showConnectionLostDialog();
                        if(!interactions.getAuthenticated()){
                            interactions.intAuthentication();
                        }
                        //register notify from wearable
                        commands.comLiveModeEnable();
                        commands.comLiveModeFirstValues();
//                        Thread.sleep(15000);

                        //send to client
                        Log.d(TAG, "run: loop");
                        server.notifyRegisteredDevices();


                        if(System.currentTimeMillis() - lastHRrecived < 60000){
                            running = false;
                            Log.d(TAG, "run: restarting service too long between updates");
                            stopService(intent);
                            stopSelf();
                            startService(intent);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        running = false;
                        Log.d(TAG, "run: restarting service exception caught");
                        stopService(intent);
                        stopSelf();
                        startService(intent);
                    }
                }
            }
        };

        t.start();
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (bluetoothAdapter.isEnabled()) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            server.stopServer();
            server.stopAdvertising();
        }
        running = false;
        unregisterReceiver(server.mBluetoothReceiver);

    }

    public void liveModeFavButton() {
        Log.d(TAG, "liveModeFavButton: ");
        if (!interactions.liveModeActive()) {
            if (!interactions.getAuthenticated()) {
                interactions.intAuthentication();
            }
            interactions.intLiveModeEnable();

        }
    }

    /**
     *
     */
    public void showConnectionLostDialog() {
        if (getActivity() != null && bluetoothConnectionState != BluetoothConnectionState.CONNECTED &&
                bluetoothConnectionState != BluetoothConnectionState.CONNECTING) {
            Log.d(TAG, "showConnectionLostDialog: is doing connect");
            connect();
        }
    }

    /**
     * If there is a connectionLostDialog shown, dismiss it to show the user the tracker is connected again.
     */
    public void destroyConnectionLostDialog() {
        Log.i(TAG, "destroyConnectionLostDialog: ");
    }


    /**
     * Connects the app with the device.
     */
    public void connect() {
        FitbitDevice.setMacAddress(device.getAddress());
         mBluetoothGatt = device.connectGatt(getActivity().getBaseContext(), true, mBluetoothGattCallback);
        commands = new Commands(mBluetoothGatt);
        interactions = new Interactions(this, commands);
        tasks = new Tasks(interactions, this);
        interactions.intLiveModeEnable();
//        interactions.intEmptyInteraction();
    }

    /**
     * Collects basic information about the selected device, stores them in 'information' and displays them to the user.
     */
    public void collectBasicInformation() {
        InformationList list = new InformationList("basic");
        currentInformationList = "basic";
        list.add(new Information("MAC Address: " + device.getAddress()));
        list.add(new Information("Name: " + device.getName()));

        int type = device.getType();
        if (type == BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
            list.add(new Information(getString(R.string.device_type0)));
        } else if (type == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
            list.add(new Information(getString(R.string.device_type1)));
        } else if (type == BluetoothDevice.DEVICE_TYPE_LE) {
            list.add(new Information(getString(R.string.device_type2)));
        } else if (type == BluetoothDevice.DEVICE_TYPE_DUAL) {
            list.add(new Information(getString(R.string.device_type3)));
        }

        int bondState = device.getBondState();
        if (bondState == BluetoothDevice.BOND_NONE) {
            list.add(new Information(getString(R.string.bond_state0)));
        } else if (bondState == BluetoothDevice.BOND_BONDING) {
            list.add(new Information(getString(R.string.bond_state1)));
        } else if (bondState == BluetoothDevice.BOND_BONDED) {
            list.add(new Information(getString(R.string.bond_state2)));
        }


        InternalStorage.loadAuthFiles(getActivity());

        if (FitbitDevice.AUTHENTICATION_KEY == null || FitbitDevice.AUTHENTICATION_KEY.equals("")) {
            list.add(new Information(getString(R.string.no_auth_cred)));
        } else {
            list.add(new Information("Authentication Key & Nonce: " + FitbitDevice.AUTHENTICATION_KEY + ", " + FitbitDevice.NONCE));
        }

        if (FitbitDevice.ENCRYPTION_KEY == null || FitbitDevice.ENCRYPTION_KEY.equals("")) {
            list.add(new Information(getString(R.string.no_enc_key)));
        } else {
            list.add(new Information("Encryption Key: " + FitbitDevice.ENCRYPTION_KEY));
        }


        information.put("basic", list);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {

            }
        }, 300);
    }


    /**
     * Returns alarmIndex and increments it value by one afterwards
     *
     * @return The alarm index.
     */
    public int getAlarmIndexAndIncrement() {
        return alarmIndex++;
    }

    /**
     * Returns the value of the alarm index.
     *
     * @param value The index of the alarm.
     */
    public void setAlarmIndex(int value) {
        alarmIndex = value;
    }

    /**
     * Returns the content of an information list in 'information' as a string.
     *
     * @param name The name of the information list.
     * @return The content as a string.
     */
    public String getDataFromInformation(String name) {
        if (information.get(name) != null) {
            return information.get(name).getData();
        } else {
            return null;
        }
    }

    /**
     * Return the tasks object.
     *
     * @return The tasks object.
     */
    public Tasks getTasks() {
        return tasks;
    }

    /**
     * Checks, if an information list in 'information' was already uploaded to the fitbit server in the past.
     *
     * @param name The name of the information list to check.
     * @return True, if the information list was not uploaded in the past.
     */
    public boolean wasInformationListAlreadyUploaded(String name) {
        return information.get(name).getAlreadyUploaded();
    }

    /**
     * Sets an information list in 'information' as already uploaded.
     *
     * @param name The name of the information list.
     */
    public void setInformationListAsAlreadyUploaded(String name) {
        information.get(name).setAlreadyUploaded(true);
    }


    public void setBluetoothConnectionState(BluetoothConnectionState newState) {
        bluetoothConnectionState = newState;
    }
}
