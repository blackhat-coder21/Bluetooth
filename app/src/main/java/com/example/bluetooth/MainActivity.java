package com.example.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import android.app.ProgressDialog;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private static final String TAG = "BluetoothDemo";
    private static final String SERVICE_UUID = "27b7d1da-08c7-4505-a6d1-2459987e5e2d";

    private Button btnShowBluetoothDevices;
    private Button btnDiscoverDevices;
    private ListView listViewBluetoothDevices;
    private BluetoothAdapter bluetoothAdapter;
    private ArrayList<BluetoothDevice> bluetoothDevices;
    private BluetoothSocket bluetoothSocket;
    private ArrayAdapter<BluetoothDevice> adapter;
    private ProgressDialog progressDialog;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        btnShowBluetoothDevices = findViewById(R.id.btnShowBluetoothDevices);
        btnDiscoverDevices = findViewById(R.id.btnDiscoverDevices);
        listViewBluetoothDevices = findViewById(R.id.listViewBluetoothDevices);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Check if Bluetooth is supported on the device
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        // Request permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            }
            else {
                // To display paired devices
                showPairedDevices();
            }
        }
        else {
            showPairedDevices();
        }

        btnShowBluetoothDevices.setOnClickListener(v -> showPairedDevices());
        btnDiscoverDevices.setOnClickListener(v -> startDiscovery());

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        // Register for broadcasts when discovery is finished
        IntentFilter discoveryFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(receiver, discoveryFilter);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Please wait...");
        progressDialog.setCancelable(false);
    }

    // To display paired Bluetooth devices
    private void showPairedDevices() {
        bluetoothDevices = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // Request Bluetooth connect permissions if needed
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_LOCATION_PERMISSION);
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            bluetoothDevices.addAll(pairedDevices);
        }
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, bluetoothDevices);
        listViewBluetoothDevices.setAdapter(adapter);

        listViewBluetoothDevices.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice selectedDevice = bluetoothDevices.get(position);
            // Connect to the selected device
            connectToDevice(selectedDevice);
        });
    }

    private void startDiscovery() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQUEST_LOCATION_PERMISSION);
            return;
        }
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothDevices.clear();
        adapter.notifyDataSetChanged();
        bluetoothAdapter.startDiscovery();
        Toast.makeText(this, "Discovering devices...", Toast.LENGTH_SHORT).show();
    }

    private void connectToDevice(BluetoothDevice device) {
        new Thread(() -> {
            try {
                runOnUiThread(() -> {
                    progressDialog.show();
                });

                // Check Bluetooth permissions are granted
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("No BLUETOOTH_CONNECT permission");
                }


                bluetoothAdapter.cancelDiscovery();
                // Initialize the Bluetooth socket and connect
                bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(SERVICE_UUID));
                bluetoothSocket.connect();

                runOnUiThread(() -> {
                    // Dismiss the progress dialog once connected
                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show();
                });
            }
            catch (IOException e) {
                Log.e(TAG, "Error connecting to device: " + e.getMessage());

                runOnUiThread(() -> {
                    // Dismiss the progress dialog if an error occurs
                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, "Failed to connect to " + device.getName(), Toast.LENGTH_SHORT).show();
                });

                // Close the socket to avoid memory leaks
                closeConnection();
            }
        }).start();
    }

    private void closeConnection() {
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
            catch (IOException e) {
                Log.e(TAG, "Could not close the client socket: " + e.getMessage());
            }
        }
    }





    // Broadcast receiver for discovered devices
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    bluetoothDevices.add(device);
                    adapter.notifyDataSetChanged();
                }
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Toast.makeText(MainActivity.this, "Discovery finished", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        // Close the Bluetooth socket if it's connected
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            }
            catch (IOException e) {
                Log.e(TAG, "Could not close the Bluetooth socket: " + e.getMessage());
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showPairedDevices();
            }
            else {

                Toast.makeText(this, "Permission required to access Bluetooth", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
