package com.xiaoxiaoming.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener {

    private  Button startOrStopBluetooth;
    private  Button discoverBluetooth;
    private  Button getDiscoverEnable;
    private  ListView bondDevices,newDevices;
    private List<String>bondDevicesList,newDevicesList;
    private Button sendMsg;
    private BluetoothSocket msgSocket =null;
    private TextView nowDevice,inMsg;
    private EditText outMsg;
    private ClientThread mClientThread=null;
    private LinearLayout canHideLinerLayout;

    private BluetoothAdapter mBluetoothAdapter;

    //服务器端和客户端以及数据传输线程
    private ServiceThread mServiceThread=null;
    private ConnectedThread mConnectedTread=null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bondDevicesList=new ArrayList<String>();
        newDevicesList=new ArrayList<String>();


        startOrStopBluetooth= (Button) findViewById(R.id.startOrStopBluetooth);
        discoverBluetooth= (Button) findViewById(R.id.discoverBluetooth);
        getDiscoverEnable= (Button) findViewById(R.id.discoverEnable);
        bondDevices= (ListView) findViewById(R.id.bond_devices);
        newDevices= (ListView) findViewById(R.id.new_devices);
        sendMsg= (Button) findViewById(R.id.send_msg);
        nowDevice= (TextView) findViewById(R.id.now_device);
        inMsg= (TextView) findViewById(R.id.in_msg);
        outMsg= (EditText) findViewById(R.id.out_msg);
        canHideLinerLayout= (LinearLayout) findViewById(R.id.canHide);

        startOrStopBluetooth.setOnClickListener(this);
        discoverBluetooth.setOnClickListener(this);
        getDiscoverEnable.setOnClickListener(this);
        sendMsg.setOnClickListener(this);

        //得到蓝牙Adapter;
        mBluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
        //设置  开启/关闭  按钮初始状态
        if(mBluetoothAdapter.isEnabled()) {
            startOrStopBluetooth.setText("重新连接");

        }

        //刷新已绑定的设备
        bondListViewUpdate();

        //搜索设备以及设备搜索完成时的广播
        IntentFilter filter=new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver,filter);

        filter=new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver,filter);

        filter=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);



    }
    //刷新绑定设备列表
    private void bondListViewUpdate(){
        bondDevicesList.clear();
        Set<BluetoothDevice>pairedDevices=mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size()>0){
            for(BluetoothDevice device:pairedDevices)
                bondDevicesList.add(device.getName()+":"+device.getAddress());
        }

        bondDevices.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,bondDevicesList));

        bondDevices.setOnItemClickListener(this);
}
    //刷新新设备列表
    private  void newListViewUpdate(){

        newDevices.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,newDevicesList));
      newDevices.setOnItemClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.startOrStopBluetooth:
                bluetoothStart(v);
                break;
            case R.id.discoverBluetooth:

                bluetoothDiscover(v);
                break;
            case R.id.discoverEnable:
                enableDiscover(v);
                break;
            case R.id.send_msg:
                if(mConnectedTread ==null){
                    Toast.makeText(MainActivity.this,"必须先建立一个连接！",Toast.LENGTH_SHORT).show();
                }
                else{
                    String tmp= String.valueOf(outMsg.getText());
                    mConnectedTread.write(tmp.getBytes());
                    inMsg.append("我 >> " + tmp + "\n");
                    outMsg.setText("");
                }
                break;

        }
    }
//使本机可以被搜索
    private void enableDiscover(View v) {
        Intent intent=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300);
        startActivity(intent);
        //因为在蓝牙默认关闭状态，此动作也会开启蓝牙
        startOrStopBluetooth.setText("重新连接");
    }
//搜索蓝牙设备
    //搜索蓝牙必备的广播
    private final BroadcastReceiver mReceiver=new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
            dealWithBluetoothState(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,0));

        }else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                newDevicesList.add(device.getName() + ":" + device.getAddress());
                newListViewUpdate();
            }
        }
        else{
            setTitle("搜索完成");
            discoverBluetooth.setText("搜索设备");
        }
    }
};
    //搜索周围设备
    private void bluetoothDiscover(View v) {
    if(mBluetoothAdapter.isEnabled()){
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        } else {
            newDevicesList.clear();
            newListViewUpdate();
            mBluetoothAdapter.startDiscovery();
            setTitle("搜索中...");
            discoverBluetooth.setText("停止搜索");
        }
    }
        else Toast.makeText(MainActivity.this,"搜索设备需要先开启蓝牙",Toast.LENGTH_SHORT).show();
    }
