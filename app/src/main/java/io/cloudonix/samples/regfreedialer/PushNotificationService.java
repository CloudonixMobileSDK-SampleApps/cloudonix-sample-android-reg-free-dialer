package io.cloudonix.samples.regfreedialer;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.firebase.messaging.FirebaseMessaging;
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

/**
 * Implementation of receiving push notification for incoming calls through Google's
 * Firebase Cloud Messaging.
 * In order for this implementation to work correctly, set up a Registration-Free server
 * (e.g. Cloudonix Sample Registration-Free server at https://github.com/CloudonixMobileSDK-SampleApps/cloudonix-sample-reg-free-server)
 * and set its URL in {@code strings.xml} under {@code regfree_register}.
 * Starting with Android 13, when a Registration-Free incoming call notification is received,
 * when the application is in the background, this implementation uses a high priority notification
 * to notify the user. The old style "full screen activity" can be used only under strict conditions
 * (such as the application being a call manager) that this code example will not try to achieve.
 */
public class PushNotificationService extends FirebaseMessagingService {
	public static final String TAG = "dialerNotifications";
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	/** Identity of notification channel */
	private final static String notificationIncomingChannel = "reg-free-call";
	/** Title for notification channel in the Android application notifications setting */
	private final static String notificationIncomingTitle = "Incoming calls";

	public static volatile boolean wasRegistered = false;
	private static boolean isBackground = false;

	private final OkHttpClient client = new OkHttpClient.Builder()
			.connectTimeout(30, TimeUnit.SECONDS) // ngrok is sometimes slow to wake up
			.build();
	private NotificationManager notificationManager;

	/**
	 * A POJO for an HTTP request to the Registration-Free server.
	 * This request has fields to match the Cloudonix Sample Registration-Free server.
	 */
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

	/**
	 * This method is automatically called by Firebase Cloud Messaging when the application is first installed,
	 * and given the application's "device token".
	 * We also make sure this is called by onCreate() on app startup, to make sure the registration
	 * is up to date.
	 */
	@Override
	public void onNewToken(@NonNull String token) {
		registerDevice(token);
	}

	/**
	 * When the service is first created (by {@link VoIPClient} when it sets up the Cloudonix Mobile SDK),
	 * it will:
	 * 1. Make sure the application is registered for push notifications.
	 * 2. Start monitoring the foreground/background state.
	 * 3. Sets up the notification channel where we send incoming call notifications.
	 */
	@Override
	public void onCreate() {
		if (!wasRegistered) // make sure the application is registered for push notifications
			FirebaseMessaging.getInstance().getToken()
					.addOnCompleteListener(task -> {
						if (task.isSuccessful()) {
							onNewToken(task.getResult());
						} else {
							showAlert("Failed to get device token: " + task.getException());
						}
					});
		ProcessLifecycleOwner.get().getLifecycle().addObserver(new BackgroundObserver());
		notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		var channel = new NotificationChannel(notificationIncomingChannel,
				notificationIncomingTitle, NotificationManager.IMPORTANCE_HIGH);
		notificationManager.createNotificationChannel(channel);
	}

	private void showAlert(String message) {
		new AlertDialog.Builder(this)
				.setTitle("Push notifications error")
				.setMessage(message)
				.setPositiveButton("OK", (dialog, which) -> {})
				.show();
	}

	/**
	 * This method will be called by the Firebase Cloud Messaging when the Registration-Free server sends
	 * and incoming call notification. This method will just pack the notification into an intent and try
	 * to deliver it to the {@link IncomingCallActivity}.
	 * @param message the incoming call notification that was received
	 */
	@Override
	public void onMessageReceived(@NonNull RemoteMessage message) {
		Log.i(TAG, "Received push data: " + message.getMessageId() + " - " + message.getData());
		Log.i(TAG, "Message TTL: " + message.getTtl());
		Intent incomingCallIntent = message.toIntent()
				.setClass(this, IncomingCallActivity.class)
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
				.addFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION)
				.addFlags(Intent.FLAG_FROM_BACKGROUND);
		if (isBackground) {
			var notificationBuilder = new NotificationCompat.Builder(this, notificationIncomingChannel)
					.setSmallIcon(R.drawable.ic_launcher_foreground)
					.setContentTitle("Incoming call from " + message.getData())
					.setPriority(NotificationCompat.PRIORITY_HIGH)
					.setCategory(NotificationCompat.CATEGORY_CALL)
					.setContentIntent(PendingIntent.getActivity(this, 0, incomingCallIntent,
									PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
			notificationManager.notify(14, notificationBuilder.build());
		} else {
			startActivity(incomingCallIntent);
		}
	}

	/**
	 * Call the Registration-Free server to register for incoming calls, instead of using
	 * periodic SIP registration.
	 * This implementation targets the Cloudonix Sample Registration-Free server, and may
	 * need to be adapted to other implementations.
	 * @param token
	 */
	public void registerDevice(String token) {
		if (token == null) {
			Log.e(TAG, "Got no token to register!");
			return;
		}
		String msisdn = getResources().getString(R.string.msisdn);
		String regURL = getResources().getString(R.string.regfree_register);
		Log.d(TAG, "Registering device " + token + " for number " + msisdn);
		request(regURL, new RegisterRequest(token, msisdn))
				.whenComplete((res,t) -> {
					if (res == null)
						return;
					Log.d(TAG, "Got registration response: " + res);
					res.close();
					wasRegistered = true;
				})
				.exceptionally(t -> {
					Log.e(TAG, "Error registering device:", t);
					if (String.valueOf(t).contains("Timeout"))
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
			public void onResponse(@NonNull Call call, @NonNull Response response) {
				result.complete(response);
			}
		};
		client.newCall(new Request.Builder().url(url).post(RequestBody.create(new Gson().toJson(bodyData), JSON))
				.build()).enqueue(callback);
		return result;
	}

	/**
	 * Monitor background state
	 */
	private class BackgroundObserver implements DefaultLifecycleObserver {
		@Override
		public void onStart(@NonNull LifecycleOwner owner) {
			isBackground = false;
		}

		@Override
		public void onStop(@NonNull LifecycleOwner owner) {
			isBackground = true;
		}
	}
}
