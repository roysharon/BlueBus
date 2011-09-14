package com.roysharon.bluebus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class BlueBridge {

	public static BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
	private static String selectedDeviceMAC = null;
	private static ReentrantLock connecting = new ReentrantLock();

	private Context context;

	public BlueBridge(Context context) {
		this.context = context;
		
		if (adapter == null) {
			Toast.makeText(context, "Your device does not seem to support Bluetooth.", Toast.LENGTH_LONG).show();
			return;
		}

		if (selectedDeviceMAC == null) {
			SharedPreferences prefs = context.getSharedPreferences("BlueBus", Context.MODE_PRIVATE);
			selectedDeviceMAC = prefs.getString("BluetoothMAC", null);
		}
		
		connectDevice();
	}

	public boolean isEnabled() {
		return adapter.isEnabled();
	}
	
	public boolean isConnected() {
		return adapter.isEnabled() && deviceUser != null;
	}
	
	public String getDeviceName() {
		return adapter.getRemoteDevice(selectedDeviceMAC).getName();
	}
	
	public void setDevice(String address) {
		selectedDeviceMAC = address;
		
		SharedPreferences prefs = context.getSharedPreferences("BlueBus", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString("BluetoothMAC", address);
		editor.commit();
	}

	
	// ----- Listeners ---------------------------------------------------------

	public static final int CONNECTING_DEVICE = 21001;
	public static final int MANAGE_CONNECTED_SOCKET = 21002;
	public static final int FAILED_USING_DEVICE = 22001;
	public static final int SUCCEEDED_USING_DEVICE = 22002;
	public static final int READ_FROM_DEVICE = 22003;
	
	private static ArrayList<Handler> listeners = new ArrayList<Handler>();
	
	public void registerListener(Handler listener) {
		if (!listeners.contains(listener)) listeners.add(listener);
	}
	
	public void unregisterListener(Handler listener) {
		listeners.remove(listener);
	}
	
	private void notifyListeners(Message msg) {
		for (Handler listener : listeners) listener.handleMessage(msg); 
	}
	

	// ----- Device Connect ----------------------------------------------------

	public void connectDevice() {
		if (selectedDeviceMAC == null) return;
		
		if (!connecting.tryLock()) return;
		
		adapter.cancelDiscovery();
		disconnectDevice();

		BluetoothDevice device = adapter.getRemoteDevice(selectedDeviceMAC);

		deviceConnector = new ConnectThread(device);
		deviceConnector.start();
	}

	public void disconnectDevice() {
		unUseDevice();

		if (deviceConnector != null) deviceConnector.cancel();
		deviceConnector = null;
	}

	private ConnectThread deviceConnector = null;

	private class ConnectThread extends Thread {

		private final UUID service = Utils.Serial;

		private BluetoothSocket socket = null;
		private BluetoothDevice device;

		public ConnectThread(BluetoothDevice device) {
			this.device = device;
		}

		public void run() {
			connectHandler.obtainMessage(CONNECTING_DEVICE).sendToTarget();

			try {
				socket = device.createRfcommSocketToServiceRecord(service);
			} catch (IOException e) {
				Log.e("BlueBus", "ConnectThread.ctor", e);
			}

			if (socket != null)
				try {
					socket.connect();
				} catch (IOException connectException) {
					Log.e("BlueBus", "ConnectThread.run", connectException);
					try {
						socket.close();
					} catch (IOException closeException) {}
					socket = null;
				}

			if (socket == null) if (connecting.isHeldByCurrentThread()) connecting.unlock();
			
			connectHandler.obtainMessage(MANAGE_CONNECTED_SOCKET, socket).sendToTarget();
		}

		public void cancel() {
			if (socket != null)
				try {
					socket.close();
				} catch (IOException e) {}
		}
	}

	private Handler connectHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MANAGE_CONNECTED_SOCKET:
					BluetoothSocket socket = (BluetoothSocket) msg.obj;
					if (socket != null) useDevice(socket);
					// fall through
				case CONNECTING_DEVICE:
					notifyListeners(msg);
					break;
			}
		}
	};

	
	// ----- Use connected device ----------------------------------------------

	protected void useDevice(BluetoothSocket socket) {
		unUseDevice();

		deviceUser = new UseThread(socket);
		deviceUser.start();
	}

	private void unUseDevice() {
		if (deviceUser != null)
			deviceUser.cancel();
		deviceUser = null;
	}

	private UseThread deviceUser = null;

	private class UseThread extends Thread {
		private final BluetoothSocket socket;
		private InputStream mmInStream = null;
		private OutputStream mmOutStream = null;

		public UseThread(BluetoothSocket socket) {
			this.socket = socket;

			try {
				mmInStream = socket.getInputStream();
				mmOutStream = socket.getOutputStream();
			} catch (IOException e) {
				Log.e("BlueBus", "UseThread.ctor", e);
				useHandler.obtainMessage(FAILED_USING_DEVICE).sendToTarget();
			}
			
			connecting.unlock();
		}

		public void run() {
			if (mmInStream == null) return;

			useHandler.obtainMessage(SUCCEEDED_USING_DEVICE).sendToTarget();

			byte[] buffer = new byte[1024];
			int bytes;

			while (true) {
				try {
					bytes = mmInStream.read(buffer);
					if (bytes > 0) useHandler.obtainMessage(READ_FROM_DEVICE, bytes, -1, buffer).sendToTarget();
				} catch (IOException e) {
					Log.e("BlueBus", "UseThread.run", e);
					break;
				}
			}
		}

		public void write(byte[] bytes) {
			if (mmOutStream != null)
				try {
					mmOutStream.write(bytes);
					mmOutStream.write(checksum(bytes, bytes.length));
					mmOutStream.flush();
				} catch (IOException e) {
					Log.e("BlueBus", "UseThread.write", e);
				}
		}

		public void cancel() {
			try {
				socket.close();
			} catch (IOException e) {}
		}
	}
	
	private byte checksum(byte[] buffer, int len) {
		int r = 1;
		for (int i = 0; i < len; ++i) r = un(un(7 * r) + un(buffer[i]));
		return (byte)r;
	}
	
	private int un(int x) {
		return x & 0xFF;
	}
	
	public void sendToDevice(String message) {
		sendToDevice(message.getBytes());
	}
	
	public void sendToDevice(byte[] message) {
		if (deviceUser == null) return;
		
		deviceUser.write(message);
	}
	
	private static byte incoming[] = new byte[1024];
	private static int incomingLen = 0;

	private Handler useHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case FAILED_USING_DEVICE:		// fall through
				case SUCCEEDED_USING_DEVICE:	// fall through
					notifyListeners(msg);
					break;
					
				case READ_FROM_DEVICE:
					int bytes = msg.arg1;
					byte[] buffer = ((byte[])msg.obj).clone();
					String text = Arrays.toString(buffer);//new String(buffer, 0, bytes);
					synchronized (incoming) {
						Log.i("BlueBus", String.format("BlueBridge.useHandler.handleMessage.READ_FROM_DEVICE: incoming=%s, bytes=%d, text=%s", incoming, bytes, text));
						for (int i = 0; i < bytes; ++i) {
							byte c = buffer[i];
							if (incomingLen == 3) {
								Log.i("BlueBus", String.format("BlueBridge.useHandler.handleMessage.READ_FROM_DEVICE: c=%d", c));
								byte cs = checksum(incoming, incomingLen);
								if (c == cs) {
									byte[] b = new byte[incomingLen];
									for (int j = incomingLen - 1; j >= 0; --j) b[j] = incoming[j];
									msg.obj = b;
									msg.arg1 = incomingLen;
									notifyListeners(msg);
								} else Log.i("BlueBus", String.format("BlueBridge.useHandler.handleMessage.READ_FROM_DEVICE: incoming=%s, len=%d, checksum=%d", incoming, incomingLen, cs));
								incomingLen = 0;
							} else incoming[incomingLen++] = c;
						}
					}
					break;
					
				default:
					Log.e("BlueBus", "useHandler.handleMessage: Bad message: "+msg.what);
					break;
			}
		}
	};
}
