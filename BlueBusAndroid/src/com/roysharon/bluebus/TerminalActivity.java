package com.roysharon.bluebus;

import java.util.ArrayList;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;


public class TerminalActivity extends Activity {

	private BlueBridge blueBridge;
	private ArrayList<TerminalMessage> history;
	private TerminalAdapter adapter;
	private ListView terminalList;
	private EditText messageField;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        blueBridge = new BlueBridge(this);
        blueBridge.registerListener(useHandler);
        
        history = new ArrayList<TerminalMessage>();
        adapter = new TerminalAdapter();
        setContentView(createTerminalView());
        
 //       messageField.requestFocus();
    }
    
	private static final int VIEW_ID = 1000;
	private static final int LIST_ID = 1001;
	private static final int PANEL_ID = 1002;
	private static final int MESSAGE_ID = 1003;
	private static final int SEND_ID = 1004;

	private LinearLayout createTerminalView() {
		LinearLayout view = new LinearLayout(this);
		view.setId(VIEW_ID);
		view.setOrientation(LinearLayout.VERTICAL);
		view.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

		terminalList = new ListView(this);
		terminalList.setId(LIST_ID);
		terminalList.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_NORMAL);
		terminalList.setAdapter(adapter);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		params.weight = 1;
		view.addView(terminalList, params);

		view.addView(createEntryPanel());

		return view;
	}

	private View createEntryPanel() {
		LinearLayout panel = new LinearLayout(this);
		panel.setId(PANEL_ID);
		panel.setOrientation(LinearLayout.HORIZONTAL);

		messageField = new EditText(this);
		messageField.setId(MESSAGE_ID);
		messageField.setInputType(InputType.TYPE_CLASS_TEXT);
		messageField.setImeOptions(EditorInfo.IME_ACTION_SEND);
		messageField.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEND) {
					sendMessage();
					return true;
				} else
					return false;
			}
		});
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		params.weight = 1;
		panel.addView(messageField, params);

		Button send = new Button(this);
		send.setId(SEND_ID);
		send.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				sendMessage();
			}
		});
		params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT);
		send.setText("Send");
		panel.addView(send, params);

		return panel;
	}

	protected void sendMessage() {
		String message = messageField.getText().toString();
		messageField.getText().clear();

		addMessage(message, true);
		
		blueBridge.sendToDevice(message);
	}
	
	protected void addMessage(String text, boolean sent) {
		history.add(new TerminalMessage(text, sent));
		adapter.notifyDataSetChanged();
	}

	public class TerminalMessage {
		public String text;
		public boolean sent;
		
		public TerminalMessage(String text, boolean sent) {
			this.text = text;
			this.sent = sent;
		}
	}
	
	public class TerminalAdapter extends BaseAdapter {

		public int getCount() {
			return history.size();
		}

		public Object getItem(int position) {
			return history.get(position).text;
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			TextView view;
			if (convertView == null) {
				view = new TextView(TerminalActivity.this);
//				LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
//				view.setLayoutParams(params);
			} else view = (TextView)convertView;
			
			
			TerminalMessage msg = history.get(position);
			view.setText(msg.text);
			view.setTextColor(msg.sent ? Color.argb(255, 255, 255, 0) : Color.GREEN);
			
			return view;
		}

	}

	private Handler useHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case BlueBridge.READ_FROM_DEVICE:
					int bytes = msg.arg1;
					byte[] buffer = (byte[]) msg.obj;
					String text = new String(buffer, 0, bytes);
					Log.i("BlueBus", String.format("TerminalActivity.useHandler.handleMessage.READ_FROM_DEVICE: bytes=%d, text=%s", bytes, text));
					addMessage(text, false);
					break;
			}
		}
		
	};
}
