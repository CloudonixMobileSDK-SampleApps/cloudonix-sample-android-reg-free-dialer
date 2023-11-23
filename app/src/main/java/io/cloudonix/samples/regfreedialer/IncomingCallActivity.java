package io.cloudonix.samples.regfreedialer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.cloudonix.samples.regfreedialer.databinding.ActivityIncomingCallBinding;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class IncomingCallActivity extends AppCompatActivity {
	private static final String TAG = "dialerIncoming";

	private ActivityIncomingCallBinding binding;
	private final OkHttpClient ringingClient = new OkHttpClient.Builder()
			.callTimeout(0, TimeUnit.MILLISECONDS)
			.readTimeout(0, TimeUnit.MILLISECONDS).build();
	private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			finish();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		binding = ActivityIncomingCallBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		setSupportActionBar(binding.toolbar);

		Intent intent = getIntent();
		if (intent == null)
			return;
		String session = intent.getStringExtra("session");
		String callerID = intent.getStringExtra("callerId");
		String ringingURL = intent.getStringExtra("ringingURL");
		binding.callerName.setText(callerID);
		binding.sessionToken.setText(session);
		Log.i(TAG, "Received incoming call with session " + session);
		startRinging(ringingURL).thenRun(this::finish);
		DialerApplication app = (DialerApplication) getApplication();
		binding.answerBtn.setOnClickListener(view -> {
			Snackbar.make(findViewById(R.id.incomingCallView), "Answering call...", Snackbar.LENGTH_SHORT).show();
			binding.answerBtn.setEnabled(false);
			app.pickup(session).exceptionally(t -> {
				Log.e(TAG, "Failed to accept call:", t);
				finish();
				return null;
			});
		});
		binding.rejectBtn.setOnClickListener(view -> {
			Snackbar.make(findViewById(R.id.incomingCallView), "Rejecting call...", Snackbar.LENGTH_SHORT).show();
			binding.rejectBtn.setEnabled(false);
			rejectCall(ringingURL).thenRun(this::finish);
		});
	}

	@Override
	public boolean onSupportNavigateUp() {
		Snackbar.make(findViewById(R.id.incomingCallView), "Trying to cancel by navigation", Snackbar.LENGTH_LONG).show();
		return false;
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(messageReceiver, new IntentFilter(OngoingCallActivity.ONGOING_MESSAGE), RECEIVER_EXPORTED);
	}

	@Override
	protected void onDestroy() {
		unregisterReceiver(messageReceiver);
		super.onDestroy();
	}

	private CompletableFuture<Void> startRinging(String url) {
		return httpCall(new Request.Builder().get().url(url).build())
				.thenAccept(response -> {
					// when we get a response,it means the ringing has ended, either because we answered,
					// rejected or because the caller has cancelled
					switch (response.code()) {
						case 200:
							Log.i(TAG, "Call was answered");
							break;
						case 205:
							Log.i(TAG, "Call was cancelled");
							break;
						default: // could happen, don't freake out
							Log.e(TAG, "Error from ringing URL: " + response);
					}
				})
				.exceptionally(t -> {
					Log.e(TAG, "Unexpected ringing URL failure: ", t);
					return null;
				});
	}

	private CompletableFuture<Void> rejectCall(String url) {
		Log.i(TAG, "Rejecting call using " + url);
		return httpCall(new Request.Builder().delete().url(url).build())
				.thenAccept(response -> Log.i(TAG, "Rejected call result: " + response))
				.exceptionally(t -> {
					Log.e(TAG, "Unexpected call rejection URL failure: ", t);
					return null;
				});
	}

	private CompletableFuture<Response> httpCall(Request req) {
		CompletableFuture<Response> promise = new CompletableFuture<>();
		ringingClient.newCall(req).enqueue(new Callback() {
			@Override
			public void onFailure(@NonNull Call call, @NonNull IOException e) {
				promise.completeExceptionally(e);
			}

			@Override
			public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
				promise.complete(response);
			}
		});
		return promise;
	}
}