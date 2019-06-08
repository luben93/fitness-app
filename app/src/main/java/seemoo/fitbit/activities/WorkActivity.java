package seemoo.fitbit.activities;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.io.Serializable;

import seemoo.fitbit.HeartRateTransmitter.GattService;
import seemoo.fitbit.HeartRateTransmitter.WearableController;
import seemoo.fitbit.R;
import seemoo.fitbit.fragments.DirectoryPickerFragment;
import seemoo.fitbit.dialogs.FirmwareFlashDialog;
import seemoo.fitbit.fragments.MainFragment;
import seemoo.fitbit.fragments.PrefFragment;
import seemoo.fitbit.fragments.TextInputFragment;
import seemoo.fitbit.fragments.WebViewFragment;
import seemoo.fitbit.https.HttpsClient;
import seemoo.fitbit.miscellaneous.ConstantValues;
import seemoo.fitbit.miscellaneous.ExternalStorage;
import seemoo.fitbit.miscellaneous.FitbitDevice;
import seemoo.fitbit.miscellaneous.InternalStorage;

import static android.widget.Toast.LENGTH_SHORT;

/**
 * The working menu.
 */
public class WorkActivity extends RequestPermissionsActivity implements Serializable{

    public static final String ARG_EXTRA_DEVICE = "device";
    public static final String ARG_SHOULD_BLINK = "shouldBlink";
    private final String TAG = this.getClass().getSimpleName();
    private HttpsClient client;

    private boolean backClosesAppToastShown = false;
    private boolean bluetoothDisconnectOnPause = true;
    private MainFragment mainFragment;
    private WebViewFragment webViewFragment;
    private DirectoryPickerFragment directoryPickerFragment;
    private FirmwareFlashDialog fwFlashDialog;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    /**
     * {@inheritDoc}
     * Initializes several objects and connects to the device.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_work);

//        mainFragment = new MainFragment();

//        getSupportFragmentManager().beginTransaction().add(R.id.work_activity_fragment_frame,
//                mainFragment).commit();
//        switchTooFragment(mainFragment);

        requestPermissionsLocation();

        Toolbar toolbar = (Toolbar) findViewById(R.id.work_activity_toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionbar = getSupportActionBar();
        actionbar.setDisplayHomeAsUpEnabled(true);
        actionbar.setHomeAsUpIndicator(R.drawable.ic_menu);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        mainFragment.checkFirstButtonPress();
//                        switchTooFragment(mainFragment);
                        drawerLayout.closeDrawers();
                        backClosesAppToastShown = false;



                        switch (menuItem.getItemId()) {
                            case R.id.nav_information:
                                mainFragment.buttonCollectBasicInformation();
                                break;
                            case R.id.nav_alarms:
                                mainFragment.buttonAlarms();
                                break;
                            case R.id.nav_online:
                                buttonOnline();
                                return false; // prevents checked look if submenu item is not yet picked
                            case R.id.nav_dump:
                                mainFragment.buttonDump();
                                return false;
                            case R.id.nav_set_date:
                                mainFragment.buttonSetDate();
                                break;
                            case R.id.nav_live_mode:
                                mainFragment.buttonLiveMode();
                                break;
                            case R.id.nav_devices:
                                mainFragment.buttonDevices();
                                break;
                            case R.id.nav_reconnect:
                                mainFragment.connect();
                                break;
                            case R.id.nav_preferences:
                                preferenceButton();
                                break;
                        }
                        if(menuItem.getItemId() != R.id.nav_live_mode){
                            leaveLiveModeIfActiveBefore();
                        }
                        return true;
                    }
                });
        navigationView.getMenu().getItem(0).setChecked(true);

        setFinishOnTouchOutside(true);

        ((TextView) navigationView.getHeaderView(0).findViewById(R.id.textView_device)).
                setText(((BluetoothDevice) getIntent().getExtras().get(ARG_EXTRA_DEVICE)).getName());
        ((TextView) navigationView.getHeaderView(0).findViewById(R.id.textView_connection_status)).setText(R.string.connected);

        webViewFragment = new WebViewFragment();

        client = new HttpsClient(Toast.makeText(this, "", Toast.LENGTH_SHORT), webViewFragment);
        startService(new Intent(this, GattService.class));
        Log.d(TAG, "onCreate: did start gatt sender loop");
        Bundle b = getIntent().getExtras();
        if(b != null){
            Intent intent = new Intent(this, WearableController.class);
            intent.putExtras(b);
            startService(intent);
            Log.d(TAG, "onCreate: did start fitbit listener  loop");

        }

    }

    public void preferenceButton(){
        Fragment prefFragment = new PrefFragment();

      switchTooFragment(prefFragment);
    }

    /**
     *
     */
    @Override
    public void onResume(){
        super.onResume();
//        mainFragment.showConnectionLostDialog();
    }

