package free.rm.skytube.gui.fragments;

import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.squareup.picasso.Picasso;

import java.io.IOException;

import free.rm.skytube.R;
import free.rm.skytube.businessobjects.AsyncTaskParallel;
import free.rm.skytube.businessobjects.GetVideoDescriptionTask;
import free.rm.skytube.businessobjects.GetVideoDetailsTask;
import free.rm.skytube.businessobjects.VideoStream.StreamMetaData;
import free.rm.skytube.businessobjects.YouTubeChannel;
import free.rm.skytube.businessobjects.YouTubeVideo;
import free.rm.skytube.businessobjects.db.CheckIfUserSubbedToChannelTask;
import free.rm.skytube.businessobjects.db.SubscribeToChannelTask;
import free.rm.skytube.businessobjects.interfaces.GetDesiredStreamListener;
import free.rm.skytube.businessobjects.interfaces.GetVideoDetailsListener;
import free.rm.skytube.gui.activities.MainActivity;
import free.rm.skytube.gui.businessobjects.CommentsAdapter;
import free.rm.skytube.gui.businessobjects.FragmentEx;
import free.rm.skytube.gui.businessobjects.MediaControllerEx;
import free.rm.skytube.gui.businessobjects.SubscribeButton;
import hollowsoft.slidingdrawer.OnDrawerOpenListener;
import hollowsoft.slidingdrawer.SlidingDrawer;

/**
 * A fragment that holds a standalone YouTube player.
 */
public class YouTubePlayerFragment extends FragmentEx implements MediaPlayer.OnPreparedListener {

	public static final String YOUTUBE_VIDEO_OBJ = "YouTubePlayerActivity.yt_video_obj";
	public static final String YOUTUBE_VIDEO_URL = "YouTubePlayerActivity.yt_video_url";

	private YouTubeVideo		youTubeVideo = null;
	private YouTubeChannel		youTubeChannel = null;

	private VideoView			videoView = null;
	private int					videoCurrentPosition = 0;
	private MediaControllerEx	mediaController = null;
	private TextView			videoDescTitleTextView = null;
	private ImageView			videoDescChannelThumbnailImageView = null;
	private TextView			videoDescChannelTextView = null;
	private SubscribeButton		videoDescSubscribeButton = null;
	private TextView			videoDescViewsTextView = null;
	private TextView			videoDescLikesTextView = null;
	private TextView			videoDescDislikesTextView = null;
	private TextView			videoDescPublishDateTextView = null;
	private TextView			videoDescriptionTextView = null;
	private ProgressBar			videoDescLikesBar = null;
	private View				voidView = null;
	private View				loadingVideoView = null;

	private SlidingDrawer		videoDescriptionDrawer = null;
	private SlidingDrawer		commentsDrawer = null;
	private View				commentsProgressBar = null,
								noVideoCommentsView = null;
	private CommentsAdapter		commentsAdapter = null;
	private ExpandableListView	commentsExpandableListView = null;

	private Handler				timerHandler = null;

	private static final int HUD_VISIBILITY_TIMEOUT = 7000;
	private static final String VIDEO_CURRENT_POSITION = "YouTubePlayerFragment.VideoCurrentPosition";
	private static final String TAG = YouTubePlayerFragment.class.getSimpleName();


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// inflate the layout for this fragment
		View view = inflater.inflate(R.layout.fragment_youtube_player, container, false);

		// indicate that this fragment has an action bar menu
		setHasOptionsMenu(true);

		if (savedInstanceState != null)
			videoCurrentPosition = savedInstanceState.getInt(VIDEO_CURRENT_POSITION, 0);

