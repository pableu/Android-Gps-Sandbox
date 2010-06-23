package ch.ethz.pablo.timesync;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class syncerMain extends Activity implements OnClickListener {
	

	private static final String TAG = "HttpTimeSync";

	private static final String DB_TABLENAME = "sync_results";
	
	private boolean post_is_running = false;
	
	private Button pushButton;
	private Button dataButton;
	private Button dumpButton;
	
	private doSomethingDelayed doSth;

	private syncService boundService = null;
	
	private SQLiteDatabase db;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        
        pushButton = (Button) findViewById(R.id.push_button);
        pushButton.setOnClickListener(this);
        
        dataButton = (Button) findViewById(R.id.sql_button);
        dataButton.setOnClickListener(this);
        
        dumpButton = (Button) findViewById(R.id.dump_button);
        dumpButton.setOnClickListener(this);
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
//    	if(post_is_running){ // stop async task if it's running if app gets paused
//			Log.v(TAG, "Stopping Async Task onPause");
//			doSth.cancel(true);
//		}
    	db.close();
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
//		if(post_is_running) { // start async task if it was running previously and was stopped by onPause()
//			Log.v(TAG, "Starting Async Task onResume");
//			doSth = (doSomethingDelayed) new doSomethingDelayed().execute();
//			((Button) findViewById(R.id.push_button)).setText("Resuming..");
//		}
		db = new DatabaseHelper(this).getReadableDatabase();
    }
    
	public void onClick(View v) {
		if (v == pushButton) {
			if(post_is_running == false) {
				post_is_running = true;
	//			Log.v(TAG, "Starting Async Task onClick");
	//			doSth = (doSomethingDelayed) new doSomethingDelayed().execute();
				
				bindService(new Intent(syncerMain.this, syncService.class), syncServiceConnection, Context.BIND_AUTO_CREATE);
				
				((Button) findViewById(R.id.push_button)).setText("Starting..");
			}
			else {
	//			Log.v(TAG, "Stopping Async Task onClick");
				post_is_running = false;
				unbindService(syncServiceConnection);
	//			doSth.cancel(true);
				((Button) findViewById(R.id.push_button)).setText("Stopping..");
			}
		}
		if (v == dataButton) {
//			int[] ts     = new int[10];
//			int[] offset = new int[10];
//			int[] rtt    = new int[10];
//			int[][] results = new int[10][3];
//			
//			Cursor lastTenCursor = db.rawQuery("SELECT ts, offset, rtt FROM " + DB_TABLENAME + " ORDER BY ts DESC LIMIT 10", null);
//			
//			lastTenCursor.moveToFirst();
//			
//			int i = 0;
//			
//			while(!lastTenCursor.isLast()) {
////				ts[i]     = lastTenCursor.getInt(0);
////				offset[i] = lastTenCursor.getInt(1);
////				rtt[i]    = lastTenCursor.getInt(2);
//				results[i][0] = lastTenCursor.getInt(0);
//				results[i][1] = lastTenCursor.getInt(1);
//				results[i][2] = lastTenCursor.getInt(2);
//				i++;
//				lastTenCursor.moveToNext();
//			}
//			
//			ListAdapter adapter=new SimpleCursorAdapter(this,
//					R.id.data_list, lastTenCursor,
//					new String[] {"ts", "offset"},
//					new int[] {R.id.title, R.id.value});
//
//			lastTenCursor.close();
			
			Cursor countCursor = db.rawQuery("SELECT count(*) FROM " + DB_TABLENAME + " WHERE ts NOT null", null);
			countCursor.moveToFirst();
			int count = countCursor.getInt(0);
			
			((TextView)findViewById(R.id.text_to_fill)).setText("Num. of Rows = " + count);
			
			Toast.makeText(getApplicationContext(), "data button pushed", Toast.LENGTH_SHORT).show();

			countCursor.close();
		}
		
		if (v == dumpButton) {
			
			new dumpCSV().execute(); // call to the AsyncTask
		}
			
	}
	
	
	private ServiceConnection syncServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			boundService = ((syncService.LocalBinder)service).getService();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			// TODO Auto-generated method stub
			boundService = null;
			
		}
	};
	
	
	
	private class dumpCSV extends AsyncTask<Void, String, Void> {
		private final static String DONE = "Finished dumping SQLite to SD Card";
		private final static String SD_ERROR = "Could not write to SD Card";

		@Override
		protected Void doInBackground(Void... params) {
			
			
			boolean externalStorageWriteable = false;
			
			String state = Environment.getExternalStorageState();

			if (Environment.MEDIA_MOUNTED.equals(state)) {
			    // We can read and write the media
			    externalStorageWriteable = true;
			} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			    externalStorageWriteable = false;
			} else {
			    // Something else is wrong. It may be one of many other states, but all we need
			    //  to know is we can neither read nor write
			    externalStorageWriteable = false;
			}
			
			if (externalStorageWriteable) {
				Log.i(TAG, "Start writing SQLite to CSV");
				try {
					File csvFile = new File(Environment.getExternalStorageDirectory(), "ch.ethz.pablo.timesync/out.csv");
					
					csvFile.mkdirs();
					
					// delete file if it already exists
					if (csvFile.exists()) {
						csvFile.delete();
					}
					csvFile.createNewFile();
					
					BufferedWriter csvWriter = new BufferedWriter(new FileWriter(csvFile));
//					BufferedWriter out = new BufferedWriter(gpxwriter);
					long id, ts;
					int offset, rtt;
					String line;
					
					Cursor allData = db.rawQuery("SELECT * FROM " + DB_TABLENAME + " WHERE ts NOT null ORDER BY ts ASC", null);
					
					allData.moveToFirst();
					// write column names in first line of CSV
					line = "id,ts,offset,rtt\n";
					csvWriter.write(line);
					
					while(!allData.isLast()) {
						id     = allData.getLong(0);
						ts     = allData.getLong(1);
						offset = allData.getInt(2);
						rtt    = allData.getInt(3);
						
						line = id + "," + ts + "," + offset + "," + rtt + "\n";
						csvWriter.write(line);
						allData.moveToNext();
					}
					csvWriter.flush();
					
					allData.close();
				} catch(Exception e) {
					Log.e(TAG, "Error while dumping SQLite to CSV: " + e.toString());
				}
				Log.i(TAG, "Done writing SQLite to CSV");
				publishProgress(DONE);
			} else {
				Log.w(TAG, "Cannot write SQLite to CSV: SD Card not writeable/available");
				publishProgress(SD_ERROR);
			}
			return null;
		}
		
		
		@Override
		protected void onProgressUpdate(String... params) {
			Toast.makeText(getApplicationContext(), params[0],Toast.LENGTH_LONG).show();
		}
		
	}


	/******* crap from here on *******/
	
    
    private class doSomethingDelayed extends AsyncTask<Void, Integer, Void> {
    	
    	private int num_runs = 0;
    	private long RTT;
    	private long timeDifference;
    	
		@Override
		protected Void doInBackground(Void... gurk) {
			
			while(!this.isCancelled()) {
		        

				Long[] syncResult = syncTime();
				

				RTT = syncResult[0];
				long serverTime = syncResult[1] + RTT/2;
				long myTime = syncResult[2];
				
				timeDifference = serverTime-myTime;
				
				Log.i(TAG, "Sync finished! RTT: " +syncResult[0]);
				Log.i(TAG, "Server: " + serverTime + " (raw: " + syncResult[1] + ")");
				Log.i(TAG, "Phone:  " + myTime);
				Log.i(TAG, "Time Difference:  " + timeDifference);
				
		        num_runs++;
		        
		        try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		        
		        // publish to UI
		        if(!this.isCancelled()) {
		        	publishProgress(num_runs);
		        }
			}
			return null;
		}

		
		@Override
		protected void onCancelled() {
			Context context = getApplicationContext();
			CharSequence text = "Cancelled BG-Thread";
			int duration = Toast.LENGTH_LONG;
			
			Toast.makeText(context, text, duration).show();
			
			((Button) findViewById(R.id.push_button)).setText("Stopped. Tap to Start!");
		}
		
		@Override
		protected void onProgressUpdate(Integer... num_runs) {
			Context context = getApplicationContext();
			CharSequence text = "Looped " + num_runs[0].toString() + " Times";
			int duration = Toast.LENGTH_SHORT;

			Toast.makeText(context, text + "\nTimeDiff: " + timeDifference + ", RTT: " + RTT, duration).show();
			
			((Button) findViewById(R.id.push_button)).setText(text + "\nTap to Stop");
			
		}
    }
    
    public Long[] syncTime() {
    	

//        byte[] sBuffer = new byte[512];
        
        HttpParams params = new BasicHttpParams();
        params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
        
        HttpClient httpclient = new DefaultHttpClient(params);
//        HttpGet request = new HttpGet("http://192.168.1.137:8880/time");
//        HttpGet request = new HttpGet("http://pableu.dyndns.org:8880/time");
        HttpGet request = new HttpGet("http://129.132.131.73:8080/time");

        long roundTripTime = 0;

        long phoneTime = 0;
        long serverTime = 0;
        
        try {

            // alternative to get string from response
            ResponseHandler<String> stringResponseHandler = new BasicResponseHandler();
            
        	// get start time
	        long ms_before = SystemClock.uptimeMillis();
	        
            // Execute HTTP Get Request
            String stringResponse = httpclient.execute(request, stringResponseHandler);
            
            // get end-time, calculate RTT
	        long ms_after = SystemClock.uptimeMillis();
	        roundTripTime = ms_after - ms_before;
	        
	        // get current timestamp of the phone...
	        phoneTime = System.currentTimeMillis();
/*
            // Pull content stream from response
            HttpEntity entity = response.getEntity();
            InputStream inputStream = entity.getContent();

            ByteArrayOutputStream content = new ByteArrayOutputStream();

            // Read response into a buffered stream
            int readBytes = 0;
            while ((readBytes = inputStream.read(sBuffer)) != -1) {
                content.write(sBuffer, 0, readBytes);
            }
*/
            try{
            	serverTime = Long.parseLong(stringResponse);
            } catch (NumberFormatException e) {
            	Log.e(TAG, "Number Format Exception when parsing server Response: " + e.getMessage());
            }
            
            
            return new Long[] {roundTripTime, serverTime, phoneTime};
            
            
        } catch (ClientProtocolException e) {
        	Log.e(TAG,e.toString());
        } catch (IOException e) {
        	Log.e(TAG,e.toString());
        }

        Log.i(TAG, "Time Difference: " + (serverTime-phoneTime) + " - RTT: " + roundTripTime);
		return null;

//		Log.i(TAG, "RTT inside post-data: " + roundTripTime);
		
//		Log.i(TAG, resp.toString());
    } 
    
}