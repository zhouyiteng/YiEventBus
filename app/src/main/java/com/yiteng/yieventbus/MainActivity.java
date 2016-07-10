package com.yiteng.yieventbus;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.zhouyiteng.yibus.ThreadMode;
import com.zhouyiteng.yibus.YiBus;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        YiBus.getDefault().register(this, ThreadMode.InMainThread);
    }

    public void btn_postClick(View btn) {
        YiBus.getDefault().post(new AEvent());
    }

    public void onEvent(AEvent aEvent) {
        Toast.makeText(this,aEvent.toString(),Toast.LENGTH_SHORT).show();
    }


    public void btn_postClick_InThread(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                YiBus.getDefault().post(new AEvent());
            }
        }).start();
    }
}
