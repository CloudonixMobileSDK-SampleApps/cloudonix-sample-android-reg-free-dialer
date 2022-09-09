package io.cloudonix.samples.regfreedialer;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;
import com.google.gson.annotations.Expose;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PushNotificationService extends FirebaseMessagingService {
	public static final String TAG = "dialerNotifications";
	public static final String TOKEN_KEY = "device-token";
	public static volatile boolean wasRegistered = false;

	private SharedPreferences prefs;

	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private final OkHttpClient client = new OkHttpClient.Builder()
			.connectTimeout(30, TimeUnit.SECONDS) // ngrok is sometimes slow to wake up
			.build();
	private static class RegisterRequest {
		@Expose
		String identifier;
		@Expose String msisdn;
		@Expose String type = "android";

		RegisterRequest(String identifier, String msisdn) {
			this.identifier = identifier;
			this.msisdn = msisdn;
		}
	}

	@Override
	public void onNewToken(@NonNull String token) {
		prefs.edit().putString(TOKEN_KEY, token).apply();
		registerDevice(token);
	}

	@Override
	public void onCreate() {
		prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		// make sure our device is registered
		String oldToken = prefs.getString(TOKEN_KEY, null);
		if (oldToken != null && !wasRegistered) {
			registerDevice(oldToken);
			wasRegistered = true;
		}
	}

	@Override
	public void onMessageReceived(@NonNull RemoteMessage message) {
		Log.i(TAG, "Received push data: " + message.getMessageId() + " - " + message.getData());
		Log.i(TAG, "Message TTL: " + message.getTtl());
		startActivity(message.toIntent()
				//.setAction(getResources().getString(R.string.incoming_call_action))
				.setClass(this, IncomingCallActivity.class)
				.addFlags(FLAG_ACTIVITY_NEW_TASK));
	}

	public void registerDevice(String token) {
		if (token == null) {
			Log.e(TAG, "Got no token to register!");
			return;
		}
		String msisdn = getResources().getString(R.string.msisdn);
		String regURL = getResources().getString(R.string.regfree_register);
		Log.d(TAG, "Registering device " + token + " for number " + msisdn);
		request(regURL, new RegisterRequest(token, msisdn))
				.thenAccept(res -> Log.d(TAG, "Got registration response: " + res.toString()))
				.exceptionally(t -> {
					Log.e(TAG, "Error registering device:", t);
					if (t.getMessage().contains("Timeout"))
						registerDevice(token);
					return null;
				});
	}

	private CompletableFuture<Response> request(String url, Object bodyData) {
		CompletableFuture<Response> result = new CompletableFuture<>();
		Callback callback = new Callback() {
			@Override
			public void onFailure(@NonNull Call call, @NonNull IOException e) {
				result.completeExceptionally(e);
			}

			@Override
			public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
				result.complete(response);
			}
		};
		client.newCall(new Request.Builder().url(url).post(RequestBody.create(new Gson().toJson(bodyData), JSON))
				.build()).enqueue(callback);
		return result;
	}
}
