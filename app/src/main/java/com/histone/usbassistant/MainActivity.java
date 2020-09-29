package com.histone.usbassistant;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.accessibilityservice.AccessibilityService;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.thl.filechooser.FileChooser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity implements UsbDeviceReceiver.HistoneUsbEvent{

    private static final String TAG = "MainActivity";
    //已连接
    private static final int CONNECTED = 0;

    //已断开
    private static final int DISCONNECT = 1;

    //接收数据更新
    private static final int RECEIVE = 2;

    //发送数据区更新
    private static final int SEND = 3;

    //发送中
    private static final int SENDING = 4;

    //更新设备列表信息
    private static final int REFRESH_ADAPTER = 5;

    private Button btn_connect,btn_clearSBuf,btn_clearRBuf,btn_inputFile,btn_send;
    private CheckBox cb_isRTxt,cb_isSTxt,cb_isLoop,cb_isFileSend;
    private EditText et_receive,et_send,et_inputFile,et_total,et_interval;
    private Spinner sp_deviceList;

    //设备列表显示list和adpter
    ArrayList<String> spDeviceList = new ArrayList<String>();
    ArrayAdapter<String> adapter;

    //存储设备pidvid key和UsbDevces对象，便于open操作
    private HashMap<String, UsbDevice> toinitDeviceList = new HashMap<>();

    //当前选择的设备名
    private String selectDeviceDescps = "";
    private boolean isRTxt,isSTxt,isLoop,isFileSend,isConnected,isPermissionGranted;

    private Usb usb ;

    //数据收发处理线程
    private ReceiveThread receiveThread;
    private SendTaskThread sendTaskThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //默认不弹软键盘
        getWindow().setSoftInputMode( WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        usb = new Usb(this);

        initUI();

        //设置usb拔插事件监听
        UsbDeviceReceiver.getInstance().setHistoneUsbEvent(this);

        //注册USB事件广播
        UsbDeviceReceiver.getInstance().registerUsbReceiver(this);

        //请求读写存储权限
        requestPermissins(new PermissionUtils.OnPermissionListener() {
            @Override
            public void onPermissionGranted() {
                isPermissionGranted = true;
            }

            @Override
            public void onPermissionDenied(String[] deniedPermissions) {
                Toast.makeText(MainActivity.this, "未获取到存储权限", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //取消USB事件广播
        UsbDeviceReceiver.getInstance().unregisterReceiver(this);
    }

    public void onBtnClick(View v){
        switch (v.getId()){

            //连接
            case R.id.btn_init:
                UsbDevice device = toinitDeviceList.get(selectDeviceDescps);
                if(btn_connect.getText().toString().contains(getString(R.string.disconnect))){
                    usb.closeUsb();
                    myHandler.sendEmptyMessage(DISCONNECT);
                    receiveThread.exit();
                }else{
                    if(device != null) {
                        usb.getUsbPermission(device);
                    }
                }
                break;
            //清接收区
            case R.id.btn_clear_receive:
                et_receive.setText("");
                break;

            //清发送区
            case R.id.btn_clear_send:
                et_send.setText("");
                break;

            //载入文件
            case R.id.btn_inputFile:
                if(!isPermissionGranted){
                    Toast.makeText(MainActivity.this, "未获取到存储权限", Toast.LENGTH_SHORT).show();
                    return;
                }
                FileChooser fileChooser = new FileChooser(MainActivity.this, new FileChooser.FileChoosenListener() {
                    @Override
                    public void onFileChoosen(String filePath) {
                        ((EditText) findViewById(R.id.et_filePath)).setText(filePath);
                        ((EditText) findViewById(R.id.et_send)).setText(readFileContent(filePath));
                    }
                });
                fileChooser.setBackIconRes(R.drawable.icon_arrow);
                fileChooser.setTitle("选择文件路径");
                fileChooser.setDoneText("确定");
                fileChooser.setThemeColor(R.color.colorAccent);
                fileChooser.open();
                break;

            //发送
            case R.id.btn_send:
                if(!isConnected){
                    Toast.makeText(MainActivity.this,getString(R.string.disconnect),Toast.LENGTH_SHORT).show();
                    return;
                }
                String btnTxt = btn_send.getText().toString();
                if(btnTxt.contains(getString(R.string.sending))){
//                    btnTxt.setText("SEND");
                    sendTaskThread.exit();
                    sendTaskThread = null;
                }else{
                    int intvalTime = 0;
                    int totalNum = 0;
                    if(isLoop){
                        intvalTime = getEditTextNum(et_interval);
                        totalNum = getEditTextNum(et_total);
                    }else{
                        totalNum = 1;
                    }

                    sendTaskThread = new SendTaskThread(et_send.getText().toString(),totalNum,intvalTime);
                    sendTaskThread.start();
                }
                break;

                default:break;
        }
    }

    private Handler myHandler = new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case CONNECTED:
                    isConnected = true;
                    btn_connect.setText(R.string.disconnect);
                    sp_deviceList.setEnabled(false);
                    break;
                case DISCONNECT:
//                    btn_connect.setEnabled(false);
                    btn_connect.setText(R.string.connect);
                    sp_deviceList.setEnabled(true);
                    isConnected = false;
                    break;
                case RECEIVE:
                    et_receive.append((String)msg.obj + " ");
                    break;
                case SEND:
                    et_receive.append(btn_send.getText().toString());
                    btn_send.setText(R.string.send);
                    break;
                case SENDING:
                    btn_send.setText(getString(R.string.sending) + (String)msg.obj);
                    break;
                case REFRESH_ADAPTER:
                    //添加输入的项 ,add后自动调用notifyDataSetChanged()
                    adapter.add((String)msg.obj);
                    break;
            }
        }
    };

    public class SendTaskThread extends Thread{

        //发送总次数
        private int totalSum = 0;
        //发送间隔
        private int intervalTime = 0;
        //发送内容 string 类型
        private String cont;
        //发送计数
        private int counting = 0;
        //发送失败次数
        private int errCounting = 0;

        public SendTaskThread(String c,int total,int iT){
            cont = c;
            totalSum = total;
            intervalTime = iT;
        }

        private boolean isExit = false;

        public void exit(){
            isExit = true;
        }

        @Override
        public void run() {

            //发送内容
            byte[] data = new byte[0];
            if(isSTxt){
                //文本
                try {
                    data = cont.getBytes("GB18030");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }else{
                //Hex
                data = BytesUtil.hexSpaceString2Bytes(cont);
            }
            while(!isExit){
                //发送
                int iret = usb.writeBuffer(data,0,data.length,150*1000);
                if(iret == Usb.SUCCESS){
                    counting++;
                }else{
                    errCounting++;
                }
                //更新UI计数
                sendMessage(SENDING,"(" + counting + "/" + totalSum + "/" + errCounting + ")");

                //间隔
                try {
                    Thread.sleep(intervalTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                //结束退出
                if((counting + errCounting) == totalSum){
                    break;
                }
            }
            myHandler.sendEmptyMessage(SEND);
        }
    }

    public class ReceiveThread extends Thread{

        private boolean isExit = false;

        public void exit(){
            isExit = true;
        }

        @Override
        public void run() {
//            super.run();
            while(!isExit){
                //实时接收
                byte[] data = new byte[32];
                int iret = usb.readBuffer(data,0,data.length,50);
                if(iret > 0){
                    //hex 转string并更新UI接收区显示
                    byte[] bCont = BytesUtil.subBytes(data,0,iret);
                    sendMessage(RECEIVE,BytesUtil.bytes2HexString(bCont));
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @Override
    public void attach(UsbDevice device) {
        //设备接入
        String listName = device.getProductName() + " (" + device.getProductId() + "," + device.getVendorId() + ")";
        //检查设备列表是否存在
        for(String str:spDeviceList){
            //存在则只更新toinitDeviceList中新的UsbDevice对象
            if(str.contains(listName)){
                toinitDeviceList.put(listName,device);
                return;
            }
        }

        //如果是新设备接入，则同时更新toinitDeviceList和UI列表显示内容
        toinitDeviceList.put(listName,device);
        sendMessage(REFRESH_ADAPTER,listName);
    }

    @Override
    public void detach(UsbDevice device) {
        //设备断开
        UsbDevice d = toinitDeviceList.get(selectDeviceDescps);
        //如果为当前设备断开，则执行相关断开操作
        if(device.getProductId() == d.getProductId() && device.getVendorId() == d.getVendorId()) {
            if(receiveThread != null) {
                receiveThread.exit();
            }
            myHandler.sendEmptyMessage(DISCONNECT);
            usb.closeUsb();
        }
        //非当前使用设备，不做处理
    }

    @Override
    public void permissions(UsbDevice device) {
        //获取usb权限
        UsbDevice d = toinitDeviceList.get(selectDeviceDescps);
        //如果为当前设备请求，则做初始化相关操作
        if(device.getProductId() == d.getProductId() && device.getVendorId() == d.getVendorId()) {
            int iret = usb.openUsb(device);
            if(iret == Usb.ERROR){
                //失败
                Toast.makeText(MainActivity.this,getString(R.string.connect) + " error!",Toast.LENGTH_SHORT).show();
                myHandler.sendEmptyMessage(DISCONNECT);
            }else{
                //成功
                receiveThread = new ReceiveThread();
                receiveThread.start();
                myHandler.sendEmptyMessage(CONNECTED);
            }
        }
    }

    private void sendMessage(int w,Object obj){
        Message msg = Message.obtain();
        msg.what = w;
        msg.obj = obj;
        myHandler.sendMessage(msg);
    }

    private void requestPermissins(PermissionUtils.OnPermissionListener mOnPermissionListener) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            mOnPermissionListener.onPermissionGranted();
            return;
        }
        String[] permissions = { "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"};
        PermissionUtils.requestPermissions(this, 0
                , permissions, mOnPermissionListener);
    }

    /**
     * 获取EditText控件的内容并转为int
     * @param et
     * @return
     */
    private int getEditTextNum(EditText et){
        String sCont = et.getText().toString();
        if(sCont.length() == 0){
            return 0;
        }

        return Integer.parseInt(sCont);
    }

    /**
     * 初始化UI控件
     */
    private void initUI(){
        btn_connect = findViewById(R.id.btn_init);
        btn_clearSBuf = findViewById(R.id.btn_clear_send);
        btn_clearRBuf = findViewById(R.id.btn_clear_receive);
        btn_inputFile = findViewById(R.id.btn_inputFile);
        btn_send = findViewById(R.id.btn_send);
        cb_isRTxt = findViewById(R.id.cb_isrtxt);
        cb_isRTxt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isRTxt = isChecked;
            }
        });
        cb_isSTxt = findViewById(R.id.cb_isstxt);
        cb_isSTxt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isSTxt = isChecked;
            }
        });
        cb_isLoop = findViewById(R.id.cb_isTimerSend);
        cb_isLoop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isLoop = isChecked;
            }
        });

        cb_isFileSend = findViewById(R.id.cb_isFileSend);
        cb_isFileSend.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isFileSend = isChecked;
            }
        });
        et_receive = findViewById(R.id.et_receive);
        et_send = findViewById(R.id.et_send);
        et_inputFile = findViewById(R.id.et_filePath);
        et_total = findViewById(R.id.et_sendCount);
        et_interval = findViewById(R.id.et_sendIntervalTime);
        sp_deviceList = findViewById(R.id.spDeviceList);

        HashMap<String, UsbDevice> deviceList = usb.getUsbList();
        // 得到usb设备列表的迭代器
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            UsbDevice mDevice = deviceIterator.next();
            Log.i(TAG, "UsbDevice (pid,vid): (" + mDevice.getProductId() + "),(" + mDevice.getVendorId() + ")");
            String listName = mDevice.getProductName() + " (" + mDevice.getProductId() + "," + mDevice.getVendorId() + ")";
            spDeviceList.add(listName);
            toinitDeviceList.put(listName,mDevice);
        }

        //把数组导入到ArrayList中
        adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,spDeviceList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        //设置下拉菜单的风格
        sp_deviceList.setAdapter(adapter);

        sp_deviceList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectDeviceDescps = sp_deviceList.getSelectedItem().toString();
                Log.i(TAG,"selectDeviceDescps:" + selectDeviceDescps);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        //设置版本号
        ((TextView)findViewById(R.id.tv_version)).setText("Vers: " + getVersion());

    }

    /**
     * 读取文件全部内容
     * @param fileName
     * @return
     */
    public String readFileContent(String fileName) {
        File file = new File(fileName);
        BufferedReader reader = null;
        StringBuffer sbf = new StringBuffer();
        try {
            reader = new BufferedReader(new FileReader(file));
            String tempStr;
            while ((tempStr = reader.readLine()) != null) {
                sbf.append(tempStr);
            }
            reader.close();
            return sbf.toString();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
        return sbf.toString();
    }

    public String getVersion() {

        // 获取packagemanager的实例
        PackageManager packageManager = getPackageManager();
        try {
            // getPackageName()是你当前类的包名，0代表是获取版本信息
            PackageInfo packInfo = packageManager.getPackageInfo(getPackageName(), 0);
            return packInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return "unKnown";
    }
}
