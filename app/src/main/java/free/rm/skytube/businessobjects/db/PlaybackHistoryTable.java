package free.rm.skytube.businessobjects.db;

public class PlaybackHistoryTable {
	public static final String TABLE_NAME = "PlaybackHistory";
	public static final String COL_YOUTUBE_VIDEO_ID = "YouTube_Video_Id";
	public static final String COL_YOUTUBE_VIDEO = "YouTube_Video";
	public static final String COL_YOUTUBE_VIDEO_POSITION = "YouTube_Video_Position"; // number of seconds at which this video should resume
	public static final String COL_YOUTUBE_VIDEO_FINISHED = "YouTube_Video_Finished";

	public static String getCreateStatement() {
		return "CREATE TABLE " + TABLE_NAME + " (" +
						COL_YOUTUBE_VIDEO_ID + " TEXT PRIMARY KEY NOT NULL, " +
						COL_YOUTUBE_VIDEO + " BLOB, " +
						COL_YOUTUBE_VIDEO_POSITION + " INT, " +
						COL_YOUTUBE_VIDEO_FINISHED + " INT " +
						" )";
	}
}

