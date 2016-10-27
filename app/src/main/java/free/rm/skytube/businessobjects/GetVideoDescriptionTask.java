package free.rm.skytube.businessobjects;

import java.io.IOException;
import java.util.List;

import free.rm.skytube.R;
import free.rm.skytube.gui.app.SkyTubeApp;
import free.rm.skytube.gui.businessobjects.Logger;

/**
 * Get the video's description and set the appropriate text view.
 */
public class GetVideoDescriptionTask extends AsyncTaskParallel<Void, Void, String> {
	private YouTubeVideo youTubeVideo;
	private GetVideoDescriptionTaskListener listener;

	public interface GetVideoDescriptionTaskListener {
		void onFinished(String description);
	}

	public GetVideoDescriptionTask(YouTubeVideo youTubeVideo, GetVideoDescriptionTaskListener listener) {
		this.youTubeVideo = youTubeVideo;
		this.listener = listener;
	}

	@Override
	protected String doInBackground(Void... params) {
		GetVideoDescription getVideoDescription = new GetVideoDescription();
		String description = SkyTubeApp.getStr(R.string.error_get_video_desc);

		try {
			getVideoDescription.init(youTubeVideo.getId());
			List<YouTubeVideo> list = getVideoDescription.getNextVideos();

			if (list.size() > 0) {
				description = list.get(0).getDescription();
			}
		} catch (IOException e) {
			Logger.e(description + " - id=" + youTubeVideo.getId(), e);
		}

		return description;
	}

	@Override
	protected void onPostExecute(String description) {
		if(listener instanceof GetVideoDescriptionTaskListener)
			listener.onFinished(description);
	}

}