		if (youTubeVideo == null) {
			loadingVideoView = view.findViewById(R.id.loadingVideoView);

			videoView = (VideoView) view.findViewById(R.id.video_view);
			// play the video once its loaded
			videoView.setOnPreparedListener(this);

			// setup the media controller (will control the video playing/pausing)
			mediaController = new MediaControllerEx(getActivity(), videoView);

			voidView = view.findViewById(R.id.void_view);
			voidView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showOrHideHud();
				}
			});

			videoDescriptionDrawer = (SlidingDrawer) view.findViewById(R.id.des_drawer);
			videoDescTitleTextView = (TextView) view.findViewById(R.id.video_desc_title);
			videoDescChannelThumbnailImageView = (ImageView) view.findViewById(R.id.video_desc_channel_thumbnail_image_view);
			videoDescChannelThumbnailImageView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (youTubeChannel != null) {
						Intent i = new Intent(getActivity(), MainActivity.class);
						i.setAction(MainActivity.ACTION_VIEW_CHANNEL);
						i.putExtra(ChannelBrowserFragment.CHANNEL_OBJ, youTubeChannel);
						startActivity(i);
					}
				}
			});
			videoDescChannelTextView = (TextView) view.findViewById(R.id.video_desc_channel);
			videoDescViewsTextView = (TextView) view.findViewById(R.id.video_desc_views);
			videoDescLikesTextView = (TextView) view.findViewById(R.id.video_desc_likes);
			videoDescDislikesTextView = (TextView) view.findViewById(R.id.video_desc_dislikes);
			videoDescPublishDateTextView = (TextView) view.findViewById(R.id.video_desc_publish_date);
			videoDescriptionTextView = (TextView) view.findViewById(R.id.video_desc_description);
			videoDescLikesBar = (ProgressBar) view.findViewById(R.id.video_desc_likes_bar);
			videoDescSubscribeButton = (SubscribeButton) view.findViewById(R.id.video_desc_subscribe_button);
			videoDescSubscribeButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					// subscribe / unsubscribe to this video's channel
					new SubscribeToChannelTask(videoDescSubscribeButton, youTubeChannel).execute();
				}
			});

			commentsExpandableListView = (ExpandableListView) view.findViewById(R.id.commentsExpandableListView);
			commentsProgressBar = view.findViewById(R.id.comments_progress_bar);
			noVideoCommentsView = view.findViewById(R.id.no_video_comments_text_view);
			commentsDrawer = (SlidingDrawer) view.findViewById(R.id.comments_drawer);
			commentsDrawer.setOnDrawerOpenListener(new OnDrawerOpenListener() {
				@Override
				public void onDrawerOpened() {
					if (commentsAdapter == null) {
						commentsAdapter = new CommentsAdapter(getActivity(), youTubeVideo.getId(), commentsExpandableListView, commentsProgressBar, noVideoCommentsView);
					}
				}
			});

			// hide action bar
			getSupportActionBar().hide();

			// get which video we need to play...
			Bundle bundle = getActivity().getIntent().getExtras();
			if (bundle != null  &&  bundle.getSerializable(YOUTUBE_VIDEO_OBJ) != null) {
				// ... either the video details are passed through the previous activity
				youTubeVideo = (YouTubeVideo) bundle.getSerializable(YOUTUBE_VIDEO_OBJ);
				setUpHUDAndPlayVideo();

				getVideoInfoTasks();
			} else {
				// ... or the video URL is passed to SkyTube via another Android app
				GetVideoDetailsTask getVideoDetailsTask = new GetVideoDetailsTask(getActivity().getIntent(), getVideoDetailsListener);
				getVideoDetailsTask.executeInParallel();
			}
		}

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(VIDEO_CURRENT_POSITION, videoCurrentPosition);
	}

	private void getVideoInfoTasks() {
		// get Channel info (e.g. avatar...etc) task
		new GetYouTubeChannelInfoTask().executeInParallel(youTubeVideo.getChannelId());

		// check if the user has subscribed to a channel... if he has, then change the state of
		// the subscribe button
		new CheckIfUserSubbedToChannelTask(videoDescSubscribeButton, youTubeVideo.getChannelId()).execute();
	}


	/**
	 * Will setup the HUD's details according to the contents of {@link #youTubeVideo}.  Then it
	 * will try to load and play the video.
	 */
	private void setUpHUDAndPlayVideo() {
		videoDescTitleTextView.setText(youTubeVideo.getTitle());
		videoDescChannelTextView.setText(youTubeVideo.getChannelName());
		videoDescViewsTextView.setText(youTubeVideo.getViewsCount());

		if (youTubeVideo.isThumbsUpPercentageSet()) {
			videoDescLikesTextView.setText(youTubeVideo.getLikeCount());
			videoDescDislikesTextView.setText(youTubeVideo.getDislikeCount());
			videoDescPublishDateTextView.setText(youTubeVideo.getPublishDatePretty());

			videoDescLikesBar.setProgress(youTubeVideo.getThumbsUpPercentage());
			//videoDescLikesBar.getProgressDrawable().setColorFilter(getResources().getColor(R.color.video_desc_like_bar), PorterDuff.Mode.SRC_IN);
		}

		// load the video
		loadVideo();
	}



	@Override
	public void onPrepared(MediaPlayer mediaPlayer) {
		loadingVideoView.setVisibility(View.GONE);
		videoView.seekTo(videoCurrentPosition);
		videoView.start();
		showHud();
	}



	@Override
	public void onPause() {
		if (videoView != null && videoView.isPlaying()) {
			videoCurrentPosition = videoView.getCurrentPosition();
		}

		super.onPause();
	}



	/**
	 * @return True if the HUD is visible (provided that this Fragment is also visible).
	 */
	private boolean isHudVisible() {
		return isVisible()  &&  (mediaController.isShowing()  ||  getSupportActionBar().isShowing());
	}



	/**
	 * Hide or display the HUD depending if the HUD is currently visible or not.
	 */
	private void showOrHideHud() {
		if (isHudVisible())
			hideHud();
		else
			showHud();
	}



	/**
	 * Show the HUD (head-up display), i.e. the Action Bar and Media Controller.
	 */
	private void showHud() {
		if (!isHudVisible()) {
			getSupportActionBar().show();
			getSupportActionBar().setTitle(youTubeVideo.getTitle());
			mediaController.show(0);

			videoDescriptionDrawer.close();
			videoDescriptionDrawer.setVisibility(View.INVISIBLE);
			commentsDrawer.close();
			commentsDrawer.setVisibility(View.INVISIBLE);

			// hide UI after a certain timeout (defined in UI_VISIBILITY_TIMEOUT)
			timerHandler = new Handler();
			timerHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					hideHud();
					timerHandler = null;
				}
			}, HUD_VISIBILITY_TIMEOUT);
		}
	}



	/**
	 * Hide the HUD.
	 */
	private void hideHud() {
		if (isHudVisible()) {
			getSupportActionBar().hide();
			mediaController.hide();

			videoDescriptionDrawer.setVisibility(View.VISIBLE);
			commentsDrawer.setVisibility(View.VISIBLE);

			// If there is a timerHandler running, then cancel it (stop if from running).  This way,
			// if the HUD was hidden on the 5th second, and the user reopens the HUD, this code will
			// prevent the HUD to re-disappear 2 seconds after it was displayed (assuming that
			// UI_VISIBILITY_TIMEOUT = 7 seconds).
			if (timerHandler != null) {
				timerHandler.removeCallbacksAndMessages(null);
				timerHandler = null;
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_reload_video:
				loadVideo();
				return true;
			case R.id.menu_open_video_with:
				Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v="+youTubeVideo.getId()));
				startActivity(browserIntent);
				videoView.pause();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}


	/**
	 * Loads the video specified in {@link #youTubeVideo}.
	 */
	private void loadVideo() {
		boolean isVideoPlaying = videoView.isPlaying();

		videoView.pause();
		final int currentVideoPosition = isVideoPlaying ? videoView.getCurrentPosition() : 0;
		videoView.stopPlayback();
		loadingVideoView.setVisibility(View.VISIBLE);

		youTubeVideo.getDesiredStream(new GetDesiredStreamListener() {
			@Override
			public void onGetDesiredStream(StreamMetaData desiredStream) {
				// play the video
				Log.i(TAG, ">> PLAYING: " + desiredStream);
				videoView.setVideoURI(desiredStream.getUri());

				// if we are reloading a video... then seek the correct position
				if (currentVideoPosition >= 0) {
					videoView.seekTo(currentVideoPosition);
				}
			}

				@Override
			public void onGetDesiredStreamError(String errorMessage) {
				if (errorMessage != null) {
					new AlertDialog.Builder(getContext())
									.setMessage(errorMessage)
									.setTitle(R.string.error_video_play)
									.setCancelable(false)
									.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
										@Override
										public void onClick(DialogInterface dialog, int which) {
											getActivity().finish();
										}
									})
									.show();
				}
			}
		});
		// get the video description
		new GetVideoDescriptionTask(youTubeVideo, new GetVideoDescriptionTask.GetVideoDescriptionTaskListener() {
			@Override
			public void onFinished(String description) {
				videoDescriptionTextView.setText(description);
			}
		}).executeInParallel();
	}


	////////////////////////////////////////////////////////////////////////////////////////////////


	////////////////////////////////////////////////////////////////////////////////////////////////

	private GetVideoDetailsListener getVideoDetailsListener = new GetVideoDetailsListener() {
		@Override
		public void onSuccess(YouTubeVideo video) {
			YouTubePlayerFragment.this.youTubeVideo = video;
			setUpHUDAndPlayVideo();	// setup the HUD and play the video

			getVideoInfoTasks();
		}

		@Override
		public void onFailure(String videoUrl) {
			String err = String.format(getString(R.string.error_invalid_url), videoUrl);
			Toast.makeText(getActivity(), err, Toast.LENGTH_LONG).show();
			Log.e(TAG, err);
			getActivity().finish();
		}
	};

	////////////////////////////////////////////////////////////////////////////////////////////////


	private class GetYouTubeChannelInfoTask extends AsyncTaskParallel<String, Void, YouTubeChannel> {

		private final String TAG = GetYouTubeChannelInfoTask.class.getSimpleName();

		@Override
		protected YouTubeChannel doInBackground(String... channelId) {
			YouTubeChannel chn = new YouTubeChannel();

			try {
				chn.init(channelId[0]);
			} catch (IOException e) {
				Log.e(TAG, "Unable to get channel info.  ChannelID=" + channelId[0], e);
				chn = null;
			}

			return chn;
		}

		@Override
		protected void onPostExecute(YouTubeChannel youTubeChannel) {
			YouTubePlayerFragment.this.youTubeChannel = youTubeChannel;

			if (youTubeChannel != null) {
				Picasso.with(getActivity())
						.load(youTubeChannel.getThumbnailNormalUrl())
						.placeholder(R.drawable.channel_thumbnail_default)
						.into(videoDescChannelThumbnailImageView);
			}
		}

	}


	public YouTubeVideo getYouTubeVideo() {
		return youTubeVideo;
	}

	public int getCurrentVideoPosition() {
		return videoView.getCurrentPosition();
	}

	public void pause() {
		videoView.pause();
	}
}
