package free.rm.skytube.gui.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.bumptech.glide.Glide;

import free.rm.skytube.R;
import free.rm.skytube.businessobjects.GetVideoDescriptionTask;
import free.rm.skytube.businessobjects.GetVideoDetailsTask;
import free.rm.skytube.businessobjects.GetYouTubeChannelInfoTask;
import free.rm.skytube.businessobjects.VideoStream.StreamMetaData;
import free.rm.skytube.businessobjects.YouTubeChannel;
import free.rm.skytube.businessobjects.YouTubeChannelInterface;
import free.rm.skytube.businessobjects.YouTubeVideo;
import free.rm.skytube.businessobjects.db.CheckIfUserSubbedToChannelTask;
import free.rm.skytube.businessobjects.db.SubscribeToChannelTask;
import free.rm.skytube.businessobjects.interfaces.GetDesiredStreamListener;
import free.rm.skytube.businessobjects.interfaces.GetVideoDetailsListener;
import free.rm.skytube.businessobjects.interfaces.YouTubePlayerActivityListener;
import free.rm.skytube.gui.activities.MainActivity;
import free.rm.skytube.gui.businessobjects.CommentsAdapter;
import free.rm.skytube.gui.businessobjects.FragmentEx;
import free.rm.skytube.gui.businessobjects.IsVideoBookmarkedTask;
import free.rm.skytube.gui.businessobjects.MediaControllerEx;
import free.rm.skytube.gui.businessobjects.SubscribeButton;
import hollowsoft.slidingdrawer.OnDrawerOpenListener;
import hollowsoft.slidingdrawer.SlidingDrawer;

/**
 * A fragment that holds a standalone YouTube player.
 */
public class YouTubePlayerFragment extends FragmentEx implements MediaPlayer.OnPreparedListener {

	public static final String YOUTUBE_VIDEO_OBJ = "YouTubePlayerFragment.yt_video_obj";

	private YouTubeVideo		youTubeVideo = null;
	private YouTubeChannel		youTubeChannel = null;

	private VideoView			videoView = null;
	/** The current video position (i.e. play time). */
	private int					videoCurrentPosition = 0;
	private MediaControllerEx	mediaController = null;

	private TextView			videoDescTitleTextView = null;
	private ImageView			videoDescChannelThumbnailImageView = null;
	private TextView			videoDescChannelTextView = null;
	private SubscribeButton		videoDescSubscribeButton = null;
	private TextView			videoDescViewsTextView = null;
	private ProgressBar			videoDescLikesBar = null;
	private TextView			videoDescLikesTextView = null;
	private TextView			videoDescDislikesTextView = null;
	private View                videoDescRatingsDisabledTextView = null;
	private TextView			videoDescPublishDateTextView = null;
	private TextView			videoDescriptionTextView = null;
	private View				voidView = null;
	private View				loadingVideoView = null;

	private SlidingDrawer		videoDescriptionDrawer = null;
	private SlidingDrawer		commentsDrawer = null;
	private View				commentsProgressBar = null,
								noVideoCommentsView = null;
	private CommentsAdapter		commentsAdapter = null;
	private ExpandableListView	commentsExpandableListView = null;

	private YouTubePlayerActivityListener listener = null;

	private Menu menu = null;

	private Handler				timerHandler = null;

