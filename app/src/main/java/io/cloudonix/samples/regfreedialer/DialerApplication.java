package io.cloudonix.samples.regfreedialer;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import net.greenfieldtech.cloudonixsdk.api.interfaces.IVoIPObserver;
import net.greenfieldtech.cloudonixsdk.api.models.RegistrationData;
import net.greenfieldtech.cloudonixsdk.appinterface.CloudonixSDKClient;
import net.greenfieldtech.cloudonixsdk.appinterface.DefaultVoipObserver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class DialerApplication extends Application {
	private static final String TAG = "dialerApp";

	private static class OngoingCall {
		public String key;
		public IVoIPObserver.CallState state;
	}

	private CloudonixSDKClient sdk;
	private final HashMap<String, OngoingCall> currentCalls = new HashMap<>();
	private CompletableFuture<Void> waitingForStart = new CompletableFuture<>();
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
			sendBroadcast(new Intent(MainActivity.GLOBAL_MESSAGE)
					.putExtra(MainActivity.SNACKBAR_TEXT_EXTRA, "Cloudonix SDK started"));
			waitingForStart.complete(null);
			// make sure the Firebase service is started to re-register the device if needed
			// (Firebase doesn't auto-start the service if it doesn't think we need a new token)
			startService(new Intent(DialerApplication.this, PushNotificationService.class));
		}

		@Override
		public void onSipStopped() {
			sendBroadcast(new Intent(MainActivity.GLOBAL_MESSAGE)
					.putExtra(MainActivity.SNACKBAR_TEXT_EXTRA, "Cloudonix SDK stopped"));
			waitingForStart = new CompletableFuture<>();
		}

		@Override
		public void onLicense(LicensingState state, String description) {
			configure();
		}

		@Override
		public void onCallState(String callKey, CallState callState, String contactUrl) {
			if (currentCalls.containsKey(callKey)) {
				currentCalls.get(callKey).state = callState;
				sendBroadcast(new Intent(OngoingCallActivity.ONGOING_MESSAGE)
						.putExtra(OngoingCallActivity.CALL_STATE_EXTRA, callState));
			}
			switch (callState) {
				case CALL_STATE_STARTING:
					currentCalls.put(callKey, new OngoingCall(){{ this.key = callKey; this.state = callState; }});
					startActivity(new Intent(DialerApplication.this, OngoingCallActivity.class)
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

	@Override
	public void onCreate() {
		super.onCreate();
		String license = new BufferedReader(new InputStreamReader(getResources().openRawResource(R.raw.cloudonix_license_key)))
				.lines().reduce("", (a,b) -> a+b);
		sdk = new CloudonixSDKClient(license, this, mainListener);
		sdk.start();
	}

	public void configure() {
		sdk.setConfig(IVoIPObserver.ConfigurationKey.USER_AGENT, "Cloudonix-Sample-RegFree/1.0");
		sdk.setConfig(IVoIPObserver.ConfigurationKey.DISABLE_PLATFORM_LOGS, "1");
		sdk.setConfiguration(new RegistrationData() {{
			setTransportType(TransportType.TRANSPORT_TYPE_UDP);
			setWorkflowType(WorkflowType.WORKFLOW_TYPE_REGISTRATION_FREE);
			setDomain(getResources().getString(R.string.cloudonix_domain));
			setServerUrl(getResources().getString(R.string.cloudonix_server));
			setUsername(getResources().getString(R.string.msisdn));
			setDisplayName(getResources().getString(R.string.msisdn));
		}});
	}

	public CompletableFuture<CloudonixSDKClient> getClient() {
		return waitingForStart.thenApply(v -> sdk);
	}

}
