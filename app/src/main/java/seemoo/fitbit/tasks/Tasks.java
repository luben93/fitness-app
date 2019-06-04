package seemoo.fitbit.tasks;


import android.bluetooth.BluetoothDevice;

import seemoo.fitbit.HeartRateTransmitter.IWearableController;
import seemoo.fitbit.activities.WorkActivity;
import seemoo.fitbit.https.HttpsClient;
import seemoo.fitbit.interactions.Interactions;
import seemoo.fitbit.miscellaneous.ConstantValues;

/**
 * Use this class to deal with tasks.
 */
public class Tasks {

    private final String TAG = this.getClass().getSimpleName();

    private Interactions interactions;
    private IWearableController mainFragment;
    private TaskQueue mTaskQueue;

    /**
     * Creates an instance of tasks.
     *
     * @param interactions  The instance of interactions
     * @param mainFragment      The current mainFragment.
     */
    public Tasks(Interactions interactions, IWearableController  mainFragment) {
        this.interactions = interactions;
        this.mainFragment = mainFragment;
        mTaskQueue = new TaskQueue();
    }

    /**
     * Finishes the current task.
     */
    public void taskFinished() {
        mTaskQueue.taskFinished();
    }

    /**
     * Returns the name of the current interaction task.
     *
     * @return The name of the current interaction task. Else null.
     */
    public String getCurrentInteractionsTaskName() {
        if (mTaskQueue.getFirstTask() != null && mTaskQueue.getFirstTask().TAG.equals("InteractionsTask")) {
            return ((InteractionsTask) mTaskQueue.getFirstTask()).getInteractionName() + "Interaction";
        } else {
            return null;
        }
    }

    /**
     * Clears the task queue.
     */
    public void clearList() {
        mTaskQueue.clear();
    }


    /**
     * <===============================================================================================================>
     * <====================================================> Tasks: <=================================================>
     * <===============================================================================================================>
     */

    //Always put an EmptyTask at last, to check if the last regular task in the task queue is working correctly.

    /**
     * Sets the tasks in the task queue, to authenticate and get a dump, if necessary, upload it to the server and send the response back to the device.
     *
     * @param client The instance to https client.
     * @param device The device to upload to.
     * @param type   The type of the dump.
     */
    public void taskUploadDump(HttpsClient client, BluetoothDevice device, String type) {
        if (!interactions.getAuthenticated()) {
            mTaskQueue.addTask(new InteractionsTask(interactions, "authentication", this));
        }
        if (mainFragment.getDataFromInformation(type) == null || mainFragment.wasInformationListAlreadyUploaded(type)) {
            mTaskQueue.addTask(new InteractionsTask(interactions, type, this) );
        }
        mTaskQueue.addTask(new UploadDumpTask(client, device, mainFragment, type, this));
        if (type != ConstantValues.INFORMATION_MICRODUMP) {
            //TODO this currently throws us back to Device Scan
            mTaskQueue.addTask(new InteractionsTask(interactions, type + "Upload", this, client));
        }
        mTaskQueue.addTask(new EmptyTask(this));
    }

    /**
     * Sets the tasks in the task queue, for the startup.
     * It loads the settings of the app from the internal storage and shows basic information about the device to the user.
     *
     * @param interactions The instance of interactions.
     * @param activity     The current activity.
     */
    public void taskStartup(Interactions interactions, WorkActivity activity) {
        mTaskQueue.addTask(new LoadSettingsTask(this, activity));
        mTaskQueue.addTask(new InformationTask(this, mainFragment));
        mTaskQueue.addTask(new EmptyTask(this));
    }
}
