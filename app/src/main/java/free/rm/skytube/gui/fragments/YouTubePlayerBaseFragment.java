package free.rm.skytube.gui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.List;

import free.rm.skytube.R;
import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeChannel;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeVideo;
import free.rm.skytube.businessobjects.YouTube.VideoBlocker;
import free.rm.skytube.businessobjects.db.Tasks.IsVideoBookmarkedTask;
import free.rm.skytube.businessobjects.interfaces.YouTubePlayerFragmentInterface;
import free.rm.skytube.gui.activities.ThumbnailViewerActivity;
import free.rm.skytube.gui.businessobjects.SubscribeButton;
import free.rm.skytube.gui.businessobjects.adapters.CommentsAdapter;
import free.rm.skytube.gui.businessobjects.fragments.ImmersiveModeFragment;
import hollowsoft.slidingdrawer.SlidingDrawer;

public abstract class YouTubePlayerBaseFragment extends ImmersiveModeFragment implements YouTubePlayerFragmentInterface {
	public static final String YOUTUBE_VIDEO_INDEX= "YouTubePlayerFragment.yt_video_index";
	public static final String YOUTUBE_VIDEO_LIST = "YouTubePlayerFragment.yt_video_list";

	protected List<YouTubeVideo> videoList;
	protected int currentVideoIndex = 0;
	//	protected YouTubeVideo youTubeVideo = null;
	protected YouTubeChannel youTubeChannel = null;

	protected Menu menu = null;

	protected View				loadingVideoView = null;
	protected SlidingDrawer		commentsDrawer = null;
	protected View				commentsProgressBar = null,
					noVideoCommentsView = null;

	protected CommentsAdapter commentsAdapter = null;
	protected ExpandableListView commentsExpandableListView = null;


	protected TextView videoDescTitleTextView = null;
	protected ImageView videoDescChannelThumbnailImageView = null;
	protected TextView			videoDescChannelTextView = null;
	protected SubscribeButton videoDescSubscribeButton = null;
	protected TextView			videoDescViewsTextView = null;
	protected ProgressBar videoDescLikesBar = null;
	protected TextView			videoDescLikesTextView = null;
	protected TextView			videoDescDislikesTextView = null;
	protected View videoDescRatingsDisabledTextView = null;
	protected TextView			videoDescPublishDateTextView = null;
	protected TextView			videoDescriptionTextView = null;
	protected RelativeLayout voidView = null;

	protected SlidingDrawer videoDescriptionDrawer = null;

	protected boolean autoPlayNextVideo = false;

	/**
	 * Will setup the HUD's details according to the contents of the passed {@link YouTubeVideo}.
	 */
	protected void setUpHUD(YouTubeVideo youTubeVideo) {
		videoDescTitleTextView.setText(youTubeVideo.getTitle());
		videoDescChannelTextView.setText(youTubeVideo.getChannelName());
		videoDescViewsTextView.setText(youTubeVideo.getViewsCount());
		videoDescPublishDateTextView.setText(youTubeVideo.getPublishDatePretty());

		if (youTubeVideo.isThumbsUpPercentageSet()) {
			videoDescLikesTextView.setText(youTubeVideo.getLikeCount());
			videoDescDislikesTextView.setText(youTubeVideo.getDislikeCount());
			videoDescLikesBar.setProgress(youTubeVideo.getThumbsUpPercentage());
		} else {
			videoDescLikesTextView.setVisibility(View.GONE);
			videoDescDislikesTextView.setVisibility(View.GONE);
			videoDescLikesBar.setVisibility(View.GONE);
			videoDescRatingsDisabledTextView.setVisibility(View.VISIBLE);
		}
	}

	protected abstract void loadVideos();

	/**
	 * Play the video using an external app
	 */
	protected void playVideoExternally() {
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoList.get(currentVideoIndex).getVideoUrl()));
		startActivity(browserIntent);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		// Hide the download video option if mobile downloads are not allowed and the device is connected through mobile, and the video isn't already downloaded
		boolean allowDownloadsOnMobile = SkyTubeApp.getPreferenceManager().getBoolean(SkyTubeApp.getStr(R.string.pref_key_allow_mobile_downloads), false);
		if(videoList != null && (videoList.get(currentVideoIndex) != null && !videoList.get(currentVideoIndex).isDownloaded()) && (SkyTubeApp.isConnectedToWiFi() || (SkyTubeApp.isConnectedToMobile() && allowDownloadsOnMobile))) {
			menu.findItem(R.id.download_video).setVisible(true);
		} else {
			menu.findItem(R.id.download_video).setVisible(false);
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_youtube_player, menu);

		this.menu = menu;

		// Will now check if the video is bookmarked or not (and then update the menu accordingly).
		//
		// youTubeVideo might be null if we have only passed the video URL to this fragment (i.e.
		// the app is still trying to construct youTubeVideo in the background).
		if (videoList != null && videoList.get(currentVideoIndex) != null)
			new IsVideoBookmarkedTask(videoList.get(currentVideoIndex), menu).executeInParallel();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_reload_video:
				loadVideos();
				return true;

			case R.id.play_next_video:
				item.setChecked(!item.isChecked());
				autoPlayNextVideo = item.isChecked();
				return true;

			case R.id.menu_open_video_with:
				playVideoExternally();
				stop();
				return true;

			case R.id.share:
				videoList.get(currentVideoIndex).shareVideo(getContext());
				return true;

			case R.id.copyurl:
				videoList.get(currentVideoIndex).copyUrl(getContext());
				return true;

			case R.id.bookmark_video:
				videoList.get(currentVideoIndex).bookmarkVideo(getContext(), menu);
				return true;

			case R.id.unbookmark_video:
				videoList.get(currentVideoIndex).unbookmarkVideo(getContext(), menu);
				return true;

			case R.id.view_thumbnail:
				Intent i = new Intent(getActivity(), ThumbnailViewerActivity.class);
				i.putExtra(ThumbnailViewerActivity.YOUTUBE_VIDEO, videoList.get(currentVideoIndex));
				startActivity(i);
				return true;

			case R.id.download_video:
				videoList.get(currentVideoIndex).downloadVideo(getContext());
				return true;

				// TODO: youTubeChannel is probably null here
			case R.id.block_channel:
				VideoBlocker.blockChannel(youTubeChannel.getId(), youTubeChannel.getTitle());

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	abstract void stop();

	/**
	 * The video URL is passed to SkyTube via another Android app (i.e. via an intent).
	 *
	 * @return The URL of the YouTube video the user wants to play.
	 */
	protected String getUrlFromIntent(final Intent intent) {
		String url = null;

		if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
			url = intent.getData().toString();
		}

		return url;
	}
}
