package free.rm.skytube.businessobjects.interfaces;

import free.rm.skytube.businessobjects.YouTubeVideo;

public interface GetVideoDetailsListener {
	void onSuccess(YouTubeVideo video);
	void onFailure(String videoUrl);
}
