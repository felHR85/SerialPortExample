package com.felhr.serialportexample;

import java.util.HashMap;
import java.util.Map;

import com.felhr.usbserial.CDCSerialDevice;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.IBinder;

public class UsbService extends Service
{
	public static final String ACTION_USB_READY = "com.felhr.connectivityservices.USB_READY";
	private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
	public static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
	public static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
	public static final String ACTION_USB_NOT_SUPPORTED = "com.felhr.usbservice.USB_NOT_SUPPORTED";
	public static final String ACTION_NO_USB = "com.felhr.usbservice.NO_USB";
	public static final String ACTION_USB_PERMISSION_GRANTED = "com.felhr.usbservice.USB_PERMISSION_GRANTED";
	public static final String ACTION_USB_PERMISSION_NOT_GRANTED = "com.felhr.usbservice.USB_PERMISSION_NOT_GRANTED";
	public static final String ACTION_USB_DISCONNECTED = "com.felhr.usbservice.USB_DISCONNECTED";
	public static final String ACTION_CDC_DRIVER_NOT_WORKING ="com.felhr.connectivityservices.ACTION_CDC_DRIVER_NOT_WORKING";
	public static final String ACTION_USB_DEVICE_NOT_WORKING = "com.felhr.connectivityservices.ACTION_USB_DEVICE_NOT_WORKING";
	
	private static final int BAUD_RATE = 9600; // BaudRate. Change thsi value if you need
	
	private Context context;
	private UsbManager usbManager;
	private UsbDevice device;
	private UsbDeviceConnection connection;
	private UsbSerialDevice serialPort;
	
	/*
	 * onCreate will be executed when service is started. It configures an IntentFilter to listen for
	 * incoming Intents (USB ATTACHED, USB DETACHED...) and it tries to open a serial port.
	 */
	@Override
	public void onCreate()
	{
		this.context = this;
		setFilter();
		usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		findSerialPortDevice();
	}
	
	@Override
	public IBinder onBind(Intent intent) 
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	private void findSerialPortDevice()
	{
		// This snippet will try to open the first encountered usb device connected, excluding usb root hubs
		HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
		if(!usbDevices.isEmpty())
		{
			boolean keep = true;
			for(Map.Entry<String, UsbDevice> entry : usbDevices.entrySet())
			{
				device = entry.getValue();
				int deviceVID = device.getVendorId();
				int devicePID = device.getProductId();
				if(deviceVID != 0x1d6b || (devicePID != 0x0001 || devicePID != 0x0002 || devicePID != 0x0003))
				{
					// There is a device connected to our Android device. Try to open it as a Serial Port.
					connection = usbManager.openDevice(device);
					requestUserPermission();
					keep = false;  
				}else
				{
					connection = null;
					device = null;
				}

				if(!keep)
					break;
			}
			if(!keep)
			{
				// There is no USB devices connected (but usb host were listed). Send an intent to MainActivity.
				Intent intent = new Intent(ACTION_NO_USB);
				sendBroadcast(intent);
			}
		}else
		{
			// There is no USB devices connected. Send an intent to MainActivity
			Intent intent = new Intent(ACTION_NO_USB);
			sendBroadcast(intent);
		}
	}

	private void setFilter()
	{
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_USB_PERMISSION);
		filter.addAction(ACTION_USB_DETACHED);
		filter.addAction(ACTION_USB_ATTACHED);
		registerReceiver(usbReceiver , filter);
	}
	
	private void requestUserPermission()
	{
		PendingIntent mPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION),0);
		usbManager.requestPermission(device, mPendingIntent);
	}
	
	private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() 
	{
		@Override
		public void onReceivedData(byte[] arg0) 
		{
			
		}
	};
	
	private final BroadcastReceiver usbReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context arg0, Intent arg1) 
		{
			if(arg1.getAction().equals(ACTION_USB_PERMISSION))
			{
				boolean granted = arg1.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
				if(granted) // User accepted our USB connection. Try to open the device as a serial port
				{
					Intent intent = new Intent(ACTION_USB_PERMISSION_GRANTED);
					arg0.sendBroadcast(intent);
					new ConnectionThread().run();
				}else // User not accepted our USB connection. Send an Intent to the Main Activity
				{
					Intent intent = new Intent(ACTION_USB_PERMISSION_NOT_GRANTED);
					arg0.sendBroadcast(intent);
				}
			}else if(arg1.getAction().equals(ACTION_USB_ATTACHED))
			{
				findSerialPortDevice(); // A USB device has been attached. Try to open it as a Serial port
			}else if(arg1.getAction().equals(ACTION_USB_DETACHED))
			{
				// Usb device was disconnected. send an intent to the Main Activity
				Intent intent = new Intent(ACTION_USB_DISCONNECTED);
				arg0.sendBroadcast(intent);
			}
		}
	};
	
	/*
	 * A simple thread to open a serial port.
	 * Although it should be a fast operation. moving usb operations away from UI thread is a good thing.
	 */
	private class ConnectionThread extends Thread
	{
		@Override
		public void run()
		{
			serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
			if(serialPort != null)
			{
				if(serialPort.open())
				{
					serialPort.setBaudRate(BAUD_RATE);
					serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
					serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
					serialPort.setParity(UsbSerialInterface.PARITY_NONE);
					serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
					serialPort.read(mCallback);
					
					// Everything went as expected. Send an intent to MainActivity
					Intent intent = new Intent(ACTION_USB_READY);
					context.sendBroadcast(intent);
				}else
				{
					// Serial port could not be opened, maybe an I/O error or if CDC driver was chosen, it does not really fit
					// Send an Intent to Main Activity
					if(serialPort instanceof CDCSerialDevice)
					{
						Intent intent = new Intent(ACTION_CDC_DRIVER_NOT_WORKING);
						context.sendBroadcast(intent);
					}else
					{
						Intent intent = new Intent(ACTION_USB_DEVICE_NOT_WORKING);
						context.sendBroadcast(intent);
					}
				}
			}else
			{
				// No driver for given device, even generic CDC driver could not be loaded
				Intent intent = new Intent(ACTION_USB_NOT_SUPPORTED);
				context.sendBroadcast(intent);
			}
		}
	}

}
