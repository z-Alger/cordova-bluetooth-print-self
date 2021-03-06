package com.ru.cordova.printer.bluetooth;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Set;
import java.util.UUID;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Bitmap.Config;
import android.util.Xml.Encoding;
import android.util.Base64;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class BluetoothPrinter extends CordovaPlugin {

  private static final String LOG_TAG = "BluetoothPrinter";
  static BluetoothAdapter mBluetoothAdapter;
  static BluetoothSocket mmSocket;
  static BluetoothDevice mmDevice;
  OutputStream mmOutputStream;
  InputStream mmInputStream;
  Thread workerThread;
  byte[] readBuffer;
  int readBufferPosition;
  int counter;
  volatile boolean stopWorker;
  Bitmap bitmap;
  public static BluetoothService mService = null;

  public BluetoothPrinter() {
  }
  // Intent request codes
  private static final int REQUEST_CONNECT_DEVICE = 1;
  private static final int REQUEST_ENABLE_BT = 2;

  // Message types sent from the BluetoothService Handler
  public static final int MESSAGE_STATE_CHANGE = 1;
  public static final int MESSAGE_READ = 2;
  public static final int MESSAGE_WRITE = 3;
  public static final int MESSAGE_DEVICE_NAME = 4;
  public static final int MESSAGE_TOAST = 5;

  // The Handler that gets information back from the BluetoothService
  private final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MESSAGE_STATE_CHANGE:
          switch (msg.arg1) {
            case BluetoothService.STATE_CONNECTED:

              break;
            case BluetoothService.STATE_CONNECTING:

              break;
            case BluetoothService.STATE_LISTEN:
            case BluetoothService.STATE_NONE:

              break;
          }
          break;
        case MESSAGE_WRITE:
          //byte[] writeBuf = (byte[]) msg.obj;
          // construct a string from the buffer
          //String writeMessage = new String(writeBuf);
          break;
        case MESSAGE_READ:
          //byte[] readBuf = (byte[]) msg.obj;
          // construct a string from the valid bytes in the buffer
          //String readMessage = new String(readBuf, 0, msg.arg1);
          break;
        case MESSAGE_DEVICE_NAME:
          // save the connected device's name

          break;
        case MESSAGE_TOAST:

          break;
      }
    }
  };

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    if (action.equals("list")) {
      listBT(callbackContext);
      return true;
    } else if (action.equals("connect")) {
      if (!mBluetoothAdapter.isEnabled()) {
        //打开蓝牙
        //Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        //startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
      }
      if (mService==null) {
        mService = new BluetoothService(mHandler);
      }
      String name = args.getString(0);
      if (findBT(callbackContext, name)) {
        try {
          connectBT(callbackContext);
        } catch (IOException e) {
          Log.e(LOG_TAG, e.getMessage());
          e.printStackTrace();
        }
      } else {
        callbackContext.error("Bluetooth Device Not Found: " + name);
      }
      return true;
    } else if (action.equals("disconnect")) {
      try {
        disconnectBT(callbackContext);
      } catch (IOException e) {
        Log.e(LOG_TAG, e.getMessage());
        e.printStackTrace();
      }
      return true;
    } else if (action.equals("print") || action.equals("printImage")) {
      try {
        String msg = args.getString(0);
        printImage(callbackContext, msg);
      } catch (IOException e) {
        Log.e(LOG_TAG, e.getMessage());
        e.printStackTrace();
      }
      return true;
    } else if (action.equals("printText")) {
      try {
        String msg = args.getString(0);
        printText(callbackContext, msg);
      } catch (IOException e) {
        Log.e(LOG_TAG, e.getMessage());
        e.printStackTrace();
      }
      return true;
    } else if (action.equals("printPOSCommand")) {
      try {
        String msg = args.getString(0);
        printPOSCommand(callbackContext, hexStringToBytes(msg));
      } catch (IOException e) {
        Log.e(LOG_TAG, e.getMessage());
        e.printStackTrace();
      }
      return true;
    }
    return false;
  }

  //This will return the array list of paired bluetooth printers
  void listBT(CallbackContext callbackContext) {
    // BluetoothAdapter mBluetoothAdapter = null;
    String errMsg = null;
    try {
      mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

      if (mBluetoothAdapter == null) {
        errMsg = "No bluetooth adapter available";
        Log.e(LOG_TAG, errMsg);
        callbackContext.error(errMsg);
        return;
      }
      if (!mBluetoothAdapter.isEnabled()) {
        Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        this.cordova.getActivity().startActivityForResult(enableBluetooth, 0);
      }
      Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
      if (pairedDevices.size() > 0) {
        JSONArray json = new JSONArray();
        for (BluetoothDevice device : pairedDevices) {
                    /*
                     Hashtable map = new Hashtable();
                     map.put("type", device.getType());
                     map.put("address", device.getAddress());
                     map.put("name", device.getName());
                     JSONObject jObj = new JSONObject(map);
                     */
          json.put(device.getName());
        }
        callbackContext.success(json);
      } else {
        callbackContext.error("No Bluetooth Device Found");
      }
      //Log.d(LOG_TAG, "Bluetooth Device Found: " + mmDevice.getName());
    } catch (Exception e) {
      errMsg = e.getMessage();
      Log.e(LOG_TAG, errMsg);
      e.printStackTrace();
      callbackContext.error(errMsg);
    }
  }

  // This will find a bluetooth printer device
  boolean findBT(CallbackContext callbackContext, String name) {
    try {
      mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
      if (mBluetoothAdapter == null) {
        Log.e(LOG_TAG, "No bluetooth adapter available");
      }
      if (!mBluetoothAdapter.isEnabled()) {
        Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        this.cordova.getActivity().startActivityForResult(enableBluetooth, 0);
      }
      Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
      if (pairedDevices.size() > 0) {
        for (BluetoothDevice device : pairedDevices) {
          if (device.getName().equalsIgnoreCase(name)) {
            mmDevice = device;
            try{
              mService.connect(device);
            }catch (Exception e){
              e.printStackTrace();
            }
            return true;
          }
        }
      }
      Log.d(LOG_TAG, "Bluetooth Device Found: " + mmDevice.getName());
    } catch (Exception e) {
      String errMsg = e.getMessage();
      Log.e(LOG_TAG, errMsg);
      e.printStackTrace();
      callbackContext.error(errMsg);
    }
    return false;
  }

  // Tries to open a connection to the bluetooth printer device
  boolean connectBT(CallbackContext callbackContext) throws IOException {
    try {
      // Standard SerialPortService ID
//            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
//            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
//            mmSocket.connect();
//            mmOutputStream = mmSocket.getOutputStream();
//            mmInputStream = mmSocket.getInputStream();
//            beginListenForData();
//            //Log.d(LOG_TAG, "Bluetooth Opened: " + mmDevice.getName());
      callbackContext.success("Bluetooth Opened: " + mmDevice.getName());
      return true;
    } catch (Exception e) {
      String errMsg = e.getMessage();
      Log.e(LOG_TAG, errMsg);
      e.printStackTrace();
      callbackContext.error(errMsg);
    }
    return false;
  }

  // After opening a connection to bluetooth printer device,
  // we have to listen and check if a data were sent to be printed.
  void beginListenForData() {
    try {
      final Handler handler = new Handler();
      // This is the ASCII code for a newline character
      final byte delimiter = 10;
      stopWorker = false;
      readBufferPosition = 0;
      readBuffer = new byte[1024];
      workerThread = new Thread(new Runnable() {
        public void run() {
          while (!Thread.currentThread().isInterrupted() && !stopWorker) {
            try {
              int bytesAvailable = mmInputStream.available();
              if (bytesAvailable > 0) {
                byte[] packetBytes = new byte[bytesAvailable];
                mmInputStream.read(packetBytes);
                for (int i = 0; i < bytesAvailable; i++) {
                  byte b = packetBytes[i];
                  if (b == delimiter) {
                    byte[] encodedBytes = new byte[readBufferPosition];
                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                        /*
                                         final String data = new String(encodedBytes, "US-ASCII");
                                         readBufferPosition = 0;
                                         handler.post(new Runnable() {
                                         public void run() {
                                         myLabel.setText(data);
                                         }
                                         });
                                         */
                  } else {
                    readBuffer[readBufferPosition++] = b;
                  }
                }
              }
            } catch (IOException ex) {
              stopWorker = true;
            }
          }
        }
      });
      workerThread.start();
    } catch (NullPointerException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  //This will send data to bluetooth printer
  boolean printText(CallbackContext callbackContext, String msg) throws IOException {
    try {
      mmOutputStream.write(msg.getBytes());
      // tell the user data were sent
      //Log.d(LOG_TAG, "Data Sent");
      callbackContext.success("Data Sent");
      return true;

    } catch (Exception e) {
      String errMsg = e.getMessage();
      Log.e(LOG_TAG, errMsg);
      e.printStackTrace();
      callbackContext.error(errMsg);
    }
    return false;
  }

  //This will send data to bluetooth printer
  boolean printImage(CallbackContext callbackContext, String msg) throws IOException {
    try {

      final String encodedString = msg;
      final String pureBase64Encoded = encodedString.substring(encodedString.indexOf(",") + 1);
      final byte[] decodedBytes = Base64.decode(pureBase64Encoded, Base64.DEFAULT);
      Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
      int mHeight = decodedBitmap.getHeight();
      //image = resizeImage(image, 48 * 8, mHeight);//348
      decodedBitmap = resizeImage(decodedBitmap, 500, mHeight);

      byte[] draw2PxPoint = decodeBitmap(decodedBitmap);

      //mmOutputStream.write(bt);
      // tell the user data were sent
      //Log.d(LOG_TAG, "Data Sent");
      //callbackContext.success("Data Sent");
      mService.write(draw2PxPoint);
      //
      byte[] end = { 0x1d, 0x4c, 0x1f, 0x00 };
      mService.write(end);
      return true;

    } catch (Exception e) {
      String errMsg = e.getMessage();
      Log.e(LOG_TAG, errMsg);
      e.printStackTrace();
      callbackContext.error(errMsg);
    }
    return false;
  }
  //  public static Bitmap createBitmap(@NonNull Bitmap source, int x, int y, int width, int height) {
//    return createBitmap(source, x, y, width, height, null, false);
//
//  }
  //New implementation
  private static Bitmap resizeImage(Bitmap bitmap, int w, int h) {
    // Bitmap BitmapOrg = bitmap;
    //int width = BitmapOrg.getWidth();
    //int height = BitmapOrg.getHeight();



    int height = 255;
    h = height;
    float scaleWidth = 1.0f;
    //float scaleHeight = 1.1383928571428f;
    Matrix matrix = new Matrix();
    matrix.postScale(scaleWidth, 1.0f);






    Matrix matrix1 = new Matrix();
    matrix1.postScale(0.88f, 1.0f);
    Bitmap bmp = Bitmap.createBitmap(bitmap, 0, 0, 600, 255, matrix1,
      false);
    int num = 100;

    // 背图
    Bitmap bitmap3 = Bitmap.createBitmap(600, 255, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap3);
    Paint paint = new Paint();
    paint.setAntiAlias(true);
    canvas.drawARGB(0, 0, 0, 0);
    // 生成白色的
    paint.setColor(Color.WHITE);
    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_ATOP));
    canvas.drawBitmap(bmp, 450, 255, paint);

    // 画正方形的
    /**
     * 参数1：bitmap对象
     * 参数2：图像左边坐标点
     * 参数3：图像上边坐标点
     */
    canvas.drawBitmap(bmp, 65,0, paint);



    Bitmap resizedBitmap = Bitmap.createBitmap(bitmap3, 0, 0, bitmap3.getWidth(),
      height, matrix, true);
    return resizedBitmap;
    //} else {
    //     Bitmap resizedBitmap = Bitmap.createBitmap(w, height + 24, Config.RGB_565);
    //    Canvas canvas = new Canvas(resizedBitmap);
    //    Paint paint = new Paint();
    //    canvas.drawColor(Color.WHITE);
    //    canvas.drawBitmap(bitmap, (w - width) / 2, 0, paint);
    //    return resizedBitmap;
    //}
  }
  /**
   * Bitmap 剪切成正方形，然后添加白边
   *
   * @param bitmap
   * @return
   */
  public static  Bitmap whiteEdgeBitmap(Bitmap bitmap) {
    int size = bitmap.getWidth() < bitmap.getHeight() ? bitmap.getWidth() : bitmap.getHeight();
    int num = 14;
    int size2 = size + num;
    //剪切成正方形
    Bitmap bitmap2 = Bitmap.createBitmap(bitmap, 0, 0, size, size);
    // 背图
    Bitmap bitmap3 = Bitmap.createBitmap(size2, size2, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap3);
    Paint paint = new Paint();
    paint.setAntiAlias(true);
    canvas.drawARGB(0, 0, 0, 0);
    // 生成白色的
    paint.setColor(Color.WHITE);
    canvas.drawBitmap(bitmap2, num / 2, num / 2, paint);
    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_ATOP));
    // 画正方形的
    canvas.drawRect(0, 0, size2, size2, paint);
    return bitmap3;
  }


  // disconnect bluetooth printer.
  boolean disconnectBT(CallbackContext callbackContext) throws IOException {
    try {
      stopWorker = true;
      mmOutputStream.close();
      mmInputStream.close();
      mmSocket.close();
      callbackContext.success("Bluetooth Disconnect");
      return true;
    } catch (Exception e) {
      String errMsg = e.getMessage();
      Log.e(LOG_TAG, errMsg);
      e.printStackTrace();
      callbackContext.error(errMsg);
    }
    return false;
  }
  boolean printPOSCommand(CallbackContext callbackContext, byte[] buffer) throws IOException {
    try {
      mmOutputStream.write(buffer);
      // tell the user data were sent
      Log.d(LOG_TAG, "Data Sent");
      callbackContext.success("Data Sent");
      return true;
    } catch (Exception e) {
      String errMsg = e.getMessage();
      Log.e(LOG_TAG, errMsg);
      e.printStackTrace();
      callbackContext.error(errMsg);
    }
    return false;
  }
  //New implementation, change old
  public static byte[] hexStringToBytes(String hexString) {
    if (hexString == null || hexString.equals("")) {
      return null;
    }
    hexString = hexString.toUpperCase();
    int length = hexString.length() / 2;
    char[] hexChars = hexString.toCharArray();
    byte[] d = new byte[length];
    for (int i = 0; i < length; i++) {
      int pos = i * 2;
      d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
    }
    return d;
  }

  private static byte charToByte(char c) {
    return (byte) "0123456789ABCDEF".indexOf(c);
  }
  private static String hexStr = "0123456789ABCDEF";

  private static String[] binaryArray = {"0000", "0001", "0010", "0011",
    "0100", "0101", "0110", "0111", "1000", "1001", "1010", "1011",
    "1100", "1101", "1110", "1111"};

  public static byte[] decodeBitmap(Bitmap bmp) {
    int bmpWidth = bmp.getWidth();
    int bmpHeight = bmp.getHeight();
    List<String> list = new ArrayList<String>(); //binaryString list
    StringBuffer sb;
    int bitLen = bmpWidth / 8;
    int zeroCount = bmpWidth % 8;
    String zeroStr = "";
    if (zeroCount > 0) {
      bitLen = bmpWidth / 8 + 1;
      for (int i = 0; i < (8 - zeroCount); i++) {
        zeroStr = zeroStr + "0";
      }
    }

    for (int i = 0; i < bmpHeight; i++) {
      sb = new StringBuffer();
      for (int j = 0; j < bmpWidth; j++) {
        int color = bmp.getPixel(j, i);

        int r = (color >> 16) & 0xff;
        int g = (color >> 8) & 0xff;
        int b = color & 0xff;
        // if color close to white，bit='0', else bit='1'
        if (r > 160 && g > 160 && b > 160) {
          sb.append("0");
        } else {
          sb.append("1");
        }
      }
      if (zeroCount > 0) {
        sb.append(zeroStr);
      }
      list.add(sb.toString());
    }

    List<String> bmpHexList = binaryListToHexStringList(list);
    String commandHexString = "1D763000";
    String widthHexString = Integer.toHexString(bmpWidth % 8 == 0 ? bmpWidth / 8 : (bmpWidth / 8 + 1));
    if (widthHexString.length() > 2) {
      Log.d(LOG_TAG, "DECODEBITMAP ERROR : width is too large");
      return null;
    } else if (widthHexString.length() == 1) {
      widthHexString = "0" + widthHexString;
    }
    widthHexString = widthHexString + "00";

    String heightHexString = Integer.toHexString(bmpHeight);
    if (heightHexString.length() > 2) {
      Log.d(LOG_TAG, "DECODEBITMAP ERROR : height is too large");
      return null;
    } else if (heightHexString.length() == 1) {
      heightHexString = "0" + heightHexString;
    }
    heightHexString = heightHexString + "00";

    List<String> commandList = new ArrayList<String>();
    commandList.add(commandHexString + widthHexString + heightHexString);
    commandList.addAll(bmpHexList);

    return hexList2Byte(commandList);
  }

  public static List<String> binaryListToHexStringList(List<String> list) {
    List<String> hexList = new ArrayList<String>();
    for (String binaryStr : list) {
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < binaryStr.length(); i += 8) {
        String str = binaryStr.substring(i, i + 8);

        String hexString = myBinaryStrToHexString(str);
        sb.append(hexString);
      }
      hexList.add(sb.toString());
    }
    return hexList;

  }

  public static String myBinaryStrToHexString(String binaryStr) {
    String hex = "";
    String f4 = binaryStr.substring(0, 4);
    String b4 = binaryStr.substring(4, 8);
    for (int i = 0; i < binaryArray.length; i++) {
      if (f4.equals(binaryArray[i])) {
        hex += hexStr.substring(i, i + 1);
      }
    }
    for (int i = 0; i < binaryArray.length; i++) {
      if (b4.equals(binaryArray[i])) {
        hex += hexStr.substring(i, i + 1);
      }
    }

    return hex;
  }

  public static byte[] hexList2Byte(List<String> list) {
    List<byte[]> commandList = new ArrayList<byte[]>();

    for (String hexStr : list) {
      commandList.add(hexStringToBytes(hexStr));
    }
    byte[] bytes = sysCopy(commandList);
    return bytes;
  }

  public static byte[] sysCopy(List<byte[]> srcArrays) {
    int len = 0;
    for (byte[] srcArray : srcArrays) {
      len += srcArray.length;
    }
    byte[] destArray = new byte[len];
    int destLen = 0;
    for (byte[] srcArray : srcArrays) {
      System.arraycopy(srcArray, 0, destArray, destLen, srcArray.length);
      destLen += srcArray.length;
    }
    return destArray;
  }

}
