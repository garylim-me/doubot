/*	This thread sends phone data: orientation, GPS location, battery life
 */

package garylimyy.doubot;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class SensorsThread implements SensorEventListener, Runnable{

	InetAddress controllerAddr;
	DatagramSocket socket;	
	int frame_nb = -1; //header identifier
	
	float x_O, y_O, z_O, x_A, y_A, z_A;
	short ix_O, iy_O, iz_O, ix_A, iy_A, iz_A;
//	int packetSize;
//	SensorManager mSensorManager = null;	

	public SensorsThread(SensorManager sensorManager, String controllerIPIn, DatagramSocket socketIn) {  //Constructor
		super();
//		mSensorManager = sensorManager;    	
		socket = socketIn;
		try {
			controllerAddr = InetAddress.getByName(controllerIPIn);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		};
	}
	

	@Override
	public void onSensorChanged(SensorEvent event) {
		synchronized (this) {
			if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
				x_O = event.values[0] *100;
				y_O = event.values[1] *100;
				z_O = event.values[2] *100;
			}
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				x_A = event.values[0] *100;
				y_A = event.values[1] *100;
				z_A = event.values[2] *100;
			} 
		}	
		send_data_UDP();
	}
	
	
	private void send_data_UDP()  {
		try {			
			ix_O = (short) (x_O);
			iy_O = (short) (y_O);
			iz_O = (short) (z_O);
			ix_A = (short) (x_A);
			iy_A = (short) (y_A);
			iz_A = (short) (z_A);        	

			byte[] data = new byte[12];
			data[0] = (byte) (ix_A >> 8);
			data[1] = (byte) ix_A;    			
			data[2] = (byte) (iy_A >> 8);
			data[3] = (byte) iy_A;    			
			data[4] = (byte) (iz_A >> 8);
			data[5] = (byte) iz_A;
			data[6] = (byte) (ix_O >> 8);
			data[7] = (byte) ix_O;    			
			data[8] = (byte) (iy_O >> 8);
			data[9] = (byte) iy_O;    			
			data[10] = (byte) (iz_O >> 8);
			data[11] = (byte) iz_O;    			
			
			// OPTION 1 for SENDING
			int nb_packets = (int) Math.ceil(data.length / (float) CameraThread.DATAGRAM_MAX_SIZE);
			int size = CameraThread.DATAGRAM_MAX_SIZE;

			// Loop through slices
			for(int i = 0; i < nb_packets; i++) {			
				if(i >0 && i == nb_packets-1) 
					size = data.length - i * CameraThread.DATAGRAM_MAX_SIZE;

				// Set additional header
				byte[] data2 = new byte[CameraThread.HEADER_SIZE + size];
				data2[0] = (byte)frame_nb;
				data2[1] = (byte)nb_packets;
				data2[2] = (byte)i;
				data2[3] = (byte)(size >> 8);
				data2[4] = (byte)size;

				// Copy current slice to byte array
				System.arraycopy(data, i * CameraThread.DATAGRAM_MAX_SIZE, data2, CameraThread.HEADER_SIZE, size);		

				try {			
					int size_p = data2.length;
					DatagramPacket packet = new DatagramPacket(data2, size_p, controllerAddr, MainActivity.CAMERA_PORT);
					//Log.d("CameraThread","MAX BUFFER: "+socket.getSendBufferSize());
					socket.send(packet);
					Log.d("SensorsThread","Packet Sent");
				} catch (Exception e) {	Log.e("SensorsThread", "Packet send error: ", e);}	
			}	
			
			
			// OPTION 2 for SENDING
//			try {			
//				packetSize = data.length;
//				DatagramPacket packet = new DatagramPacket(data, packetSize, controllerAddr, MainActivity.SENSORS_PORT);
//				Log.d("SensorsThread"," Send "+ new String(data,0,packet.getLength()));
//				socket.send(packet);
//			} catch (Exception e) {	
//				Log.e("SensorsThread", "Error: ", e);
//			}	
			
		}
		catch (Exception exception) {
			Log.e("SensorsThread", "Error: ", exception);
		}
	}

	
	public void stop_thread() {
		socket.close();
	}


	@Override
	public void run() {
		 // Moves the current Thread into the background
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

	}

	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
	}

	
}
