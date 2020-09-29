package com.histone.usbassistant;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

public class Usb{

    private static final String TAG = "Usb";
    private final static String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    public static final int SUCCESS = 0;
    public static final int ERROR = -1;

    private Context mContext;
    private UsbManager mUsbManager;
//    private UsbDevice mDevice = null;
    private UsbDeviceConnection mConnection = null;
    private UsbEndpoint mEndpointIn = null;
    private UsbEndpoint mEndpointOut = null;
    private PendingIntent mPermissionIntent;

    public Usb(Context context){
        mContext = context;
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(
                ACTION_USB_PERMISSION), 0);
    }

    public HashMap<String, UsbDevice> getUsbList(){

        return this.mUsbManager.getDeviceList();
//        // 得到usb设备列表的迭代器
//        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
//        while (deviceIterator.hasNext()) {
//            UsbDevice mDevice = deviceIterator.next();
//            Log.i(TAG, "UsbDevice (pid,vid): (" + mDevice.getProductId() + "),(" + mDevice.getVendorId() + ")");
//        }
    }

    public void getUsbPermission(UsbDevice mDevice){
        mUsbManager.requestPermission(mDevice, mPermissionIntent);
    }

    public int openUsb(UsbDevice mDevice){
        mConnection = mUsbManager.openDevice(mDevice);
        if (this.mConnection == null) {
            Log.i(TAG,"mConnection is null");
            return ERROR;
        }

        if (mDevice.getInterfaceCount() == 0) {
            closeUsb();
            Log.i(TAG,"getInterfaceCount is 0");
            return ERROR;
        }
        for (int n = 0; n < 1/*interfaceCount*/; n++) {
            UsbInterface intf = mDevice.getInterface(n);
            mConnection.claimInterface(intf, true/*forceClaim*/);

            int endpoitCount = intf.getEndpointCount();
            if (endpoitCount == 0) {
                closeUsb();
                Log.i(TAG,"EndpointCount is 0");
                return ERROR;
            }
            for (int m = 0; m < endpoitCount; m++) {
                UsbEndpoint endpoint = intf.getEndpoint(m);
                if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK/*2*/) {
                    if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT/*0*/) {
                        System.out.println(" out n=" + n + "  m=" + m);
                        mEndpointOut = endpoint;
                    } else if (endpoint.getDirection() == UsbConstants.USB_DIR_IN/*128*/) {
                        System.out.println("in n=" + n + "  m=" + m);
                        mEndpointIn = endpoint;
                    }
                }
            }
        }

        if ((mEndpointOut == null) || (mEndpointIn == null)) {
            closeUsb();
            Log.i(TAG,"EndpointIn or EndpointOut is null");
            return ERROR;
        }
        return SUCCESS;
    }

    public boolean isConnected(){
        return mConnection == null? false:true;
    }

    public void closeUsb() {
        if (mConnection != null) {
            mConnection.close();
        }
        mConnection = null;
        Log.d(TAG, "usb closeDevice");

        this.mEndpointIn = null;
        this.mEndpointOut = null;
    }

    public int writeBuffer(final byte[] writeBuffer, int offsetSize, int nBytesToWrite,
                                        int writeTimeOut) {
//        addTxtToFileWrite(bytes2HexString(writeBuffer));
        int writeSegmentsSize = 16384;//16K
        int writeStartSize = 0;
        byte[] writeSegmentsBuffer = new byte[writeSegmentsSize];
        byte[] writeTrueBuffer = new byte[nBytesToWrite];
        System.arraycopy(writeBuffer, offsetSize, writeTrueBuffer, 0, nBytesToWrite);
        int allTransferSize = 0;
        int bulkTransferSize = 0;
        long start = System.currentTimeMillis();

        if (nBytesToWrite > writeSegmentsSize) {
            //整包数据
            for (int i = 0; i < nBytesToWrite / writeSegmentsSize; i++) {

                System.arraycopy(writeTrueBuffer, writeStartSize, writeSegmentsBuffer, 0,
                        writeSegmentsSize);
                bulkTransferSize = this.mConnection.bulkTransfer(this.mEndpointOut,
                        writeSegmentsBuffer, 0, writeSegmentsSize, writeTimeOut);

                if (bulkTransferSize == -1) {
                    Log.e(TAG, "writeBuffer: 第" + i + "包发送失败");
                    return ERROR;
                } else {
                    writeStartSize += writeSegmentsSize;
                    allTransferSize += bulkTransferSize;
                }
            }

            Log.i(TAG, "已经发送" + allTransferSize + "，共" + (nBytesToWrite / writeSegmentsSize) + "整包");
            //最后一小包数据
            writeSegmentsBuffer = new byte[nBytesToWrite
                    - allTransferSize];
            System.arraycopy(writeTrueBuffer, allTransferSize, writeSegmentsBuffer, 0, nBytesToWrite
                    - allTransferSize);
            bulkTransferSize = this.mConnection.bulkTransfer(this.mEndpointOut,
                    writeSegmentsBuffer, 0, nBytesToWrite - allTransferSize, writeTimeOut);
            Log.i(TAG, "最后一小包发送：" + bulkTransferSize);
            allTransferSize += bulkTransferSize;

            Log.d(TAG, "nBytesToWrite： " + nBytesToWrite + "send allTransferSize：" + allTransferSize);
            if (allTransferSize == nBytesToWrite)
                return SUCCESS;
        } else {
            bulkTransferSize = this.mConnection.bulkTransfer(this.mEndpointOut, writeBuffer,
                    offsetSize, nBytesToWrite, writeTimeOut);
            Log.d(TAG, "send buklTransferSize == " + bulkTransferSize);
            if (bulkTransferSize == nBytesToWrite) {
                // 打印所有write的数据
//                BytesUtil.showNumByteInfo("usb writeBuffer", writeBuffer, writeBuffer.length);
                return SUCCESS;
            }
        }
        Log.e(TAG, "writeBuffer failed ： bulkTransferSize == " + bulkTransferSize + "\n"
                + "nBytesToWrite == " + nBytesToWrite);
        return ERROR;
    }

    /**
     * 二进制字节数据转换（大写）HEX字符串,
     */
    public static String bytes2HexString(byte[] data)
    {
        StringBuilder buffer = new StringBuilder();
        byte[] arrayOfByte = data;
        for (int i = 0; i < data.length; i++)
        {
            byte b = arrayOfByte[i];
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                buffer.append('0');
            }
            buffer.append(hex);
            buffer.append(' ');
        }

        if( buffer.length() > 0)
            return buffer.toString().toUpperCase(Locale.getDefault());
        else return "";
    }

    private int loopNo = 0;
    private void addTxtToFileWrite(String content){
        FileWriter writer = null;
        try {
            //FileWriter(file, true),第二个参数为true是追加内容，false是覆盖
            Log.i("carman", "loopNo:" + loopNo);
            writer = new FileWriter(new File("/sdcard/add.txt"), true);
            loopNo++;
            writer.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(writer != null){
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public int readBuffer(byte[] readBuffer, int offsetSize, int nBytesToRead, int readTimeOut) {
        if (this.mConnection == null || this.mEndpointIn == null) {
            Log.e(TAG, "this.mConnection==null || this.mEndpointIn==null!!!");
            return ERROR;
        }
        long currentTime = System.currentTimeMillis();
        int offSet = 0;
        int size = 0;
        while (System.currentTimeMillis() - currentTime < readTimeOut) {
            if(mConnection == null){
                return offSet;
            }
            size = this.mConnection.bulkTransfer(mEndpointIn, readBuffer, offSet, nBytesToRead
                    - offSet, readTimeOut);

            if (size > 0) {
                Log.w(TAG, "readBuffer: size="+size );
                offSet += size;
            }
            if (offSet >= nBytesToRead) {
                break;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
            }
        }
        if(offSet != 0)
        Log.i(TAG, "read  last size =" + size + "whole size =" + offSet);
        return offSet;
    }
}
