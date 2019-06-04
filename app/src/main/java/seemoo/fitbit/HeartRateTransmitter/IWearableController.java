package seemoo.fitbit.HeartRateTransmitter;

import seemoo.fitbit.tasks.Tasks;

public  interface IWearableController {
        Tasks getTasks();
//        Activity getActivity();
        String getString(int id);
      void setAlarmIndex(int index);
      int getAlarmIndexAndIncrement();
      void setBluetoothConnectionState(BluetoothConnectionState state);
      String  getDataFromInformation(String type);
      void setInformationListAsAlreadyUploaded(String type);
      void collectBasicInformation();
      boolean wasInformationListAlreadyUploaded(String type);
     enum BluetoothConnectionState {DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, UNKNOWN}

    }
