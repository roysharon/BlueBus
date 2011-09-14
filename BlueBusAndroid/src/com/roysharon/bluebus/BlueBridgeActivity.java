package com.roysharon.bluebus;

import java.util.Set;

import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class BlueBridgeActivity extends ListActivity {

    private static final String ACTION_STOP_SCAN = "Click here to stop scan";
	private static final String ACTION_RETRY_CONNECTING = "Could not connect. Retry...";
	private static final String ACTION_DISCONNECT = "Disconnect";
	private static final String ACTION_CONNECTING = "Connecting...";
	private static final String ACTION_PAIR_NEW_DEVICE = "Pair new device...";
	private static final String ACTION_RETRY_SCAN = "Retry scan...";
	private static final String ACTION_CHOOSE_ANOTHER = "Choose another device...";
	private static final String ACTION_FAILED_USING = "Could not open in/out streams.\nDisconnect";
	private static final String ACTION_STOP_USING = "Opened in/out streams.\nDisconnect";

	private static final int REQUEST_ENABLE_BT = 19001;

    private BlueBridge blueBridge;
	private ArrayAdapter<String> devices;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        blueBridge = new BlueBridge(this);
        blueBridge.registerListener(useHandler);
        
        devices = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        setListAdapter(devices);
        getListView().setOnItemClickListener(deviceClicked);
        
		registerReceiver(foundDeviceReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
		registerReceiver(foundDeviceReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
		registerReceiver(foundDeviceReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

		findDevice(true);
    }

	@Override
	protected void onDestroy() {
    	unregisterReceiver(foundDeviceReceiver);
    	
		super.onDestroy();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case REQUEST_ENABLE_BT:
				if (resultCode != RESULT_OK) {
		            Toast.makeText(this, "This application requires Bluetooth enabled to work, so it will close now. Sorry.", Toast.LENGTH_LONG).show();
		            finish();
		            return;
				} else findDevice(true);
		}
	}

	
	//----- Device List -------------------------------------------------------
	
	private void findDevice(boolean scan) {
        if (!blueBridge.isEnabled()) {
            resetList("Bluetooth is disabled. Please turn it on.", null);
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }
        
        resetList("", ACTION_PAIR_NEW_DEVICE);
		Set<BluetoothDevice> pairedDevices = BlueBridge.adapter.getBondedDevices();
		if (pairedDevices.size() > 0) {
			for (BluetoothDevice device : pairedDevices) addRowBeforeEnd(device.getName(), device.getAddress());
		} else if (scan) discoverDevice();
	}
	
	private void discoverDevice() {
		if (!BlueBridge.adapter.startDiscovery()) resetList("No Bluetooth devices detected.", ACTION_RETRY_SCAN);
	}
    
    private void resetList(String text, String action) {
    	devices.clear();
    	addRow(text, action);
	}

	private void addRow(String text, String action) {
		devices.add(text + '\n' + action);
	}

	private void addRow(String text, String action, int index) {
		devices.insert(text + '\n' + action, index);
	}

	private void addRowBeforeEnd(String text, String action) {
		int count = devices.getCount();
		if (count == 0) addRow(text, action);
		else addRow(text, action, count - 1);
	}
    
    private final BroadcastReceiver foundDeviceReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String address = device.getAddress(), name = device.getName();
                for (int i = devices.getCount() - 1; i >= 0; --i) {
                	String dinfo = devices.getItem(i), daction = getAction(dinfo);
                	if (address.equals(daction)) {
                		devices.remove(dinfo);
                		addRow(name, address, i);
                		return;
                	}
                }
                addRowBeforeEnd(name, address);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
            	resetList("Looking for Bluetooth devices...", ACTION_STOP_SCAN);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
            	devices.remove(devices.getItem(devices.getCount() - 1));
            	if (devices.getCount() == 0) resetList("No Bluetooth devices detected.", ACTION_RETRY_SCAN);
            	else addRow("", ACTION_RETRY_SCAN);
	        }
        }
    };

	private OnItemClickListener deviceClicked = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			if (BlueBridge.adapter.isDiscovering()) BlueBridge.adapter.cancelDiscovery(); // handles also ACTION_STOP_SCAN

			TextView text = (TextView)view.findViewById(android.R.id.text1);
			String action = getAction(text.getText().toString());
			if (BluetoothAdapter.checkBluetoothAddress(action)) {
				blueBridge.setDevice(action);
				blueBridge.connectDevice();
			}
			else if (ACTION_RETRY_SCAN.equals(action) || ACTION_PAIR_NEW_DEVICE.equals(action)) discoverDevice();
			else if (ACTION_CONNECTING.equals(action) || ACTION_DISCONNECT.equals(action) || ACTION_CHOOSE_ANOTHER.equals(action)) {
				blueBridge.disconnectDevice();
				findDevice(false);
			} else if (ACTION_RETRY_CONNECTING.equals(action)) blueBridge.connectDevice();
		}
	};

	private String getAction(String info) {
		String[] lines = info.split("\n");
		return lines.length == 1 ? null : lines[lines.length - 1];
	}

	
	//----- Handler -----------------------------------------------------------
	
	private Handler useHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			String deviceName = blueBridge.getDeviceName();
			switch (msg.what) {
				case BlueBridge.CONNECTING_DEVICE:
					resetList(deviceName, ACTION_CONNECTING);
					break;
	
				case BlueBridge.MANAGE_CONNECTED_SOCKET:
					BluetoothSocket socket = (BluetoothSocket) msg.obj;
					String action = socket != null ? ACTION_DISCONNECT : ACTION_RETRY_CONNECTING;
					resetList(deviceName, action);
					if (socket == null) addRow("", ACTION_CHOOSE_ANOTHER);
					break;
					
				case BlueBridge.FAILED_USING_DEVICE:
					resetList(deviceName, ACTION_FAILED_USING);
					break;
	
				case BlueBridge.SUCCEEDED_USING_DEVICE:
					resetList(deviceName, ACTION_STOP_USING);
					break;
			}
		}
	};

}