package io.cloudonix.samples.regfreedialer;

import android.app.Application;
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

	VoIPClient client = new VoIPClient(this);

	@Override
	public void onCreate() {
		super.onCreate();
		client.create(new RegistrationData() {{
			setTransportType(TransportType.TRANSPORT_TYPE_UDP);
			setWorkflowType(WorkflowType.WORKFLOW_TYPE_REGISTRATION_FREE);
			setDomain(getResources().getString(R.string.cloudonix_domain));
			setServerUrl(getResources().getString(R.string.cloudonix_server));
			setUsername(getResources().getString(R.string.msisdn));
			setDisplayName(getResources().getString(R.string.msisdn));
		}});
	}

	public CompletableFuture<Void> pickup(String session) {
		return client.pickup(session);
	}

	public void hangup(String callKey) {
		client.hangup(callKey).exceptionally(t -> {
			Log.e(TAG, "Error hanging up:", t);
			return null;
		});
	}
}
