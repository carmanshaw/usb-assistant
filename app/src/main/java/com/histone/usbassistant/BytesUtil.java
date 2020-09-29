package com.histone.usbassistant;

import java.util.Arrays;
import java.util.Locale;

import android.util.Log;

/**
 * @author Administrator
 *
 */
public class BytesUtil {


    /**
     * 二进制字节数据转换（大写）HEX字符串（由空格分开的字符串）
     */
    public static String bytes2HexSpaceString(byte[] data)
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
            hex += " ";
            buffer.append(hex);
        }

        if( buffer.length() > 0)
            return buffer.toString().toUpperCase(Locale.getDefault());
        else return "";
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
        }

        if( buffer.length() > 0)
            return buffer.toString().toUpperCase(Locale.getDefault());
        else return "";
    }

    /**
     * 十六进制字符串转换为二进制byte数据.若不是偶数个补'0'
     */
    public static byte[] hexString2Bytes(String data)
    {
        StringBuilder buffer = new StringBuilder(data);

        byte[] result = new byte[(buffer.length() + 1) / 2];
        if ((buffer.length() & 0x01) == 1) {
            buffer.append( '0' );
        }

        for (int i = 0; i < result.length; i++) {
            result[i] = ((byte)(hex2byte(buffer.charAt(i * 2 + 1))
                    | hex2byte(buffer.charAt(i * 2)) << 4));
        }
        return result;
    }

    public static byte[] hexSpaceString2Bytes(String data){
        StringBuilder buffer = new StringBuilder(data);
        byte[] result = new byte[(buffer.length() + 1) / 3];
//        if ((buffer.length() & 0x01) == 1) {
//            buffer.append( '0' );
//        }

        for (int i = 0; i < result.length; i++) {
//            if(buffer.charAt(i) == ' '){
//                continue;
//            }
            result[i] = ((byte)(hex2byte(buffer.charAt(i * 3 + 1))
                    | hex2byte(buffer.charAt(i * 3)) << 4));
        }
        return result;
    }

    /**
     * ASC数组转换为BCD数据组，如不是偶数个字节补0x30 ， 比如0x31 0x39转换为0x19
     */
    public static byte[] asc2BCDBytes(byte[] data)
    {
        int dataLen = data.length;

        byte[] result = new byte[(dataLen + 1) / 2];
        if ((dataLen & 0x01) == 1) {
            data = Arrays.copyOf(data, dataLen + 1);
            data[dataLen] = 0x30;
        }

        for (int i = 0; i < result.length; i++) {
            result[i] = ((byte) (   asc2bcd ( data[i * 2 + 1])
                    | asc2bcd ( data[i * 2]    ) << 4))  ;
        }
        return result;
    }

    /**
     * BCD数组转换为ASC数据组 比如0x19 转换为0x31 0x39
     */
    public static byte[] bcdBytes2Asc(byte[] data)
    {
        int dataLen = data.length;

        byte[] result = new byte[dataLen * 2];
        byte  val;

        for (int i = 0; i < dataLen; i++) {
            val = (byte) (((data[i] & 0xf0) >> 4) & 0x0f);
            result[i * 2] = (byte) (val > 9 ? val + 'A' - 10 : val + '0');

            val =  (byte)(data[i] & 0x0f);
            result[i * 2 + 1] = (byte) (val > 9 ? val + 'A' - 10 : val + '0');
        }
        return result;
    }


    /*
     * asc2bcd 字节比如0x39转换为0x09 ,若不是'A'-'F' 'a' -'f' '0'-'9'范围的数据 返回0
     */
    private static byte asc2bcd(byte asc) {
        byte bcd;

        if ((asc >= '0') && (asc <= '9'))
            bcd = (byte) (asc - '0');
        else if ((asc >= 'A') && (asc <= 'F'))
            bcd = (byte) (asc - 'A' + 10);
        else if ((asc >= 'a') && (asc <= 'f'))
            bcd = (byte) (asc - 'a' + 10);
        else
            bcd = 0x00;
        return bcd;
    }

    /**
     * 若不是'A'-'F' 'a' -'f' '0'-'9'范围的数据 返回0
     */
    public static byte hex2byte(char hex)
    {
        if ((hex <= '9') && (hex >= '0')) {
            return (byte)(hex - '0');
        }

        if ((hex <= 'f') && (hex >= 'a')) {
            return (byte)(hex - 'a' + 10);
        }

        if ((hex <= 'F') && (hex >= 'A')) {
            return (byte)(hex - 'A' + 10);
        }

        return 0;
    }

    /**
     * 截取子数组元素
     * @param data
     * @param offset - 偏移位置。0~data.length
     * @param len  为负数表示正常范围的最大长度
     * @return 子数组
     */
    public static byte[] subBytes(byte[] data, int offset, int len)
    {
        if ((offset < 0) || (data.length <= offset)) {
            return new byte[0]; //null;
        }

        if ((len < 0) || (data.length < offset + len)) {
            len = data.length - offset;
        }

        byte[] ret = new byte[len];

        System.arraycopy(data, offset, ret, 0, len);
        return ret;
    }

    /**
     * 依据bytes数组空间的长度，连接多个bytes数组，如某个数组为null,则略过
     */
    public static byte[] mergeBytes(byte[]... data)
    {
        int len = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == null) {
                //throw new IllegalArgumentException("");
            }
            else
            {
                len += data[i].length;
            }
        }

        byte[] newData = new byte[len];
        len = 0;
        byte[][] arrayOfByte = data;
        int j = data.length;
        for (int i = 0; i < j; i++) {
            byte[] d = arrayOfByte[i];

            if( d != null)
            {
                System.arraycopy(d, 0, newData, len, d.length);
                len += d.length;
            }
        }
        return newData;
    }

    /**
     * @param need4bytes 必须要求4个字节
     * @return int型数据
     */
    public static int bytes2Int(byte[] need4bytes)
    {
        byte []bytes = subBytes(need4bytes, 0, 4);
        int lastIndex = bytes.length - 1;
        int result = 0;
        for (int i = 0; i < bytes.length; i++) {
            result |= (bytes[i] & 0xFF) << ((lastIndex - i) << 3);
        }

        return result;
    }

    /**
     * int转换为字节数据
     * @param intValue
     * @return 高位在前  4个字节数据
     */
    public static byte[] int2Bytes(int intValue)
    {
        byte[] bytes = new byte[4];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = ((byte)(intValue >> ((3 - i) << 3) & 0xFF));
        }
        return bytes;
    }

    /**
     * int转换为字节数据,高位在前  2个字节数据
     * @param shortValue
     * @return 字节数组
     */
    public static byte[] short2Bytes(short shortValue)
    {
        byte[] bytes = new byte[2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = ((byte)(shortValue >> ((1 - i) << 3) & 0xFF));
        }
        return bytes;
    }

    /**
     * @param need2bytes 2个字节
     * @return short型数据
     */
    public static short bytes2Short(byte[] need2bytes)
    {
        byte []bytes = subBytes(need2bytes, 0, 2);

        int lastIndex = bytes.length - 1;
        short result = 0;
        for (int i = 0; i < bytes.length; i++) {
            result |= (bytes[i] & 0xFF) << ((lastIndex - i) << 3);
        }

        return result;
    }

    /**
     * ASC数组转换为BCD数据组，如不是偶数个字节补0x46即'F'
     * @param asc
     * @return BCD数据组
     */
    public static byte[] asc2BCDPaddingF(byte[] asc)
    {
        byte[] tempbuf = null;
        int len = asc.length;
        byte[] pbuf=new byte[len + 1];

        if(len % 2 != 0){
            pbuf[len] = 0x46; //'F'
            System.arraycopy(asc, 0, pbuf, 0, len);
            tempbuf = BytesUtil.asc2BCDBytes(pbuf);
        }
        else{
            tempbuf = BytesUtil.asc2BCDBytes(asc);
        }
        return tempbuf;
    }

    //显示指定长度的数组信息
    public static void showNumByteInfo(String strNotice,byte [] byteInfo,int len){
        String strDisp="";
        byte[] temp = new byte[len];
        Log.i("carman", strNotice);
        System.arraycopy(byteInfo, 0, temp, 0, len);
        for(int i=0;i<len;i++){
            strDisp=strDisp+String.format("%2X", temp[i]).replace(" ","0")+" ";
        }
        Log.e("carman", strDisp);
    }

}

