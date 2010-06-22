package ch.ethz.pablo.timesync;


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class DatabaseHelper extends SQLiteOpenHelper {
	
	private final static String DATABASE_NAME = "db"; 
	
	public DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, 1);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE sync_results (" +
				"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
				"ts int, " +
				"offset int)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL("DROP TABLE IF EXISTS sync_results");
		onCreate(db);
	}
}