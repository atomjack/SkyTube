package free.rm.skytube.businessobjects.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;

import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.businessobjects.Logger;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeVideo;

/**
 * A database (DB) that stores video playback history
 */
public class PlaybackStatusDb extends SQLiteOpenHelperEx {
	private static volatile PlaybackStatusDb playbackStatusDb = null;
	private static HashMap<String, VideoWatchedStatus> playbackHistoryMap = null;

	private static final int DATABASE_VERSION = 1;
	private static boolean hasUpdated = false;
	private static final String DATABASE_NAME = "playbackhistory.db";

	public static synchronized PlaybackStatusDb getVideoDownloadsDb() {
		if (playbackStatusDb == null) {
			playbackStatusDb = new PlaybackStatusDb(SkyTubeApp.getContext());
		}

		return playbackStatusDb;
	}

	private PlaybackStatusDb(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	protected void clearDatabaseInstance() {

	}

	public void deleteAllPlaybackHistory() {
		getWritableDatabase().delete(PlaybackStatusTable.TABLE_NAME, null, null);
		playbackHistoryMap = null;
		hasUpdated = true;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(PlaybackStatusTable.getCreateStatement());
	}

	@Override
	public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

	}

	public VideoWatchedStatus getVideoWatchedStatus(YouTubeVideo video) {
		if(playbackHistoryMap == null) {
			Cursor cursor = getReadableDatabase().query(
							PlaybackStatusTable.TABLE_NAME,
							new String[]{PlaybackStatusTable.COL_YOUTUBE_VIDEO_ID, PlaybackStatusTable.COL_YOUTUBE_VIDEO_POSITION, PlaybackStatusTable.COL_YOUTUBE_VIDEO_WATCHED},
							null,
							null, null, null, null);
			playbackHistoryMap = new HashMap<>();
			if(cursor.moveToFirst()) {
				do {
					String video_id = cursor.getString(cursor.getColumnIndex(PlaybackStatusTable.COL_YOUTUBE_VIDEO_ID));
					int position = cursor.getInt(cursor.getColumnIndex(PlaybackStatusTable.COL_YOUTUBE_VIDEO_POSITION));
					int finished = cursor.getInt(cursor.getColumnIndex(PlaybackStatusTable.COL_YOUTUBE_VIDEO_WATCHED));
					VideoWatchedStatus status = new VideoWatchedStatus(position, finished == 1);
					playbackHistoryMap.put(video_id, status);
				} while (cursor.moveToNext());
			}
			cursor.close();
		}
		if(playbackHistoryMap.get(video.getId()) == null) {
			// Requested video has no entry in the database, so create one in the Map. No need to create it in the Database yet - if needed,
			// that will happen when video position is set
			VideoWatchedStatus status = new VideoWatchedStatus();
			playbackHistoryMap.put(video.getId(), status);
		}
		return playbackHistoryMap.get(video.getId());
	}

	public boolean setVideoPosition(YouTubeVideo video, long position) {
		// Don't record the position if it's < 5 seconds
		if(position < 5000)
			return false;

		int watched = 0;
		// If the user has stopped watching the video and the position is greater than 90% of the duration, mark the video as watched and reset position
		if((float)position / (video.getDurationInSeconds()*1000) >= 0.9) {
			watched = 1;
			position = 0;
		}

		ContentValues values = new ContentValues();
		values.put(PlaybackStatusTable.COL_YOUTUBE_VIDEO_ID, video.getId());
		values.put(PlaybackStatusTable.COL_YOUTUBE_VIDEO_POSITION, (int)position);
		values.put(PlaybackStatusTable.COL_YOUTUBE_VIDEO_WATCHED, watched);

		playbackHistoryMap.get(video.getId()).position = position;
		playbackHistoryMap.get(video.getId()).watched = watched == 1;

		boolean addSuccessful = getWritableDatabase().insertWithOnConflict(PlaybackStatusTable.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1;
		if(addSuccessful)
				hasUpdated = true;

		return addSuccessful;
	}

	public boolean setVideoWatchedStatus(YouTubeVideo video, boolean watched) {
		ContentValues values = new ContentValues();
		values.put(PlaybackStatusTable.COL_YOUTUBE_VIDEO_ID, video.getId());
		values.put(PlaybackStatusTable.COL_YOUTUBE_VIDEO_POSITION, 0);
		values.put(PlaybackStatusTable.COL_YOUTUBE_VIDEO_WATCHED, watched ? 1 : 0);

		playbackHistoryMap.get(video.getId()).watched = watched;
		playbackHistoryMap.get(video.getId()).position = 0;

		boolean success = getWritableDatabase().insertWithOnConflict(PlaybackStatusTable.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1;
		if(success)
			hasUpdated = true;
		return success;
	}

	public class VideoWatchedStatus {
		public VideoWatchedStatus() {}
		public VideoWatchedStatus(long position, boolean watched) {
			this.position = position;
			this.watched = watched;
		}

		@Override
		public String toString() {
			return String.format("Position: %d\nWatched: %s\n", position, watched);
		}

		public long position = 0;
		public boolean watched = false;
	}

	public static boolean isHasUpdated() {
		return hasUpdated;
	}

	public static void setHasUpdated(boolean hasUpdated) {
		PlaybackStatusDb.hasUpdated = hasUpdated;
	}
}
