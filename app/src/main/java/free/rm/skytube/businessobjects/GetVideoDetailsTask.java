package free.rm.skytube.businessobjects;

import android.content.Intent;
import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import free.rm.skytube.businessobjects.interfaces.GetVideoDetailsListener;

/**
 * This task will, from the given video URL, get the details of the video (e.g. video name,
 * likes ...etc).
 */
public class GetVideoDetailsTask extends AsyncTaskParallel<Void, Void, YouTubeVideo> {

	private String videoUrl = null;
	private GetVideoDetailsListener listener;
	private boolean includeDescription = false;
	private static final String TAG = GetVideoDetailsTask.class.getSimpleName();

	public GetVideoDetailsTask(String videoUrl, GetVideoDetailsListener listener) {
		this.videoUrl = videoUrl;
		this.listener = listener;
	}

	public GetVideoDetailsTask(Intent intent, boolean includeDescription, GetVideoDetailsListener listener) {
		String url = getUrlFromIntent(intent);

		try {
			// YouTube sends subscriptions updates email in which its videos' URL are encoded...
			// Hence we need to decode them first...
			videoUrl = URLDecoder.decode(url, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			Log.e(TAG, "UnsupportedEncodingException on " + videoUrl + " encoding = UTF-8", e);
			videoUrl = url;
		}
		this.listener = listener;
		this.includeDescription = includeDescription;
	}

	public GetVideoDetailsTask(Intent intent, GetVideoDetailsListener listener) {
		this(intent, false, listener);
	}

	/**
	 * Returns an instance of {@link YouTubeVideo} from the given {@link #videoUrl}.
	 *
	 * @return {@link YouTubeVideo}; null if an error has occurred.
	 */
	@Override
	protected YouTubeVideo doInBackground(Void... params) {
		String videoId = getYouTubeIdFromUrl(videoUrl);
		YouTubeVideo youTubeVideo = null;

		if (videoId != null) {
			try {
				GetVideosDetailsByIDs getVideo = new GetVideosDetailsByIDs();
				getVideo.init(videoId, includeDescription);
				List<YouTubeVideo> youTubeVideos = getVideo.getNextVideos();

				if (youTubeVideos.size() > 0)
					youTubeVideo = youTubeVideos.get(0);
			} catch (IOException ex) {
//				Logger.e("Unable to get video details, where id="+videoId, ex);
			}
		}

		return youTubeVideo;
	}


	@Override
	protected void onPostExecute(YouTubeVideo youTubeVideo) {
		if (youTubeVideo == null) {
			// invalid URL error (i.e. we are unable to decode the URL)
			if(listener != null)
				listener.onFailure(videoUrl);
		} else {
			if(listener != null)
				listener.onSuccess(youTubeVideo);
		}
	}


	/**
	 * The video URL is passed to SkyTube via another Android app (i.e. via an intent).
	 *
	 * @return The URL of the YouTube video the user wants to play.
	 */
	private String getUrlFromIntent(final Intent intent) {
		String url = null;

		if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
			url = intent.getData().toString();
		}

		return url;
	}


	/**
	 * Extracts the video ID from the given video URL.
	 *
	 * @param url	YouTube video URL.
	 * @return ID if everything went as planned; null otherwise.
	 */
	private String getYouTubeIdFromUrl(String url) {
		if (url == null)
			return null;

		final String pattern = "(?<=v=|/videos/|embed/|youtu\\.be/|/v/|/e/)[^#&\\?]*";
		Pattern compiledPattern = Pattern.compile(pattern);
		Matcher matcher = compiledPattern.matcher(url);

		return matcher.find() ? matcher.group() /*video id*/ : null;
	}

}