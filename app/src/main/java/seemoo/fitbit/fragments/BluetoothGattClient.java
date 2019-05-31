package seemoo.fitbit.fragments;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashMap;

import seemoo.fitbit.R;
import seemoo.fitbit.commands.Commands;
import seemoo.fitbit.events.TransferProgressEvent;
import seemoo.fitbit.fragments.MainFragment;
import seemoo.fitbit.information.Alarm;
import seemoo.fitbit.information.Information;
import seemoo.fitbit.information.InformationList;
import seemoo.fitbit.interactions.Interactions;
import seemoo.fitbit.miscellaneous.ConstantValues;
import seemoo.fitbit.miscellaneous.ExternalStorage;
import seemoo.fitbit.miscellaneous.FitbitDevice;
import seemoo.fitbit.miscellaneous.InternalStorage;
import seemoo.fitbit.miscellaneous.Utilities;
import seemoo.fitbit.tasks.Tasks;

import static android.content.Context.MODE_PRIVATE;

public class BluetoothGattClient extends Service {

    private final String TAG = this.getClass().getSimpleName();

    private BluetoothDevice device;
    private ArrayList<BluetoothGattService> services = new ArrayList<>();

    private Commands commands;
    private Interactions interactions;
    private Tasks tasks;
//    private InformationList informationToDisplay = new InformationList("");
//    private ListView mListView;
//    private FloatingActionButton clearAlarmsButton;
//    private FloatingActionButton saveButton;

    private Object interactionData;
    private Toast toast_short;
    private Toast toast_long;
//    private int alarmIndex = -1;
    private String currentInformationList;
//    private int customLength = -1;
//    private String fileName;
//    private boolean firstPress = true;
//    private AlertDialog connectionLostDialog = null;

    public enum BluetoothConnectionState {DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, UNKNOWN}

    private MainFragment.BluetoothConnectionState bluetoothConnectionState = MainFragment.BluetoothConnectionState.UNKNOWN;

    private HashMap<String, InformationList> information = new HashMap<>();

    //    private GraphView graph;
    private BarGraphSeries<DataPoint> graphDataSeries;
    private int graphCounter = 0;

    private gattView getActivity() {
        return null;
    }

    //}

    @Override
    public void onCreate() {

    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return null;
    }

    public interface gattView {
        void showConnectionLostDialog();
        void destroyConnectionLostDialog();
        void onCharacteristicRead(Interactions interactions);
    }


        private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {

        /**
         * {@inheritDoc}
         * Logs aconnection state change and tries to reconnect, if connection is lost.
         */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                bluetoothConnectionState = MainFragment.BluetoothConnectionState.DISCONNECTED;
                services.clear();
                commands.close();
                getActivity().showConnectionLostDialog();

                Log.e(TAG, "Connection lost. Trying to reconnect.");
            } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                bluetoothConnectionState = MainFragment.BluetoothConnectionState.CONNECTING;
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothConnectionState = MainFragment.BluetoothConnectionState.CONNECTED;
                getActivity().destroyConnectionLostDialog();

                TransferProgressEvent event = new TransferProgressEvent(TransferProgressEvent.EVENT_TYPE_FW);
                event.setTransferState(TransferProgressEvent.STATE_REBOOT_FIN);
                EventBus.getDefault().post(event);

