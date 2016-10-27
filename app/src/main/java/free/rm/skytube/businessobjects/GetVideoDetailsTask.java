package free.rm.skytube.businessobjects;

import android.content.Intent;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import free.rm.skytube.businessobjects.interfaces.GetVideoDetailsListener;
import free.rm.skytube.gui.businessobjects.Logger;

/**
 * This task will, from the given video URL, get the details of the video (e.g. video name,
 * likes ...etc).
 */
public class GetVideoDetailsTask extends AsyncTaskParallel<Void, Void, YouTubeVideo> {

	private String videoUrl = null;
	private GetVideoDetailsListener listener;
	private boolean includeDescription = false;

	public GetVideoDetailsTask(String videoUrl, GetVideoDetailsListener listener) {
		this.videoUrl = videoUrl;
		this.listener = listener;
	}

	public GetVideoDetailsTask(Intent intent, boolean includeDescription, GetVideoDetailsListener listener) {
		videoUrl = getUrlFromIntent(intent);
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
				Logger.e("Unable to get video details, where id="+videoId, ex);
			}
		}

		return youTubeVideo;
	}


	@Override
	protected void onPostExecute(YouTubeVideo youTubeVideo) {
		if (youTubeVideo == null) {
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