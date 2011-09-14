package com.roysharon.bluebus;

import com.roysharon.bluebus.R;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TabHost;


public class BlueBusActivity extends TabActivity {
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.main);

        TabHost tabHost = getTabHost();

        Intent intent = new Intent().setClass(this, BlueBridgeActivity.class);
        TabHost.TabSpec spec = tabHost.newTabSpec("Bluetooth").setIndicator("Bluetooth").setContent(intent);
        tabHost.addTab(spec);

        intent = new Intent().setClass(this, TerminalActivity.class);
        spec = tabHost.newTabSpec("Terminal").setIndicator("Terminal").setContent(intent);
        tabHost.addTab(spec);

        intent = new Intent().setClass(this, DashboardActivity.class);
        spec = tabHost.newTabSpec("Dashboard").setIndicator("Dashboard").setContent(intent);
        tabHost.addTab(spec);

        tabHost.setCurrentTab(2);
    }
}