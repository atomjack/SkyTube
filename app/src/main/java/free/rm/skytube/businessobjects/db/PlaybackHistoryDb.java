package free.rm.skytube.businessobjects.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.gson.Gson;

import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.businessobjects.Logger;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeVideo;

/**
 * A database (DB) that stores video playback history
 */
public class PlaybackHistoryDb extends SQLiteOpenHelperEx {
	private static volatile PlaybackHistoryDb playbackHistoryDb = null;
	private static boolean hasUpdated = false;

	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "playbackhistory.db";

	public static synchronized PlaybackHistoryDb getVideoDownloadsDb() {
		if (playbackHistoryDb == null) {
			playbackHistoryDb = new PlaybackHistoryDb(SkyTubeApp.getContext());
		}

		return playbackHistoryDb;
	}

	private PlaybackHistoryDb(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	protected void clearDatabaseInstance() {

	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(PlaybackHistoryTable.getCreateStatement());
	}

	@Override
	public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

	}

	public long getVideoPosition(YouTubeVideo video) {
		Cursor cursor = getReadableDatabase().query(
						PlaybackHistoryTable.TABLE_NAME,
						new String[]{PlaybackHistoryTable.COL_YOUTUBE_VIDEO_POSITION},
						PlaybackHistoryTable.COL_YOUTUBE_VIDEO_ID + " = ?",
						new String[]{video.getId()}, null, null, null);

		long position = -1;
		if (cursor.moveToNext()) {
			position = cursor.getLong(cursor.getColumnIndex(PlaybackHistoryTable.COL_YOUTUBE_VIDEO_POSITION));
			Logger.d(this, "duration of %s: %d", video.getTitle(), video.getDurationInSeconds());
//			if(video.getDuration())
		}
		cursor.close();
		return position;
	}

	public boolean setVideoPosition(YouTubeVideo video, long position) {
		Gson gson = new Gson();
		ContentValues values = new ContentValues();
		values.put(PlaybackHistoryTable.COL_YOUTUBE_VIDEO_ID, video.getId());
		values.put(PlaybackHistoryTable.COL_YOUTUBE_VIDEO, gson.toJson(video).getBytes());
		values.put(PlaybackHistoryTable.COL_YOUTUBE_VIDEO_POSITION, (int)position);
		values.put(PlaybackHistoryTable.COL_YOUTUBE_VIDEO_FINISHED, 0); // TODO: Set this properly

		boolean addSuccessful = getWritableDatabase().replace(PlaybackHistoryTable.TABLE_NAME, null, values) != -1;
		return addSuccessful;
	}
}
