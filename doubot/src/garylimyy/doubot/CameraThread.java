/*	This thread takes images with the camera, and sends mjpeg files
 */

package garylimyy.doubot;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.AsyncTask;
import android.util.Log;
import android.view.SurfaceView;

public class CameraThread implements Runnable {

	//camera stuff:
	Camera mCamera;
	SurfaceView parent_context;
	Bitmap mBitmap;
	int[] mRGBData;
	int width_ima, height_ima;
	
	//network stuff:
	String controllerIP;
	InetAddress controllerAddr;
	DatagramSocket socket;	
	public static int HEADER_SIZE = 5;
	public static int DATAGRAM_MAX_SIZE = 1250 - HEADER_SIZE;	
	int frame_nb = 0;
	int size_packet_sent = 0;
	
	//thread stuff:
		private boolean RUN_THREAD=true;
	
	public CameraThread(SurfaceView context, String ControllerIP, DatagramSocket socketIn) {  //Constructor
		parent_context = context;
		controllerIP = ControllerIP;
		socket = socketIn;
	}

    public void stop_thread() {
    	RUN_THREAD = false;
		socket.close();
		Log.d("CameraThread","thread stopped");
    }
    

	@Override
	public void run() {
		
		// Moves the current Thread into the background
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE);
		
		try {
			controllerAddr = InetAddress.getByName(controllerIP);

			//One-off exception for tablets with only one camera:
			if (android.os.Build.MODEL.toString().startsWith("Nexus 7")) {
				Log.d("CameraThread","nexus 7 detected!");
				mCamera = Camera.open(0);        

//				Camera.Parameters parameters = mCamera.getParameters();
//				parameters.setPreviewFpsRange(15000,15000);
				//parameters.setRotation(90);
				//parameters.set("rotation", 90);
				//parameters.set("orientation", "landscape");
				//parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
				
//				mCamera.setParameters(parameters);
				//mCamera.setDisplayOrientation(90);

				mCamera.setPreviewDisplay(parent_context.getHolder());			
				mCamera.setPreviewCallback(new cam_PreviewCallback());           
				mCamera.startPreview();
				
			} else {
				mCamera = Camera.open();
				
				Camera.Parameters parameters = mCamera.getParameters(); 
				parameters.setPreviewSize(320, 240);
				parameters.setRotation(90);
				parameters.setPreviewFpsRange(5000,15000); //5000-15000 for 5-15fps
				parameters.setSceneMode(Camera.Parameters.SCENE_MODE_SPORTS);
				parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
				parameters.setColorEffect(Camera.Parameters.EFFECT_NONE);
				
				mCamera.setParameters(parameters);
				mCamera.setPreviewDisplay(parent_context.getHolder());			
				mCamera.setPreviewCallback(new cam_PreviewCallback());           
//				mCamera.setDisplayOrientation(0);
				mCamera.startPreview();				
			}
			Log.d("CameraThread","Init completed");
			
		} 
		catch (Exception exception) {
			Log.e("CameraThread", "Error: ", exception);
		}		
	}
    
	// 1. Preview callback used whenever new frame is available
	private class cam_PreviewCallback implements PreviewCallback {
		
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			
			Log.d("CameraThread","Preview Callback started");
			
			if(RUN_THREAD != true) {
				Log.d("CameraThread","Camera stopped");
				mCamera.setPreviewCallback(null);
				mCamera.stopPreview();
				mCamera.release();
				mCamera = null; 
				return;
			} 
			
			if (mBitmap == null) {		//create Bitmap image first time
				Camera.Parameters params = camera.getParameters();
				width_ima = params.getPreviewSize().width;
				height_ima = params.getPreviewSize().height;        			  
				mBitmap = Bitmap.createBitmap(width_ima, height_ima, Bitmap.Config.RGB_565);
				
				mRGBData = new int[width_ima * height_ima];
			}

			decodeYUV420SP(mRGBData, data, width_ima, height_ima);
			mBitmap.setPixels(mRGBData, 0, width_ima, 0, 0, width_ima, height_ima);
			
			//option 1:
			new DownloadFilesTask().execute("nostringneeded");
			
		}
	}
    
	//2. Creates Async task to send data thru UDP
    private class DownloadFilesTask extends AsyncTask<String, Integer, Long> {
		@Override
		protected Long doInBackground(String... params) {
			if(mBitmap != null) {
				int size_p=0,i;    	
				
				ByteArrayOutputStream byteStream = new ByteArrayOutputStream(); 
				mBitmap.compress(Bitmap.CompressFormat.JPEG, 30, byteStream);	// !!!!!!!  30 change compression rate to change packets size

				byte data[] = byteStream.toByteArray();
				Log.e("CameraThread", "SIZE1: " + data.length);

				int nb_packets = (int) Math.ceil(data.length / (float)DATAGRAM_MAX_SIZE);
				int size = DATAGRAM_MAX_SIZE;

				// Loop through slices
				for(i = 0; i < nb_packets; i++) {			
					if(i >0 && i == nb_packets-1) 
						size = data.length - i * DATAGRAM_MAX_SIZE;

					// Set additional header
					byte[] data2 = new byte[HEADER_SIZE + size];
					data2[0] = (byte)frame_nb;
					data2[1] = (byte)nb_packets;
					data2[2] = (byte)i;
					data2[3] = (byte)(size >> 8);
					data2[4] = (byte)size;

					// Copy current slice to byte array
					System.arraycopy(data, i * DATAGRAM_MAX_SIZE, data2, HEADER_SIZE, size);		

					try {			
						size_p = data2.length;
						DatagramPacket packet = new DatagramPacket(data2, size_p, controllerAddr, MainActivity.CAMERA_PORT);
						//Log.d("CameraThread","MAX BUFFER: "+socket.getSendBufferSize());
						socket.send(packet);
						Log.d("CameraThread","Packet Sent");
					} catch (Exception e) {	Log.e("CameraThread", "Error2: ", e);}	
				}	
				
				frame_nb++;
				if(frame_nb == 128) frame_nb=0;	
				Log.d("CameraThread","1 full UDP image Packet Sent");
			}
			
			return null;
		}
    }
    
	
	static public void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {
		final int frameSize = width * height;

		for (int j = 0, yp = 0; j < height; j++) {
			int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
			for (int i = 0; i < width; i++, yp++) {
				int y = (0xff & ((int) yuv420sp[yp])) - 16;
				if (y < 0) y = 0;
				if ((i & 1) == 0) {
					v = (0xff & yuv420sp[uvp++]) - 128;
					u = (0xff & yuv420sp[uvp++]) - 128;
				}

				int y1192 = 1192 * y;
				int r = (y1192 + 1634 * v);
				int g = (y1192 - 833 * v - 400 * u);
				int b = (y1192 + 2066 * u);

				if (r < 0) r = 0; else if (r > 262143) r = 262143;
				if (g < 0) g = 0; else if (g > 262143) g = 262143;
				if (b < 0) b = 0; else if (b > 262143) b = 262143;

				rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
			}
		}
	}


}
