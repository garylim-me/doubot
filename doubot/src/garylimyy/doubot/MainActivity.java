/*	This is the UI activity; also communicates with the IOIO board
 */

package garylimyy.doubot;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;

import garylimyy.doubot.MainThread;
import garylimyy.doubot.SensorsThread;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.text.format.Formatter;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends IOIOActivity {

	//Thread stuff:
	private static Handler UIHandler;
	private static Handler IOIOHandler;
	MainThread mainThread;
	SensorsThread sensorsThread = null;
	SensorManager sensorManager = null;
	static boolean manualMode = false;
	public static int IOIO_PORT = 9001; //listening port for incoming
	public static int CAMERA_PORT = 9002; //will be overwritten by packet port source!
	public static int SENSORS_PORT = 9003; //will be overwritten by packet port source!
	
	
	//Network stuff:
	private static String ControllerIP;
	WifiLock wifiLock;

	//View stuff:
	ToggleButton togglebutton;
	EditText edittextControllerIP;
	EditText edittextThisIP;
	SurfaceView view;
	private ToggleButton btnManualCommand;
	private SeekBar seekBar3;
	private SeekBar seekBar5;
	private SeekBar seekBar7;
	
	//IOIO Stuff:
	private final int ARM_PIN = 11;
	private final int LEFT_PIN = 12;
	private final int RIGHT_PIN = 13;
	int armServoVal, leftServoVal, rightServoVal;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		view = (SurfaceView) findViewById(R.id.PREVIEW);
		
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		edittextControllerIP = (EditText) findViewById(R.id.Controller_IP);
		togglebutton = (ToggleButton) findViewById(R.id.CameraButton);
		togglebutton.setOnClickListener(new btnListener());		

		
		
		//Manual Sliders:
		btnManualCommand = (ToggleButton) findViewById(R.id.btnManualCommand);
		seekBar3 = (SeekBar) findViewById(R.id.seekBar3);
		seekBar5 = (SeekBar) findViewById(R.id.seekBar5);
		seekBar7 = (SeekBar) findViewById(R.id.seekBar7);

		//handlers: change UI display
		UIHandler = new Handler() {
			@Override
		    public void handleMessage(Message msg) {
			    Bundle bundle = msg.getData();
			    edittextControllerIP.setText(bundle.getString("CONTROLLER_IP"));
			}
		};
		
		//handlers: Change ioio values
		IOIOHandler = new Handler() {
			@Override
		    public void handleMessage(Message msg) {
				if (!manualMode) { 
					Bundle bundle = msg.getData();

					armServoVal = bundle.getInt("armServoVal");
					leftServoVal = bundle.getInt("leftServoVal");
					rightServoVal = bundle.getInt("rightServoVal");
					
					Log.d("MainActivity","Left servo Values: "+ leftServoVal);
				}
			}
		};
		
		
		btnManualCommand.setOnClickListener(new OnClickListener() {
			public void onClick(View v1) {
				if (btnManualCommand.isChecked()) {
					manualMode = true;
				}  
				else {
					manualMode = false;
				}
			} 
		});
		
		enableUi(false);

		//Show Doubot IP in UI:
		edittextThisIP = (EditText) findViewById(R.id.Doubot_IP);
		WifiManager wim= (WifiManager) getSystemService(WIFI_SERVICE);
		edittextThisIP.append(Formatter.formatIpAddress(wim.getConnectionInfo().getIpAddress()));
		
//		wifiLock=wim.createWifiLock(WifiManager.WIFI_MODE_FULL , "MyWifiLock");
//		wifiLock.acquire();
		
		//Get threads started:
		togglebutton.setChecked(true);
		StartMainThread();
	}

	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	

	private class btnListener implements OnClickListener {
		public void onClick(View v) {		
			// Perform action on clicks
			if (togglebutton.isChecked()) {
				StartMainThread();
			} 
			else {
				mainThread.stop_threads();
//				sensorManager.unregisterListener(sensorsThread);
				Toast.makeText(MainActivity.this, "Stop streaming", Toast.LENGTH_SHORT).show();
			}
		}
	}
	
	private void StartMainThread() {
		ControllerIP = edittextControllerIP.getText().toString(); 
		edittextControllerIP.setText("listening...");
		
		mainThread = new MainThread(MainActivity.this, sensorManager, view, UIHandler, IOIOHandler);
		new Thread(mainThread).start(); //calls the init method
		
		// sensor disabled for now				
//		 sensorsThread = mainThread.sensorsThread;				
//		 
////		 sensorManager.registerListener(sensorsThread, 
////				SensorManager.SENSOR_ORIENTATION |SensorManager.SENSOR_ACCELEROMETER,
////				SensorManager.SENSOR_DELAY_UI);
//		
////		 SensorManager sensorService = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
//		 Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION|Sensor.TYPE_ACCELEROMETER );
//		 if (sensor != null) {
//			 sensorManager.registerListener(sensorsThread, sensor,
//		     SensorManager.SENSOR_DELAY_NORMAL);
//		     Log.d("MainActivity", "sensors registered");
//
//		 } else {
//			 Log.d("MainActivity", "sensors register ERROR");	
//		 }

		Toast.makeText(MainActivity.this, "Start streaming", Toast.LENGTH_SHORT).show();
		
	}

	class Looper extends BaseIOIOLooper {
		// Set up IOIO:
		private PwmOutput armServo, leftServo, rightServo;
		
		@Override
		public void setup() throws ConnectionLostException {
			
			try {
				armServo = ioio_.openPwmOutput(ARM_PIN, 50);
				leftServo = ioio_.openPwmOutput(LEFT_PIN, 50);
				rightServo = ioio_.openPwmOutput(RIGHT_PIN, 50);
				
				//Toast.makeText(MainActivity.this, "ioio connected", Toast.LENGTH_SHORT).show();
				enableUi(true);

			} catch (ConnectionLostException e) {
				enableUi(false);
				throw e;
			} 
		}

		@Override
		public void loop() {
			//Log.d("MainActivity","IOIO: NEW LOOP()");
			try {
				//Log.d("MainActivity","IOIO: LOOPING, try()");
				if (manualMode) { 
					
					armServoVal = 500 + seekBar3.getProgress() * 2;
					leftServoVal = 1000 + seekBar5.getProgress() ;
					rightServoVal = 500 + seekBar7.getProgress();
				} 
				
				// Set servo values:
				armServo.setPulseWidth(armServoVal);
				leftServo.setPulseWidth(leftServoVal);
				rightServo.setPulseWidth(rightServoVal);				
			} 
			catch (Exception e) {
				Log.d("MainActivity","IOIO: LOOP ERROR: ", e);
			} 
		}
	}

	
	@Override
	protected IOIOLooper createIOIOLooper() {
		return new Looper();
	}


	private void enableUi(final boolean enable) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				seekBar3.setEnabled(enable);
				seekBar5.setEnabled(enable);
				seekBar7.setEnabled(enable);
			}
		});
	}
	
}