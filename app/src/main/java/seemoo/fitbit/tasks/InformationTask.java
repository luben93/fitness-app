package seemoo.fitbit.tasks;


import seemoo.fitbit.HeartRateTransmitter.IWearableController;

/**
 * Collects basic information from the device.
 */
class InformationTask extends Task {

    private Tasks tasks;
    private IWearableController mainFragment;

    /**
     * Creates an instance of information task.
     *
     * @param tasks    The instance of tasks.
     * @param mainFragment The current mainFragment.
     */
    InformationTask(Tasks tasks, IWearableController mainFragment) {
        this.tasks = tasks;
        this.mainFragment = mainFragment;
    }

    /**
     * {@inheritDoc}
     * Collects basic information from the device.
     */
    @Override
    public void execute() {
        mainFragment.getActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mainFragment.collectBasicInformation();
            }
        });
        tasks.taskFinished();
    }
}
