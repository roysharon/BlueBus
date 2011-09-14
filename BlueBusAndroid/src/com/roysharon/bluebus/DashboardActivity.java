package com.roysharon.bluebus;

import java.util.ArrayList;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;


public class DashboardActivity extends Activity {

	private BlueBridge blueBridge;
	private ListView dashboardList;
	private DashboardAdapter adapter;
	private ArrayList<DashboardControl> dashboard, incoming, outgoing;

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        createDashboard("x,y,@a,@b,z");
        adapter = new DashboardAdapter();
        
        blueBridge = new BlueBridge(this);
        blueBridge.registerListener(useHandler);
        
        setContentView(createDashboardView());
    }

	private static final int VIEW_ID = 1000;
	private static final int DASHBOARD_ID = 1001;

	private LinearLayout createDashboardView() {
		LinearLayout view = new LinearLayout(this);
		view.setId(VIEW_ID);
		view.setOrientation(LinearLayout.VERTICAL);
		view.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

		dashboardList = new ListView(this);
		dashboardList.setId(DASHBOARD_ID);
		dashboardList.setAdapter(adapter);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		view.addView(dashboardList, params);

		return view;
	}

	protected void createDashboard(String description) {
		dashboard = new ArrayList<DashboardControl>();
		incoming = new ArrayList<DashboardControl>();
		outgoing = new ArrayList<DashboardControl>();
		
		for (String d : description.split(",")) {
			ControlType type;
			boolean isIncoming = false;
			switch (d.charAt(0)) {
				case '@': type = ControlType.Lever; break;
				default: type = ControlType.ReadHex; isIncoming = true; break;
			}
			DashboardControl control = new DashboardControl(type, d.substring(isIncoming ? 0 : 1));
			dashboard.add(control);
			if (isIncoming) incoming.add(control);
			else outgoing.add(control);
		}
	}
	
	protected void updateDashboard(byte[] buffer) {
		if (buffer.length == incoming.size()) {
			for (int i = buffer.length - 1; i >= 0; --i) incoming.get(i).value = buffer[i];
			adapter.notifyDataSetChanged();
		} else Log.e("BlueBus", String.format("DashboardActivity.updateDashboard: Buffer length %d does not correspond to incoming size %d", buffer.length, incoming.size()));
	}

	protected void sendUpdate() {
		byte[] buffer = new byte[outgoing.size()];
		for (int i = buffer.length - 1; i >= 0; --i) buffer[i] = (byte)(outgoing.get(i).value & 0xFF);
		blueBridge.sendToDevice(buffer);
	}
	
	private Handler useHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case BlueBridge.READ_FROM_DEVICE:
					int bytes = msg.arg1;
					byte[] buffer = (byte[])msg.obj;
					String text = new String(buffer, 0, bytes);
					Log.i("BlueBus", String.format("DashboardActivity.useHandler.handleMessage.READ_FROM_DEVICE: bytes=%d, text=%s", bytes, text));
					updateDashboard(buffer);
					break;
			}
		}
	};

	
	//----- Logical Controls --------------------------------------------------
	
	public enum ControlType {
		ReadHex, Lever
	}
	
	public class DashboardControl {
		public ControlType type;
		public String title;
		public int value;
		
		public DashboardControl(ControlType type, String title) {
			this.type = type;
			this.title = title;
			this.value = 0;
		}
	}

	public class DashboardAdapter extends BaseAdapter {

		@Override
		public int getViewTypeCount() {
			return 2;
		}

		@Override
		public int getItemViewType(int position) {
			switch (dashboard.get(position).type) {
				case Lever:   return 1;
				case ReadHex: // fall through
				default:      return 0;
			}
		}

		public int getCount() {
			return dashboard.size();
		}

		public Object getItem(int position) {
			return dashboard.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			DashboardControl control = dashboard.get(position);
			
			if (convertView == null) {
				switch (control.type) {
					case Lever:   convertView = new LeverControl(); break;
					case ReadHex: // fall through
					default:      convertView = new TextControl(); break;
				}
			}
			
			((Control)convertView).update(control);
			
			return convertView;
		}

		@Override
		public boolean isEnabled(int position) {
			return dashboard.get(position).type != ControlType.ReadHex;
		}
	}
	
	
	//----- Visual Controls ---------------------------------------------------
	
	public interface Control {
		void update(DashboardControl control);
	}
	
	public class TextControl extends TextView implements Control {

		public TextControl() {
			super(DashboardActivity.this);
		}
		
		public void update(DashboardControl control) {
			setText(String.format("%s: 0x%02x", control.title, control.value));
		}
	}
	
	public class LeverControl extends LinearLayout implements Control {

		private TextView label;
		private SeekBar lever;
		private DashboardControl control;
		
		public LeverControl() {
			super(DashboardActivity.this);
			
			setOrientation(HORIZONTAL);
			setPadding(0, 20, 0, 20);
			
			label = new TextView(DashboardActivity.this);
			addView(label);
			
			lever = new SeekBar(DashboardActivity.this);
			lever.setMax(255);
			lever.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				
				public void onStopTrackingTouch(SeekBar seekBar) {}
				
				public void onStartTrackingTouch(SeekBar seekBar) {}
				
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					if (fromUser) {
						control.value = progress;
						updateDisplay();
						sendUpdate();
					}
				}
			});
			LinearLayout.LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
			params.weight = 1;
			addView(lever, params);
		}
		
		public void update(DashboardControl control) {
			this.control = control;
			updateDisplay();
		}

		private void updateDisplay() {
			label.setText(String.format("%s: 0x%02x ", control.title, control.value));
			lever.setProgress(control.value);
		}
	}
}
