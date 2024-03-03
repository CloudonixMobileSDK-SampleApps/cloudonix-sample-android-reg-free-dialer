package io.cloudonix.samples.regfreedialer;

import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import net.greenfieldtech.cloudonixsdk.api.interfaces.IVoIPObserver;
import net.greenfieldtech.cloudonixsdk.api.models.RegistrationData;
import net.greenfieldtech.cloudonixsdk.appinterface.CloudonixSDKClient;
import net.greenfieldtech.cloudonixsdk.appinterface.DefaultVoipObserver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is an example implementation of using the Cloudonix Mobile SDK for Android
 * to receive Registration-Free calls.
 * This implementation is pretty minimal and should be used only as a reference.
 * The workflow is as follows:
 * 1. During {@link #create(RegistrationData)} the SDK gets initialized and then started.
 * 2. When the {@code onLicense} event is received, the SIP configuration is sent to the
 *    SDK to initialize the SIP stack.
 * 3. When the {@code onSipStarted} event is received, we start the {@link PushNotificationService}
 *    to wait for incoming Registration-Free calls.
 * 4. When a call is received, the application will invoke {@link #pickup(String)}
 *    to connect the call - passing it the Registration-Free session token.
 * 5. The SDK will send {@code onCallState} events to update the application on the state of
 *    the call, until it is disconnected.
 * 6. If the application wants to hangup the call, it will invoke {@link #hangup(String)} with the call
 *    key from the {@code onCallState} event.
 */
public class VoIPClient {
	private static final String TAG = "voip";
	private final DialerApplication context;
	private CloudonixSDKClient sdk;
	private CompletableFuture<Void> waitingForStart = new CompletableFuture<>();
	private RegistrationData account;

	public class OngoingCall {
		public String key;
		public IVoIPObserver.CallState state;
	}

	private final ConcurrentHashMap<String, OngoingCall> currentCalls = new ConcurrentHashMap<>();

	private final DefaultVoipObserver mainListener = new DefaultVoipObserver(){
		@Override
		public void onLog(int level, String message) {
			boolean indent = false;
			for (String line : message.split("\n")) {
				Log.println(CloudonixSDKClient.logLevelToAndroid(level), "Cloudonix", (indent ? "  " : "") + line);
				indent = true;
			}
		}

		@Override
		public void onSipStarted() {
			context.sendBroadcast(new Intent(MainActivity.GLOBAL_MESSAGE)
					.putExtra(MainActivity.SNACKBAR_TEXT_EXTRA, "Cloudonix SDK started"));
			waitingForStart.complete(null);
			// make sure the Firebase service is started to re-register the device if needed
			// (Firebase doesn't auto-start the service if it doesn't think we need a new token)
			context.startService(new Intent(context, PushNotificationService.class));
		}

		@Override
		public void onSipStartFailed(String error) {
			context.sendBroadcast(new Intent(MainActivity.GLOBAL_MESSAGE)
					.putExtra(MainActivity.SNACKBAR_TEXT_EXTRA, "Cloudonix SDK failed to start: " + error));
		}

		@Override
		public void onSipStopped() {
			context.sendBroadcast(new Intent(MainActivity.GLOBAL_MESSAGE)
					.putExtra(MainActivity.SNACKBAR_TEXT_EXTRA, "Cloudonix SDK stopped"));
			//waitingForStart = new CompletableFuture<>();
		}

		@Override
		public void onLicense(LicensingState state, String description) {
			sdk.setConfig(IVoIPObserver.ConfigurationKey.USER_AGENT, "Cloudonix-Sample-RegFree/1.0");
			sdk.setConfig(IVoIPObserver.ConfigurationKey.DISABLE_PLATFORM_LOGS, "1");
			sdk.setConfiguration(account);
		}

		@Override
		public void onCallState(String callKey, CallState callState, String contactUrl) {
			if (currentCalls.containsKey(callKey)) {
				currentCalls.get(callKey).state = callState;
				context.sendBroadcast(new Intent(OngoingCallActivity.ONGOING_MESSAGE)
						.putExtra(OngoingCallActivity.CALL_STATE_EXTRA, callState));
			}
			switch (callState) {
				case CALL_STATE_STARTING:
					currentCalls.put(callKey, new OngoingCall(){{ this.key = callKey; this.state = callState; }});
					context.startActivity(new Intent(context, OngoingCallActivity.class)
							.putExtra(OngoingCallActivity.KEY_EXTRA, callKey)
							.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
					break;
				case CALL_STATE_DISCONNECTED:
				case CALL_STATE_DISCONNECTEDDUETOBUSY:
				case CALL_STATE_DISCONNECTEDDUETONETWORKCHANGE:
				case CALL_STATE_DISCONNECTEDDUETONOMEDIA:
				case CALL_STATE_DISCONNECTEDDUETOTIMEOUT:
				case CALL_STATE_DISCONNECTEDMEDIACHANGED:
					currentCalls.remove(callKey);
					break;
				default:
					Log.d(TAG, "Unhandled call status state " + callState.name() + " for call " + callKey);
			}
		}
	};

	public VoIPClient(DialerApplication context) {
		this.context = context;
	}

	public void create(RegistrationData registrationData) {
		account = registrationData;
		String license = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.cloudonix_license_key)))
				.lines().reduce("", (a,b) -> a+b);
		sdk = new CloudonixSDKClient(license, context, mainListener);
		sdk.start();
	}

	public CompletableFuture<Void> pickup(String session) {
		return waitingForStart.thenCompose(__ -> {
			if (sdk.dialRegistrationFree(session))
				return CompletableFuture.completedFuture(null);
			return CompletableFuture.failedFuture(new Exception("Error dialing"));
		});
	}

	public CompletableFuture<Void> hangup(String callKey) {
		return waitingForStart.thenRun(() -> sdk.hangup(callKey));
	}

}
