package stroeher.sven.bluetooth_le_scanner.interactions;

import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import stroeher.sven.bluetooth_le_scanner.activities.WorkActivity;
import stroeher.sven.bluetooth_le_scanner.miscellaneous.ButtonHandler;

/**
 * The waiting queue for interactions. When the execution of one interaction is finished, the next on in line gets executed.
 */
class BluetoothInteractionQueue {

    private final String TAG = this.getClass().getSimpleName();

    private ButtonHandler buttonHandler;
    private Interactions interactions;
    private Semaphore mInteractionLock;
    private ExecutorService mExecutorService;
    private WorkActivity activity;
    private Toast toast;
    //FIFO list of interactions which gets automatically executed.
    private ArrayList<BluetoothInteraction> bluetoothInteractions;

    /**
     * Creates an interaction queue.
     *
     * @param buttonHandler The instance of the button handler.
     * @param interactions  The instance of interactions.
     * @param activity      The current activity.
     * @param toast         The toast, to send messages to the user.
     */
    BluetoothInteractionQueue(ButtonHandler buttonHandler, Interactions interactions, WorkActivity activity, Toast toast) {
        this.buttonHandler = buttonHandler;
        this.interactions = interactions;
        this.activity = activity;
        this.toast = toast;
        mInteractionLock = new Semaphore(1, true);
        mExecutorService = Executors.newSingleThreadExecutor();
        bluetoothInteractions = new ArrayList<>();
    }


    /**
     * Adds a new interaction to interaction list.
     *
     * @param interaction The interaction to add.
     */
    void addInteraction(BluetoothInteraction interaction) {
        buttonHandler.setAllGone();
        bluetoothInteractions.add(interaction);
        BluetoothInteractionRunnable runnable = new BluetoothInteractionRunnable(interaction, mInteractionLock, interactions, this, activity, toast);
        mExecutorService.execute(runnable);
    }

    /**
     * Deletes the first command from interaction list and releases execution lock.
     */
    void interactionFinished() {
        if (bluetoothInteractions.size() > 0) {
            if (bluetoothInteractions.size() == 1) {
                buttonHandler.setAllVisible();
            }
            bluetoothInteractions.remove(0);
            mInteractionLock.release();
        } else {
            Log.e(TAG, "Error: There is no interaction to remove!");
        }
    }

    /**
     * Returns the first interaction in the list.
     *
     * @return The first interaction in the list. Null. if the list is empty.
     */
    BluetoothInteraction getFirstBluetoothInteraction() {
        if (bluetoothInteractions.isEmpty()) {
            return null;
        }
        return bluetoothInteractions.get(0);
    }
}
