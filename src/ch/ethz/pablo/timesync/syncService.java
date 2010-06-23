package ch.ethz.pablo.timesync;

import java.io.IOException;

import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;



public class syncService extends Service  {
	public static final String BROADCAST_ACTION=
		"ch.ethz.pablo.timesync.TimeSyncEvent";
	
	private static final String TAG = "HttpSyncService";
	private static final String DB_TABLENAME = "sync_results";
	
	
	private final Binder binder = new LocalBinder();

	private boolean mStopSyncing = false;
	
	private Handler mHandler = new Handler();
	
	private SQLiteDatabase db;


    public class LocalBinder extends Binder {
        syncService getService() {
            return syncService.this;
        }
    }

	
	@Override
	public void onCreate() {
		
		Log.i(TAG, "onCreate called");
		showNotification();
		
//        params = new BasicHttpParams();
//        params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
//        
//        httpclient = new DefaultHttpClient(params);
//        stringResponseHandler = new BasicResponseHandler();
		
		// get database
		db = new DatabaseHelper(this).getWritableDatabase();
	}
	


//	@Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        Log.i(TAG, "Received start id " + startId + ": " + intent);
//        // continue running until it is explicitly stopped, so return sticky.
//        return START_STICKY;
//    }

	
	@Override
	public void onDestroy() {
		Log.i(TAG, "onDestroy called");
		mStopSyncing = true;
		db.close();
	}
	
	
	@Override
	public IBinder onBind(Intent intent) {
		//TODO start syncing (or call onRebind?)
		return binder;
	}
	
//	public boolean onUnbind(Intent intent) {
//		//TODO stop syncing
//		return true; // return true to have onRebind() called when a new client binds
//	}
//	
//	public void onRebind(Intent intent) {
//		//TODO restart syncing
//	}

    private void showNotification() {
    	mHandler.post(syncThread);
//    	bgTask = (syncTask) new syncTask().execute();
	}
    
    
    private Runnable syncThread = new Runnable() {
    	
    	
    	private static final int DEFAULT_BACKOFF = 500;   // 0.5 seconds
    	private static final int MAX_BACKOFF =     60000; // a minute
    	
    	private int backoff = DEFAULT_BACKOFF;
    	
    	public void run() {
    		
    		try {
	        	long RTT;
	        	long timeDifference;
	        	
	        	Long[] syncResult = syncTime();
	
				RTT = syncResult[0];
				long serverTime = syncResult[1] + RTT/2;
				long myTime = syncResult[2];
				
				timeDifference = serverTime-myTime;
				
				Log.i(TAG, "Sync finished! RTT: " +syncResult[0]);
				Log.i(TAG, "Server: " + serverTime + " (raw: " + syncResult[1] + ")");
				Log.i(TAG, "Phone:  " + myTime);
				Log.i(TAG, "Time Difference:  " + timeDifference);
				
				
				ContentValues cv = new ContentValues(3);
				cv.put("ts", myTime);
				cv.put("offset", timeDifference);
				cv.put("rtt", RTT);
				db.insert(DB_TABLENAME, "ts", cv);
				
				backoff = 500; 
				
				// TODO: smarter exception-handling, 
				// e.g. backoff only for http-errors, ...
    		} catch (Exception e) {
    			Log.e(TAG, "Doh! " + e.toString());
    			
    			// exponential backoff up to a limit when something fails (e.g. server offline)
    			if(backoff < MAX_BACKOFF) {
    				backoff = 2*backoff; 
    			}
    		}
    		if(!mStopSyncing) {
    			mHandler.postDelayed(this, backoff);
    		}
    		else {
    			Log.i(TAG, "Stop Syncing");
    		}
    	}
    };	

    private Long[] syncTime() {
    	
        HttpParams params = new BasicHttpParams();
        params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
        
        HttpClient httpclient = new DefaultHttpClient(params);
//        HttpGet request = new HttpGet("http://192.168.1.137:8081/time");
//        HttpGet request = new HttpGet("http://pableu.dyndns.org:8080/time");
        HttpGet request = new HttpGet("http://129.132.131.73:8081/time");

        long roundTripTime = 0;

        long phoneTime = 0;
        long serverTime = 0;
        
        try {
	        // handler that gets string from response
	        ResponseHandler<String> stringResponseHandler = new BasicResponseHandler();
	        
	    	// get start time
	        long ms_before = SystemClock.uptimeMillis();
	        
	        // Execute HTTP Get Request
	        String stringResponse = httpclient.execute(request, stringResponseHandler);
	
	        // get current timestamp of the phone...
	        phoneTime = System.currentTimeMillis();
	        
	        // get end-time, calculate RTT
	        long ms_after = SystemClock.uptimeMillis();
	        roundTripTime = ms_after - ms_before;
	        
	    	serverTime = Long.parseLong(stringResponse);
	        
	        return new Long[] {roundTripTime, serverTime, phoneTime};
        } catch (IOException e) {
        	Log.w(TAG,e.toString());
        } catch (NumberFormatException e) {
        	Log.e(TAG, "Number Format Exception when parsing server Response: " + e.getMessage());
        } catch (Exception e) {
        	Log.w(TAG, e.toString());
        }
		return null;
    } 
	
}