package stroeher.sven.bluetooth_le_scanner.https;


import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

import com.fitbit.api.FitbitAPIException;
import com.fitbit.api.client.FitbitAPIEntityCache;
import com.fitbit.api.client.FitbitApiClientAgent;
import com.fitbit.api.client.FitbitApiCredentialsCache;
import com.fitbit.api.client.FitbitApiCredentialsCacheMapImpl;
import com.fitbit.api.client.FitbitApiEntityCacheMapImpl;
import com.fitbit.api.client.FitbitApiSubscriptionStorage;
import com.fitbit.api.client.FitbitApiSubscriptionStorageInMemoryImpl;
import com.fitbit.api.client.LocalUserDetail;
import com.fitbit.api.client.http.AccessToken;
import com.fitbit.api.client.http.TempCredentials;
import com.fitbit.api.client.service.FitbitAPIClientService;
import com.fitbit.api.common.model.user.UserInfo;
import com.fitbit.api.model.APIResourceCredentials;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import stroeher.sven.bluetooth_le_scanner.activities.WorkActivity;
import stroeher.sven.bluetooth_le_scanner.interactions.Interactions;
import stroeher.sven.bluetooth_le_scanner.miscellaneous.ConstantValues;
import stroeher.sven.bluetooth_le_scanner.miscellaneous.InternalStorage;

class OAuth {

    private final String TAG = this.getClass().getSimpleName();

    private Toast toast;
    private WorkActivity activity;

    private FitbitApiClientAgent apiClientAgent;
    private FitbitAPIClientService<FitbitApiClientAgent> apiClientService;
    private TempCredentials credentials;

    /**
     * Creates an instance of OAuth.
     * @param toast The toast to show messages to the user.
     * @param activity The current activity.
     */
    OAuth(Toast toast, WorkActivity activity) {
        this.toast = toast;
        this.activity = activity;
    }

    /**
     * Fetches the oAuth verifier value from the fitbit server.
     * In that process, the user has to login to the fitbit server and give read and write access to her/his/its account to this app.
     * @param webView The webView instance that shall be used.
     */
    void getVerifier(final WebView webView) {
        toast.setText("Connecting to Server...");
        toast.show();
        new Thread(new Runnable() {
            public void run() {
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        activity.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                activity.reverseWebView();
                            }
                        });
                    }
                }, 10000);
                try {

                    FitbitAPIEntityCache entityCache = new FitbitApiEntityCacheMapImpl();
                    FitbitApiCredentialsCache credentialsCache = new FitbitApiCredentialsCacheMapImpl();
                    FitbitApiSubscriptionStorage subscriptionStore = new FitbitApiSubscriptionStorageInMemoryImpl();
                    apiClientAgent = new FitbitApiClientAgent(ConstantValues.API_BASE_URL, ConstantValues.WEB_BASE_URL, credentialsCache);
                    apiClientService = new FitbitAPIClientService<>(apiClientAgent, ConstantValues.CONSUMER_KEY, ConstantValues.CONSUMER_SECRET, credentialsCache, entityCache, subscriptionStore);
                    credentials = apiClientAgent.getOAuthTempToken();
                    final String token = credentials.getToken();
                    activity.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            Log.e(TAG, ConstantValues.WEB_BASE_URL + ConstantValues.OAUTH_TOKEN_URL_PART + token);
                            webView.loadUrl(ConstantValues.WEB_BASE_URL + ConstantValues.OAUTH_TOKEN_URL_PART + token);
                        }
                    });
                    timer.cancel();
                } catch (FitbitAPIException e) {
                    Log.e(TAG, e.toString());
                    activity.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            activity.reverseWebView();
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Fetches the user name from the fitbit server and greets the user.
     * @param verifier The oAuth verifier value.
     * @param interactions The current interactions instance.
     */
    void getUserName(final String verifier, final Interactions interactions) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    AccessToken accessToken = apiClientAgent.getOAuthAccessToken(credentials, verifier);
                    AuthValues.setAccessTokenKey(accessToken.getToken());
                    AuthValues.setAccessTokenSecret(accessToken.getTokenSecret());
                    AuthValues.setVerifier(verifier);
                    InternalStorage.saveString(accessToken.getToken(), ConstantValues.FILE_ACCESS_TOKEN_KEY, activity);
                    InternalStorage.saveString(accessToken.getTokenSecret(), ConstantValues.FILE_ACCESS_TOKEN_SECRET, activity);
                    InternalStorage.saveString(verifier, ConstantValues.FILE_VERIFIER, activity);
                    APIResourceCredentials resourceCredentials = new APIResourceCredentials("1", accessToken.getToken(), accessToken.getTokenSecret());
                    resourceCredentials.setAccessToken(accessToken.getToken());
                    resourceCredentials.setAccessTokenSecret(accessToken.getTokenSecret());
                    resourceCredentials.setResourceId("1");

                    LocalUserDetail user = new LocalUserDetail("1");
                    apiClientService.saveResourceCredentials(user, resourceCredentials);
                    FitbitApiClientAgent agent = apiClientService.getClient();
                    final UserInfo userInfo = agent.getUserInfo(user);
                    activity.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            toast.setText("Hello " + userInfo.getFullName() + "!");
                            toast.show();
                        }
                    });
                    getCredentials(interactions);
                } catch (FitbitAPIException e) {
                    toast.setText("Error: Unable to get user name!");
                    toast.show();
                    Log.e(TAG, e.toString());
                }
            }
        }).start();
    }

    /**
     * Fetches the credentials (nonce, authentication key) from the fitbit server.
     * @param interactions The current instance of interactions.
     */
    private void getCredentials(Interactions interactions) {
        HttpsMessage message = new HttpsMessage("POST", ConstantValues.OAUTH_CREDENTIALS_URL);
        HashMap<String, String> additionalParameter = new HashMap<>();
        additionalParameter.put("serialNumber", AuthValues.SERIAL_NUMBER);
        message.setAdditionalParameter(additionalParameter);
        message.addProperty("Content-Type", "application/x-www-form-urlencoded");
        message.addProperty("Content-Length", "25");
        message.setBody("serialNumber=" + AuthValues.SERIAL_NUMBER);
        String btleClientAuthCredentials = message.sendMessage();
        Log.e(TAG, "btleClientAuthCredentials = " + btleClientAuthCredentials);
        if (btleClientAuthCredentials != null && btleClientAuthCredentials.contains("authSubKey")) {
            Log.e(TAG, "btleClientAuthCredentials = " + btleClientAuthCredentials);
            AuthValues.setAuthenticationKey(btleClientAuthCredentials.substring(44, 76));
            AuthValues.setNonce(btleClientAuthCredentials.substring(btleClientAuthCredentials.indexOf("\"nonce\":") + 8, btleClientAuthCredentials.length() - 3));
            InternalStorage.saveString(AuthValues.AUTHENTICATION_KEY, ConstantValues.FILE_AUTH_KEY, activity);
            InternalStorage.saveString(AuthValues.NONCE, ConstantValues.FILE_NONCE, activity);
        } else if(btleClientAuthCredentials != null && btleClientAuthCredentials.contains("Tracker with serialNumber")){
            activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    toast.setText("Error: Tracker has not yet been paired with the server!");
                    toast.show();
                }
            });
        }
        interactions.interactionFinished();
        interactions.intEmptyInteraction();
        message.logParameter();
    }

}
