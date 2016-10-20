package free.rm.skytube.businessobjects;

public interface ChromecastListener {
	void playVideoOnChromecast(YouTubeVideo video, int position);
	void showLoadingSpinner();
	void onPlayStarted();
	void onPlayStopped();
	void redrawPanel();
//	void showChromecastOverlay();
}