                commands.comDiscoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                bluetoothConnectionState = MainFragment.BluetoothConnectionState.DISCONNECTING;
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
            getActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    toast_short.setText(R.string.connection_established);
                    toast_short.show();
                }
            });
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
            if (interactions.liveModeActive()) {
                interactions.setAccelReadoutActive(Utilities.checkLiveModeReadout(characteristic.getValue()));
                information.put(interactions.getCurrentInteraction(), Utilities.translate(characteristic.getValue()));
                graphDataSeries = Utilities.updateGraph(characteristic.getValue());
//                getActivity().runOnUiThread(new Runnable() {
//
//                    @Override
//                    public void run() {
//                        if (interactions.accelReadoutActive() && interactions.liveModeActive()) {
//                            graph.setVisibility(View.VISIBLE);
//                            graph.removeAllSeries();
//                            graph.addSeries(graphDataSeries);
//                        } else {
//                            graph.setVisibility(View.GONE);
//                        }
//                        informationToDisplay.override(information.get(interactions.getCurrentInteraction()), mListView);
//                        saveButton.setVisibility(View.VISIBLE);
//                        currentInformationList = "LiveMode";
//                    }
//                });
                getActivity().onCharacteristicRead(interactions);
            }
            commands.commandFinished();
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

            if (Utilities.byteArrayToHexString(characteristic.getValue()) == "c01301000") {
                //Command
                Log.e(TAG, "Error: " + Utilities.getError(Utilities.byteArrayToHexString(characteristic.getValue())));
            }

            if (Utilities.byteArrayToHexString(characteristic.getValue()).length() >= 4 && Utilities.byteArrayToHexString(characteristic.getValue()).substring(0, 4).equals(ConstantValues.NEG_ACKNOWLEDGEMENT)) {
                Log.e(TAG, "Error: " + Utilities.getError(Utilities.byteArrayToHexString(characteristic.getValue())));
                services.clear();
                commands.close();
                getActivity().showConnectionLostDialog();

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

                    String keyAdditionalRawOutput = getResources().getString(R.string.settings_workactivity_1);
                    String keyAdditionalAlarmInformation = getResources().getString(R.string.settings_workactivity_2);
                    String keySaveDumpFiles = getResources().getString(R.string.settings_workactivity_3);
                    final SharedPreferences spAdditionalRawOutput = getActivity().getSharedPreferences(keyAdditionalRawOutput, MODE_PRIVATE);
                    final SharedPreferences spAdditionalAlarmInformation = getActivity().getSharedPreferences(keyAdditionalAlarmInformation, MODE_PRIVATE);
                    final SharedPreferences spSaveDumpFiles = getActivity().getSharedPreferences(keySaveDumpFiles, MODE_PRIVATE);
                    final Boolean additionalRawOutputBoolean = spAdditionalRawOutput.getBoolean(keyAdditionalRawOutput, false);
                    final Boolean additionalAlarmInformationBoolean = spAdditionalAlarmInformation.getBoolean(keyAdditionalAlarmInformation, false);
                    final Boolean saveDumpFilesBoolean = spSaveDumpFiles.getBoolean(keySaveDumpFiles, false);

                    currentInformationList = ((InformationList) interactionData).getName();
                    information.put(currentInformationList, (InformationList) interactionData);
                    graphDataSeries = Utilities.updateGraph(characteristic.getValue());
                    getActivity().runOnUiThread(informationListRunnable(currentInformationList, information, interactionData,
                            additionalRawOutputBoolean, additionalAlarmInformationBoolean,
                            saveDumpFilesBoolean, informationToDisplay, mListView, saveButton,
                            clearAlarmsButton, characteristic.getValue()));
                }
            }
        }

        private Runnable informationListRunnable(final String currentInformationListRun, final HashMap<String, InformationList> informationRun,
                                                 final Object interactionDataRun, final Boolean additionalRawOutputBooleanRun, final Boolean additionalAlarmInformationBooleanRun,
                                                 final Boolean saveDumpFilesBooleanRun, final InformationList informationToDisplayRun,
                                                 final ListView mListViewRun, final FloatingActionButton saveButtonRun,
                                                 final FloatingActionButton clearAlarmsButtonRun, final byte[] characteristicValue) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    if (Utilities.checkLiveModeReadout(characteristicValue) == false) {
                        graph.setVisibility(View.GONE);
                    }
                    if ((graphCounter % 30) == 0) {
                        graph.removeAllSeries();
                        graph.addSeries(graphDataSeries);
                    }
                    graphCounter++;
                    InformationList temp = new InformationList("");
                    temp.addAll(informationRun.get(((InformationList) interactionDataRun).getName()));
                    if (saveDumpFilesBooleanRun) {
                        ExternalStorage.saveInformationList(informationRun.get(currentInformationListRun), currentInformationListRun, getActivity());
                    }
                    if (currentInformationListRun.equals("Memory_KEY")) {
                        FitbitDevice.setEncryptionKey(informationRun.get(currentInformationListRun).getBeautyData().trim());
                        Log.e(TAG, "Encryption Key: " + FitbitDevice.ENCRYPTION_KEY);
                        InternalStorage.saveString(FitbitDevice.ENCRYPTION_KEY, ConstantValues.FILE_ENC_KEY, getActivity());
                    }
                    final int positionRawOutput = temp.getPosition(new Information(ConstantValues.RAW_OUTPUT));
                    if (!additionalRawOutputBooleanRun && positionRawOutput > 0) {
                        temp.remove(positionRawOutput - 1, temp.size());
                    }
                    final int positionAdditionalInfo = temp.getPosition(new Information(ConstantValues.ADDITIONAL_INFO));
                    if (!additionalAlarmInformationBooleanRun && positionAdditionalInfo > 0) {
                        temp.remove(positionAdditionalInfo - 1, positionRawOutput - 1);
                    }
                    informationToDisplayRun.override(temp, mListViewRun);
                    if (mListViewRun.getVisibility() == View.VISIBLE) {
                        saveButtonRun.setVisibility(View.VISIBLE);
                    }
                    if (informationToDisplayRun.size() > 1 && informationToDisplayRun.get(1) instanceof Alarm) {
                        clearAlarmsButtonRun.setVisibility(View.VISIBLE);
                    }
                }
            };

            return runnable;
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
}