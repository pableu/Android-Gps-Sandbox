package ch.ethz.pablo.timesync;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
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
	private Button truncateButton;

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
        
        truncateButton = (Button) findViewById(R.id.truncate_button);
        truncateButton.setOnClickListener(this);
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	db.close();
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
		db = new DatabaseHelper(this).getReadableDatabase();
    }
    
	public void onClick(View v) {
		if (v == pushButton) {
			if(post_is_running == false) {
				post_is_running = true;
				
				bindService(new Intent(syncerMain.this, syncService.class), syncServiceConnection, Context.BIND_AUTO_CREATE);
				
				((Button) findViewById(R.id.push_button)).setText("Starting..");
			}
			else {
				post_is_running = false;
				unbindService(syncServiceConnection);
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
		
		if (v == truncateButton) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Are you sure? This will delete all the TimeSync-records from SQLite.")
			       .setCancelable(false)
			       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   db.execSQL("DELETE FROM sync_results;");
			        	   Toast.makeText(getApplicationContext(), "Deleted!", Toast.LENGTH_SHORT).show();
			           }
			       })
			       .setNegativeButton("No", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.cancel();
			           }
			       });
			AlertDialog alert = builder.create();
			alert.show();
		}
			
	}
	
	
	private ServiceConnection syncServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			boundService = ((syncService.LocalBinder)service).getService();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
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
	
	
	
}