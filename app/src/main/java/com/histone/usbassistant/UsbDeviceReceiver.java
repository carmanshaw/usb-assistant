package com.histone.usbassistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;


import java.util.Vector;

@SuppressWarnings("unchecked")
public class UsbDeviceReceiver {

    private static final String TAG = "UsbDeviceReceiver";
    private final static String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    public UsbDeviceReceiver(){}
    private static final UsbDeviceReceiver receiver = new UsbDeviceReceiver();
    public static UsbDeviceReceiver getInstance(){
        return receiver;
    }

    private Vector vectorEvent = new Vector<HistoneUsbEvent>();

    public void setHistoneUsbEvent(HistoneUsbEvent mHistoneUsbEvent){
        if(!vectorEvent.contains(mHistoneUsbEvent)) {
            vectorEvent.add(mHistoneUsbEvent);
        }
    }

    public BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device == null) {
                return;
            }

            Log.i(TAG,"pid:" + device.getProductId() + " vid:" + device.getVendorId());
            String action = intent.getAction();
            // 获取权限结果的广播
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    Log.i(TAG, "UsbManager.EXTRA_DEVICE" + device);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        notifyEvent(0,device);
                        Log.i(TAG, "Opening usb device: " + device.getDeviceName() + "...");
                    } else {
                        Log.i(TAG, "EXTRA_PERMISSION_GRANTED false");
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.d(TAG, "Attach Broadcast");
                notifyEvent(1,device);

            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.i(TAG, "USB Detached");
                notifyEvent(2,device);
            }
        }
    };

    public void registerUsbReceiver(Context context) {

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(mUsbReceiver, filter);
//        isReceiverRegistered = true;
        Log.i(TAG, "registerUsbReceiver");
    }

    public void unregisterReceiver(Context context) {
        context.unregisterReceiver(mUsbReceiver);
    }

    public void notifyEvent(int isAttach,UsbDevice device){
        for(int i=0;i<vectorEvent.size();i++){
            HistoneUsbEvent event = (HistoneUsbEvent)vectorEvent.get(i);
            if(isAttach == 1) {
                event.attach(device);
            }else if(isAttach == 2) {
                event.detach(device);
            }else{
                event.permissions(device);
            }
        }
    }

    public interface HistoneUsbEvent{
        public void attach(UsbDevice device);
        public void detach(UsbDevice device);
        public void permissions(UsbDevice device);
    }
}