//开启或者重启服务端
    private void bluetoothStart(View v) {
        if(!mBluetoothAdapter.isEnabled()){
            Intent intent=new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, 1);

        }
        else{
            if(mConnectedTread!=null)
            {
                mConnectedTread.cancel();
                }

            if(mServiceThread!=null)
                mServiceThread.cancel();



            mServiceThread=new ServiceThread();
            mServiceThread.start();

            canHideLinerLayout.setVisibility(View.VISIBLE);
            nowDevice.setText("");


        }
    }

//监听点击设备列表事件
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String s;
        if (mBluetoothAdapter.isEnabled()) {
            if(parent.getId()==R.id.new_devices){
                 s=newDevicesList.get(position);
            }
            else{
                s=bondDevicesList.get(position);
            }
            String address=s.substring(s.indexOf(":")+1).trim();

            if(mClientThread!=null)mClientThread.cancel();

            mClientThread=new ClientThread(mBluetoothAdapter.getRemoteDevice(address));
            mClientThread.start();

    }
        else {
            Toast.makeText(MainActivity.this,"请先开启蓝牙",Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    protected void onDestroy() {
        //unregister mReservice
        unregisterReceiver(mReceiver);

        //关闭蓝牙
        mBluetoothAdapter.disable();
        super.onDestroy();
    }

 //数据交互用到的变量
    private final String NAME="my_bluetooth";
    private final UUID MY_UUID=UUID.fromString("12345678-1234-1234-1234-123456789771");

    //服务器端线程
    private  class ServiceThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public ServiceThread() {
            System.out.println("服务器开启");
            BluetoothServerSocket tmp = null;

            try {
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmServerSocket = tmp;

        }

        @Override
        public void run() {
            while (true) {
                try {
                    msgSocket = mmServerSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    try {
                        mmServerSocket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    return;
                }
                if (msgSocket != null) {
                    try {
                        mmServerSocket.close();
                             //使用。。。
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    BluetoothDevice mDevice=msgSocket.getRemoteDevice();
                    String s=mDevice.getName()+":"+mDevice.getAddress();
                    mHandler.obtainMessage(1,-1,-1,s).sendToTarget();


                    mConnectedTread=new ConnectedThread();
                    mConnectedTread.start();
                    return;


                }
            }
        }
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("服务器关闭");
        }
    }
    //客户端线程
    private class ClientThread extends Thread{
        private final BluetoothDevice mDevice;

        public ClientThread(BluetoothDevice device){
            mDevice=device;
            BluetoothSocket socket=null;
            try {
                socket=mDevice.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            msgSocket =socket;
        }

        @Override
        public void run() {
          mBluetoothAdapter.cancelDiscovery();

            try {
                msgSocket.connect();
            } catch (IOException e) {
                e.printStackTrace();
                try {
                   msgSocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

                return;
            }
            //do somethings...
            String s=mDevice.getName()+":"+mDevice.getAddress();
            mHandler.obtainMessage(1,-1,-1,s).sendToTarget();


            mConnectedTread=new ConnectedThread();
            mConnectedTread.start();

        }

        public  void cancel()  {
            try {
                msgSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
    //传输数据线程
    private class ConnectedThread extends Thread{
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ConnectedThread(){
            InputStream tmpIn=null;
            OutputStream tmpOut=null;

            try {
                tmpIn=msgSocket.getInputStream();
                tmpOut=msgSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream=tmpIn;
            outputStream=tmpOut;
        }

        @Override
        public void run() {
           byte[]buffer=new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    String tmp=new String(buffer,0,bytes);
                    mHandler.obtainMessage(2, -1, -1, tmp).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }

        public void write(byte[]bytes)  {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public  void cancel() {
            try {
                msgSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    //监听蓝牙开关状态改变
  private void dealWithBluetoothState(int now){
      //蓝牙开启完成
    if(now==BluetoothAdapter.STATE_ON)
    {
        bondListViewUpdate();
        startOrStopBluetooth.setText("重新连接");
        mServiceThread= new ServiceThread();
        mServiceThread.start();

    }
      //蓝牙关闭完成
      if(now==BluetoothAdapter.STATE_OFF){
          startOrStopBluetooth.setText("开启蓝牙");
          if(mServiceThread!=null)mServiceThread.cancel();
          if(mConnectedTread!=null)mConnectedTread.cancel();
      }
  }


    private final Handler mHandler=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case 1: nowDevice.setText((String)msg.obj);canHideLinerLayout.setVisibility(View.INVISIBLE);break;
                case 2:
                    inMsg.append(msgSocket.getRemoteDevice().getName() + " >> " + ((String)msg.obj)+"\n");

                    break;
            }

        }
    };


}