    /**
     * {@inheritDoc}
     * Closes bluetooth gatt.
     */
    @Override
    public void onBackPressed() {
        /*super.onBackPressed();
        if (commands != null) {
            commands.close();
        }*/
        if (navigationView.getMenu().getItem(0).isChecked()) {
            if (!backClosesAppToastShown) {
                backClosesAppToastShown = true;
                Toast.makeText(this, R.string.back_closes_app_message, LENGTH_SHORT).show();
            } else {
                this.finishAffinity();
            }
        } else {
            switchTooFragment(mainFragment);
            mainFragment.buttonCollectBasicInformation();
        }
    }

    public void showConnectionLostDialog(){
        mainFragment.showConnectionLostDialog();
    }

    /**
     * {@inheritDoc}
     * Lets the user choose the external directory and stores settings internally.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        drawerLayout.openDrawer(GravityCompat.START);
        return true;
        }

    /**
     * Helper-Method for PreferenceFragment
      */
    public void changeToDirectoryPickerFragment(){
        directoryPickerFragment = DirectoryPickerFragment.newInstance(
                ExternalStorage.getDirectory(this));
        switchTooFragment(directoryPickerFragment);
    }

    /**
     * Gets called, when 'online' button is pressed.
     * Shows a list to the user, which lets her/him choose between:
     * - Authentication via a web interface.
     * - a local authentication, if there already was an authentication for this device in the past.
     * - the upload of a microdump.
     * - the upload of a megadump.
     * - the upload of a firmware.
     */

