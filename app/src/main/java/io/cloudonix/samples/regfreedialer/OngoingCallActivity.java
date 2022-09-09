package io.cloudonix.samples.regfreedialer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.google.android.material.snackbar.Snackbar;

import net.greenfieldtech.cloudonixsdk.api.interfaces.IVoIPObserver;

import java.util.Objects;

import io.cloudonix.samples.regfreedialer.databinding.ActivityOngoingCallBinding;

public class OngoingCallActivity extends AppCompatActivity {
	private static final String TAG = "dialerOngoing";
	public static final String ONGOING_MESSAGE = "io.cloudonix.samples.regfree.ongoing";
	public static final String CALL_STATE_EXTRA = "io.cloudonix.samples.regfree.call-state";
	public static final String KEY_EXTRA = "io.cloudonix.samples.regfree.call-key";

	private ActivityOngoingCallBinding binding;
	private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			IVoIPObserver.CallState state = (IVoIPObserver.CallState) intent.getSerializableExtra(CALL_STATE_EXTRA);
			if (state == null)
				return;
			Log.i(TAG, "Updated call state: " + state.name());
			switch (state) {
				case CALL_STATE_DISCONNECTED:
				case CALL_STATE_DISCONNECTEDDUETOBUSY:
				case CALL_STATE_DISCONNECTEDDUETONETWORKCHANGE:
				case CALL_STATE_DISCONNECTEDDUETONOMEDIA:
				case CALL_STATE_DISCONNECTEDDUETOTIMEOUT:
				case CALL_STATE_DISCONNECTEDMEDIACHANGED:
					finish(state);
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		String callKey = Objects.requireNonNull(getIntent().getStringExtra(KEY_EXTRA), "Call key must be provided for call");
		binding = ActivityOngoingCallBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		DialerApplication app = (DialerApplication) getApplication();
		binding.hangupBtn.setOnClickListener(view -> {
			Snackbar.make(findViewById(R.id.ongoingCallView), "Hanging up...", Snackbar.LENGTH_SHORT).show();
			binding.hangupBtn.setEnabled(false);
			app.getClient().thenAccept(client -> client.hangup(callKey)).exceptionally(t -> {
				Log.e(TAG, "Error hanging up:", t);
				return null;
			});
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(messageReceiver, new IntentFilter(ONGOING_MESSAGE));
	}

	@Override
	protected void onDestroy() {
		unregisterReceiver(messageReceiver);
		super.onDestroy();
	}

	public void finish(IVoIPObserver.CallState callState) {
		Snackbar.make(findViewById(R.id.ongoingCallView), makeDisconnectLabel(callState), Snackbar.LENGTH_LONG)
				.addCallback(new Snackbar.Callback() {
					@Override
					public void onDismissed(Snackbar transientBottomBar, int event) {
						finish();
					}
				})
				.show();
	}

	private String makeDisconnectLabel(IVoIPObserver.CallState callState) {
		switch (callState) {
			case CALL_STATE_DISCONNECTEDDUETOBUSY:
				return "Call rejected: destination busy";
			case CALL_STATE_DISCONNECTEDDUETONETWORKCHANGE:
				return "Call failed due to network problem";
			case CALL_STATE_DISCONNECTEDDUETONOMEDIA:
				return "Call failed due to media problem";
			case CALL_STATE_DISCONNECTEDDUETOTIMEOUT:
				return "Call failed due to network timeout";
			case CALL_STATE_DISCONNECTEDMEDIACHANGED:
				return "Call failed due to media error";
			default:
				return "Call done";
		}
	}
}