	/** Timeout (in milliseconds) before the HUD (i.e. media controller + action/title bar) is hidden */
	private static final int HUD_VISIBILITY_TIMEOUT = 5000;
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
			videoDescRatingsDisabledTextView = view.findViewById(R.id.video_desc_ratings_disabled);
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
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			Activity activity = (Activity)context;
			listener = (YouTubePlayerActivityListener)activity;
		} catch (ClassCastException e) {
			throw new ClassCastException("YouTubePlayerFragment must be instantiated from an Activity that implements YouTubePlayerActivityListener");
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(VIDEO_CURRENT_POSITION, videoCurrentPosition);
	}


	/**
	 * Will asynchronously retrieve additional video information such as channgel avatar ...etc
	 */
	private void getVideoInfoTasks() {
		// get Channel info (e.g. avatar...etc) task
		new GetYouTubeChannelInfoTask(new YouTubeChannelInterface() {
			@Override
			public void onGetYouTubeChannel(YouTubeChannel youTubeChannel) {
				YouTubePlayerFragment.this.youTubeChannel = youTubeChannel;

				if (youTubeChannel != null) {
					Glide.with(getActivity())
									.load(youTubeChannel.getThumbnailNormalUrl())
									.placeholder(R.drawable.channel_thumbnail_default)
									.into(videoDescChannelThumbnailImageView);
				}
			}
		}).executeInParallel(youTubeVideo.getChannelId());

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
			getSupportActionBar().setTitle(youTubeVideo != null ? youTubeVideo.getTitle() : "");
			mediaController.show(0);

			videoDescriptionDrawer.close();
			videoDescriptionDrawer.setVisibility(View.INVISIBLE);
			commentsDrawer.close();
			commentsDrawer.setVisibility(View.INVISIBLE);

			// hide UI after a certain timeout (defined in HUD_VISIBILITY_TIMEOUT)
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
			mediaController.hideController();

			videoDescriptionDrawer.setVisibility(View.VISIBLE);
			commentsDrawer.setVisibility(View.VISIBLE);

			// If there is a timerHandler running, then cancel it (stop if from running).  This way,
			// if the HUD was hidden on the 5th second, and the user reopens the HUD, this code will
			// prevent the HUD to re-disappear 2 seconds after it was displayed (assuming that
			// HUD_VISIBILITY_TIMEOUT = 5 seconds).
			if (timerHandler != null) {
				timerHandler.removeCallbacksAndMessages(null);
				timerHandler = null;
			}
		}
	}


	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_youtube_player, menu);

		this.menu = menu;

		listener.onOptionsMenuCreated(menu);

		// Will now check if the video is bookmarked or not (and then update the menu accordingly).
		//
		// youTubeVideo might be null if we have only passed the video URL to this fragment (i.e.
		// the app is still trying to construct youTubeVideo in the background).
		if (youTubeVideo != null)
			new IsVideoBookmarkedTask(youTubeVideo, menu).executeInParallel();
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_reload_video:
				loadVideo();
				return true;
			
			case R.id.menu_open_video_with:
				playVideoExternally();
				videoView.pause();
				return true;

			case R.id.share:
				youTubeVideo.shareVideo(getContext());
				return true;

			case R.id.copyurl:
				youTubeVideo.copyUrl(getContext());
				return true;

			case R.id.bookmark_video:
				youTubeVideo.bookmarkVideo(getContext(), menu);
				return true;

			case R.id.unbookmark_video:
				youTubeVideo.unbookmarkVideo(getContext(), menu);
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}


	/**
	 * Play the video using an external app
	 */
	private void playVideoExternally() {
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(youTubeVideo.getVideoUrl()));
		startActivity(browserIntent);
	}


	/**
	 * Loads the video specified in {@link #youTubeVideo}.
	 */
	private void loadVideo() {
		// if the video is NOT live
		if (!youTubeVideo.isLiveStream()) {
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
		} else {
			// video is live:  ask the user if he wants to play the video using an other app
			new AlertDialog.Builder(getContext())
					.setMessage(R.string.warning_live_video)
					.setTitle(R.string.error_video_play)
					.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							closeActivity();
						}
					})
					.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							playVideoExternally();
							closeActivity();
						}
					})
					.show();
		}
	}


	////////////////////////////////////////////////////////////////////////////////////////////////

	private GetVideoDetailsListener getVideoDetailsListener = new GetVideoDetailsListener() {
		@Override
		public void onSuccess(YouTubeVideo video) {
			YouTubePlayerFragment.this.youTubeVideo = video;
			setUpHUDAndPlayVideo();	// setup the HUD and play the video

			getVideoInfoTasks();

			// will now check if the video is bookmarked or not (and then update the menu
			// accordingly)
			new IsVideoBookmarkedTask(video, menu).executeInParallel();
		}

		@Override
		public void onFailure(String videoUrl) {
			String err = String.format(getString(R.string.error_invalid_url), videoUrl);
			Toast.makeText(getActivity(), err, Toast.LENGTH_LONG).show();
			// log error
			Log.e(TAG, err);
			// close the video player activity
			getActivity().finish();
		}
	};

	////////////////////////////////////////////////////////////////////////////////////////////////

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
