package ch.ethz.pablo.timesync;


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;


public class DatabaseHelper extends SQLiteOpenHelper {
	
	private final static String DATABASE_NAME = "db"; 
	private final static String TAG = "HttpTimeSyncDB";
	
	public DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, 2);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE sync_results (" +
				"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
				"ts int, " +
				"offset int, " +
				"rtt int);");
		Log.i(TAG, "created SQLite-DB");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		if (oldVersion == 1 && newVersion == 2) {
			db.execSQL("ALTER TABLE sync_results ADD COLUMN rtt int;");
		} else {
			db.execSQL("DROP TABLE IF EXISTS sync_results;");
			onCreate(db);
		}
	}
}