package io.cloudonix.samples.regfreedialer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {
	private static final String TAG = "dialerMain";
	public static final String GLOBAL_MESSAGE = "io.cloudonix.samples.regfree.main";
	public static final String SNACKBAR_TEXT_EXTRA = "io.cloudonix.samples.regfree.snack";

	BroadcastReceiver messageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String text = intent.getStringExtra(SNACKBAR_TEXT_EXTRA);
			if (text == null)
				return;
			Log.i(TAG, "Showing message: "+ text);
			Snackbar.make(findViewById(R.id.mainView), text, Snackbar.LENGTH_SHORT).show();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		verifyPermissions();
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(messageReceiver, new IntentFilter(GLOBAL_MESSAGE));
	}

	@Override
	protected void onDestroy() {
		unregisterReceiver(messageReceiver);
		super.onDestroy();
	}

	private void verifyPermissions() {
		if (getApplicationContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
			return;
		requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, 15);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		for (int i = 0; i < permissions.length; i++) {
			if (grantResults[i] == PackageManager.PERMISSION_DENIED)
				new AlertDialog.Builder(this)
						.setTitle("Missing permissions")
						.setMessage("This application cannot continue without the required permission: " + permissions[i])
						.setPositiveButton("Try again...", (dialog, which) -> verifyPermissions())
						.setNegativeButton("Abort", (dialog, which) -> System.exit(1))
						.show();
		}
	}
}