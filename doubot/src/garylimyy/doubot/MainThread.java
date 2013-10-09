/*	This thread initiates the camera and sensor thread. thinking of combining with ioio (main activity) thread.
 */

package garylimyy.doubot;

import garylimyy.doubot.CameraThread;
import garylimyy.doubot.MainActivity;
import garylimyy.doubot.SensorsThread;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;


public class MainThread implements Runnable {

	Runnable cameraThread;
	SensorsThread sensorsThread;	
	SurfaceView view;
	String controllerIP;
	SensorManager sensorManager;
	
	private Handler UIHandler;
	private Handler IOIOHandler;
	
	//Constructor:
	public MainThread(MainActivity app, SensorManager sensorManagerIn, SurfaceView viewIn, Handler UIHandlerIn, Handler IOIOHandlerIn) {
		super();
		Log.d("MainThread", "MAIN THREAD INIT");
		
		view=viewIn;
		sensorManager = sensorManagerIn;
		
		//Handler to update UI updates and send IOIO commands to MainActivity
		IOIOHandler = IOIOHandlerIn;
		UIHandler = UIHandlerIn;
			
	}
	
    public void run() {
    	
    	 // Moves the current Thread into the background
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
    	Log.d("MainThread", "MAIN THREAD STARTED");
    	
    	DatagramSocket socket=null;

    	try {
    		//create listening socket:
    		socket = new DatagramSocket(MainActivity.IOIO_PORT);
    		Log.d("MainThread","Socket created ");
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    		Log.d("MainThread","Socket failure: " + e);
    	} 
    	
    	byte[] receiveData = new byte[1024];
    	DatagramPacket packet_R = new DatagramPacket(receiveData, receiveData.length);
    	
		try {
			Log.d("MainThread","Socket listening ");
			
			socket.receive(packet_R);
			controllerIP = new String(packet_R.getData(),0,packet_R.getLength());
			controllerIP = packet_R.getAddress().toString().substring(1); //temp; DETECTED IP!
			Log.d("MainThread","IP STRING IN PACKET: " + controllerIP + ", DETECTED IP: " + packet_R.getAddress().toString().substring(1) + ", DETECTED PORT: "+ packet_R.getPort());
			
			//setting dynamic port numbers:
			MainActivity.CAMERA_PORT = packet_R.getPort();
			MainActivity.SENSORS_PORT = packet_R.getPort();
			
			Log.d("MainThread","Socket received ");
		} 
		catch (Exception e) {
			
			Log.d("MainThread","FAILED: "+ e);
		} 

		Message UImsg = new Message();
		Bundle UIbundle = new Bundle();

		UIbundle.putString("CONTROLLER_IP",controllerIP);
		UImsg.setData(UIbundle);
		UIHandler.sendMessage(UImsg);
		
		cameraThread = new CameraThread(view ,controllerIP, socket);
//		cameraThread.setPriority((Thread.MAX_PRIORITY + Thread.NORM_PRIORITY) / 2);
		new Thread(cameraThread).start(); //calls the init method

		//sensor disabled for now
		//sensorsThread = new SensorsThread(sensorManager, controllerIP, socket);
		
		//continuous loop to receive IOIO commands from Controller:
		while (true) {
			
			Log.d("MainThread","IOIO: LOOPING, att to create receivepacket()");
			byte[] data2 = new byte[6];
			DatagramPacket receivePacket = new DatagramPacket(data2, data2.length);
			try {
				socket.setSoTimeout(500);
				socket.receive(receivePacket);
				
				byte[] data3 = receivePacket.getData();		
				int armServoVal = (int) ((data3[0] & 0xff) << 8 | (data3[1] & 0xff)); 
				int leftServoVal = (int) ((data3[2] & 0xff) << 8 | (data3[3] & 0xff));
				int rightServoVal = (int) ((data3[4] & 0xff) << 8 | (data3[5] & 0xff));

				Message IOIOmsg = new Message();
				Bundle IOIObundle = new Bundle();

				IOIObundle.putInt("armServoVal", armServoVal); 
				IOIObundle.putInt("leftServoVal", leftServoVal); 
				IOIObundle.putInt("rightServoVal", rightServoVal); 
				IOIOmsg.setData(IOIObundle);
				IOIOHandler.sendMessage(IOIOmsg);
			}
			catch (Exception e) {
				Log.d("MainThread","IOIO: SIGNAL NOT RECEIVED");
			}
		}
    }
    
    public void stop_threads() {
    	Log.d("MainThread", "MAIN THREAD STOPPED");
    	((CameraThread) cameraThread).stop_thread();
    	
		//sensor disabled for now
    	//sensorsThread.stop_thread();
    }	
}