    private void buttonOnline() {
        mainFragment.setAlarmAndSaveButtonGone();
        final String[] items = new String[]{"Flash firmware (Flex & Charge HR)", "Get reusable credentials from Fitbit server", "Local authentication", "Set encryption Key", "Manually set authentication credentials", "Let tracker blink", "Switch live mode accel"};//, "Clear Data on Tracker", "Boot to BSL", "Boot to APP"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose an option:");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                navigationView.getMenu().findItem(R.id.nav_online).setChecked(true);
                switch (which) {
                    case 0:
                        handleFirmwareFlashButton();
                        break;
                    case 1:
                        startFitbitAuthentication();
                        break;
                    case 2:
                        mainFragment.buttonLocalAuthenticate();
                        break;
                    case 3:
                        TextInputFragment textInputFragment =
                                TextInputFragment.newInstance(ConstantValues.ASK_ENC_KEY,
                                        "",
                                        new TextInputFragment.OnOkButtonClickInterface() {
                                            @Override
                                            public void onOkButtonClick(String enteredText) {

                                                FitbitDevice.setEncryptionKey(enteredText);
                                                InternalStorage.saveString(enteredText, ConstantValues.FILE_ENC_KEY, WorkActivity.this);

                                                switchTooFragment(mainFragment);
                                            }
                                        });
                        switchTooFragment(textInputFragment);
                        break;
                    case 4:
                        handleAuthCredentialsButton();
                        break;
                    case 5:
                        mainFragment.letDeviceBlink();
                        break;
                    case 6:
                        AlertDialog.Builder builder = new AlertDialog.Builder(WorkActivity.this);
                        builder.setMessage("This feature toggles the display of accelerometer data in LiveMode on Fitbit Flex flashed with a custom firmware");
                        builder.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                mainFragment.buttonSwitchLiveMode();
                                mainFragment.buttonCollectBasicInformation();
                            }
                        });
                        builder.setCancelable(true);
                        builder.show();
                        break;
                }
            }
        });
        builder.show();
    }

    private void leaveLiveModeIfActiveBefore() {
        if(mainFragment.isLiveModeActive()){
            mainFragment.endLiveMode();
        }

    }

    private void handleFirmwareFlashButton() {
        fwFlashDialog = new FirmwareFlashDialog(WorkActivity.this, mainFragment);
        fwFlashDialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FirmwareFlashDialog.PICK_FWFILE_REQUEST) {
            if (resultCode == RESULT_OK) {
                fwFlashDialog.passActivityResult(data);
            }
        }
    }

    private void handleAuthCredentialsButton() {
        TextInputFragment textInputFragment =
            TextInputFragment.newInstance(ConstantValues.ASK_AUTH_KEY,
                "",
                new TextInputFragment.OnOkButtonClickInterface() {
                    @Override
                    public void onOkButtonClick(String enteredText) {

                        FitbitDevice.setAuthenticationKey(enteredText);
                        InternalStorage.saveString(enteredText, ConstantValues.FILE_AUTH_KEY, WorkActivity.this);

                        askForAuthNonce();
                    }
                });
        switchTooFragment(textInputFragment);
    }

    private void askForAuthNonce() {
        TextInputFragment textInputFragment =
                TextInputFragment.newInstance(ConstantValues.ASK_AUTH_NONCE,
                        "",
                        new TextInputFragment.OnOkButtonClickInterface() {
                            @Override
                            public void onOkButtonClick(String enteredText) {
                                FitbitDevice.setNonce(enteredText);
                                InternalStorage.saveString(enteredText, ConstantValues.FILE_NONCE, WorkActivity.this);
                                //TODO calculate hex to int format:
                                // System.out.println("long: " + ((Long.parseLong("c17c9d26", 16))-Math.pow(2,32)) ); or
                                //System.out.println("long: " + ((Long.parseLong("269d7cc1", 16)) ));  (with hex reverse byte order, and then back to int etc...)
                                //should also be possible with more performant code (is this ones complement?)
                                switchTooFragment(mainFragment);
                            }
                        });
        switchTooFragment(textInputFragment);
    }


    /**
     * Starts the authentication via the web interface.
     */
    public void startFitbitAuthentication() {
        switchTooFragment(webViewFragment);
    }

    public void finishClickWebView(boolean successful) {
        if(successful){
            TextInputFragment textInputFragment =
                    TextInputFragment.newInstance(ConstantValues.ASK_AUTH_PIN,
                            "",
                            new TextInputFragment.OnOkButtonClickInterface() {
                                @Override
                                public void onOkButtonClick(String enteredText) {
                                    switchTooFragment(mainFragment);
                                    mainFragment.fitbitApiKeyEntered(enteredText);
                                }
                            });
            switchTooFragment(textInputFragment);
        }
        else {
            switchTooFragment(mainFragment);
        }

    }

    private void switchTooFragment(Fragment newFragment) {
        getSupportFragmentManager().beginTransaction().replace(
                R.id.work_activity_fragment_frame,
                newFragment).commitNow();
    }


    public HttpsClient getHttpsClient() {
        return client;
    }

    public void directorySelected(String path) {
        ExternalStorage.setDirectory(path, this);
        Log.e(TAG, "New external directory = " + path);
        switchTooFragment(mainFragment);
    }

    public static Intent getStartIntent(Context context, BluetoothDevice selectedDevice, boolean shouldBlink){
        Intent intent = new Intent(context, WorkActivity.class);
        intent.putExtra(ARG_EXTRA_DEVICE, selectedDevice);
        intent.putExtra(ARG_SHOULD_BLINK, shouldBlink);
        return intent;
    }

    public void setDumpMenuButtonActive() {
        navigationView.getMenu().findItem(R.id.nav_dump).setChecked(true);
        leaveLiveModeIfActiveBefore();
    }

    public void disableBluetoothDisconnectOnPause() {
        bluetoothDisconnectOnPause = false;
    }
    public void enableBluetoothDisconnectOnPause() {
        bluetoothDisconnectOnPause = false;
    }

    public boolean isBluetoothDisconnectOnPause() {
        return bluetoothDisconnectOnPause;
    }
}