package free.rm.skytube.gui.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import free.rm.skytube.R;
import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.businessobjects.GetVideoDetailsTask;
import free.rm.skytube.businessobjects.Logger;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeChannel;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeChannelInterface;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeVideo;
import free.rm.skytube.businessobjects.YouTube.Tasks.GetVideoDescriptionTask;
import free.rm.skytube.businessobjects.YouTube.Tasks.GetYouTubeChannelInfoTask;
import free.rm.skytube.businessobjects.YouTube.VideoStream.StreamMetaData;
import free.rm.skytube.businessobjects.db.DownloadedVideosDb;
import free.rm.skytube.businessobjects.db.PlaybackStatusDb;
import free.rm.skytube.businessobjects.db.Tasks.CheckIfUserSubbedToChannelTask;
import free.rm.skytube.businessobjects.db.Tasks.IsVideoBookmarkedTask;
import free.rm.skytube.businessobjects.interfaces.GetDesiredStreamListener;
import free.rm.skytube.gui.activities.MainActivity;
import free.rm.skytube.gui.businessobjects.MediaControllerEx;
import free.rm.skytube.gui.businessobjects.OnSwipeTouchListener;
import free.rm.skytube.gui.businessobjects.YouTubeVideoListener;
import free.rm.skytube.gui.businessobjects.adapters.CommentsAdapter;
import hollowsoft.slidingdrawer.OnDrawerOpenListener;

/**
 * A fragment that holds a standalone YouTube player.
 */
public class YouTubePlayerFragment extends YouTubePlayerBaseFragment implements MediaPlayer.OnPreparedListener, YouTubeVideoListener, MediaPlayer.OnCompletionListener {
	private VideoView			videoView = null;
	/** The current video position (i.e. play time). */
	private int					videoCurrentPosition = 0;
	private MediaControllerEx	mediaController = null;

	private ImageView           indicatorImageView = null;
	private TextView            indicatorTextView = null;
	private RelativeLayout      indicatorView = null;

	private View                videoDescriptionDrawerIconView = null;

	private View                commentsDrawerIconView = null;


	private Handler             hideHudTimerHandler = null;
	private Handler             hideVideoDescAndCommentsIconsTimerHandler = null;

	private float               startBrightness = -1.0f;
	private float               startVolumePercent  = -1.0f;
	private int                 startVideoTime = -1;

	/** Timeout (in milliseconds) before the HUD (i.e. media controller + action/title bar) is hidden. */
	private static final int HUD_VISIBILITY_TIMEOUT = 5000;
	/** Timeout (in milliseconds) before the navigation bar is hidden (which will occur only after
	 * the HUD is hidden). */
	private static final int NAVBAR_VISIBILITY_TIMEOUT = 500;
	private static final String VIDEO_CURRENT_POSITION = "YouTubePlayerFragment.VideoCurrentPosition";
	private static final String TAG = YouTubePlayerFragment.class.getSimpleName();
	private static final String TUTORIAL_COMPLETED = "YouTubePlayerFragment.TutorialCompleted";

	private static final int MAX_VIDEO_STEP_TIME = 60 * 1000;
	private static final int MAX_BRIGHTNESS = 100;

	private List<Uri> videoListUris = new ArrayList<>();


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// if immersive mode is enabled then hide the navigation bar
		if (userWantsImmersiveMode()) {
			hideNavigationBar();
		}

		// inflate the layout for this fragment
		View view = inflater.inflate(R.layout.fragment_youtube_player, container, false);

		// indicate that this fragment has an action bar menu
		setHasOptionsMenu(true);

		if (savedInstanceState != null)
			videoCurrentPosition = savedInstanceState.getInt(VIDEO_CURRENT_POSITION, 0);

		// initialise the views
		initViews(view);

		// hide action bar
		getSupportActionBar().hide();

		// get which video we need to play...
		Bundle bundle = getActivity().getIntent().getExtras();
		if (bundle != null  &&  bundle.getSerializable(YOUTUBE_VIDEO_LIST) != null) {
			// ... either the video details are passed through the previous activity
			currentVideoIndex = bundle.getInt(YOUTUBE_VIDEO_INDEX);
			videoList = (ArrayList<YouTubeVideo>)bundle.getSerializable(YOUTUBE_VIDEO_LIST);
			for(int i=0;i<videoList.size();i++) {
				videoListUris.add(null);
			}
			setUpHUD(videoList.get(currentVideoIndex));
			loadVideos();
			setupPrevNextButtons();

			getVideoInfoTasks();
		} else {
			// ... or the video URL is passed to SkyTube via another Android app
			new GetVideoDetailsTask(getUrlFromIntent(getActivity().getIntent()), this).executeInParallel();
		}

		return view;
	}

	private void setupPrevNextButtons() {
		// If there's more than one video to play, set the prev & next button listeners, which will show the buttons.
		if(videoList.size() > 1) {
			mediaController.setPrevNextListeners(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (currentVideoIndex + 1 < videoList.size()) {
						currentVideoIndex++;
						loadVideos();
					}
				}
			}, new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (currentVideoIndex > 0) {
						currentVideoIndex--;
						loadVideos();
					}
				}
			});
		}
	}


	/**
	 * Initialise the views.
	 *
	 * @param view Fragment view.
	 */
	private void initViews(View view) {
		loadingVideoView = view.findViewById(R.id.loadingVideoView);

		videoView = view.findViewById(R.id.video_view);
		// videoView should log any errors
		videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
			@Override
			public boolean onError(MediaPlayer mp, int what, int extra) {
				String msg = String.format(Locale.getDefault(),
								"Error has occurred while playing video, url='%s', what=%d, extra=%d",
								videoList.get(currentVideoIndex) != null ? videoList.get(currentVideoIndex).getVideoUrl() : "null",
								what,
								extra);
				Log.e(TAG, msg);
				return false;
			}
		});
		// play the video once its loaded
		videoView.setOnPreparedListener(this);
		videoView.setOnCompletionListener(this);

		// setup the media controller (will control the video playing/pausing)
		mediaController = new MediaControllerEx(getActivity(), videoView, this);

		// ensure that the mediaController is always above the NavBar (given that the NavBar can
		// be in immersive mode)
		if (userWantsImmersiveMode()) {
			mediaController.setPadding(0, 0, 0, getNavBarHeightInPixels());
		}

		voidView = view.findViewById(R.id.void_view);
		indicatorView = view.findViewById(R.id.indicatorView);
		indicatorImageView = view.findViewById(R.id.indicatorImageView);
		indicatorTextView = view.findViewById(R.id.indicatorTextView);
		// detect if user's swipes motions and taps...
		voidView.setOnTouchListener(new OnSwipeTouchListener(getActivity()) {

			@Override
			public boolean onSwipeLeft() {
				commentsDrawer.animateOpen();
				return true;
			}

			@Override
			public boolean onSwipeTop() {
				videoDescriptionDrawer.animateOpen();
				return true;
			}

			@Override
			public boolean onDoubleTap() {
				if (videoView.isPlaying()) {
					videoView.pause();
				} else {
					videoView.start();
				}
				return true;
			}

			@Override
			public boolean onSingleTap() {
				showOrHideHud();
				return true;
			}

			@Override
			public void onGestureDone() {
				startBrightness = -1.0f;
				startVolumePercent = -1.0f;
				startVideoTime = -1;
				hideIndicator();
			}

			@Override
			public void adjustBrightness(double adjustPercent) {
				// We are setting brightness percent to a value that should be from -1.0 to 1.0. We need to limit it here for these values first
				if (adjustPercent < -1.0f) {
					adjustPercent = -1.0f;
				} else if (adjustPercent > 1.0f) {
					adjustPercent = 1.0f;
				}

				WindowManager.LayoutParams lp = getActivity().getWindow().getAttributes();
				if (startBrightness < 0) {
					startBrightness = lp.screenBrightness;
				}
				// We are getting a final brightness value when summing current brightness and the percent we got from swipe action. Should be >= 0 and <= 1
				float targetBrightness = (float) (startBrightness + adjustPercent * 1.0f);
				if (targetBrightness <= 0.0f) {
					targetBrightness = 0.0f;
				} else if (targetBrightness >= 1.0f) {
					targetBrightness = 1.0f;
				}
				lp.screenBrightness = targetBrightness;
				getActivity().getWindow().setAttributes(lp);

				indicatorImageView.setImageResource(R.drawable.ic_brightness);
				indicatorTextView.setText((int) (targetBrightness * MAX_BRIGHTNESS) + "%");

				// Show indicator. It will be hidden once onGestureDone will be called
				showIndicator();
			}

			@Override
			public void adjustVolumeLevel(double adjustPercent) {
				// We are setting volume percent to a value that should be from -1.0 to 1.0. We need to limit it here for these values first
				if (adjustPercent < -1.0f) {
					adjustPercent = -1.0f;
				} else if (adjustPercent > 1.0f) {
					adjustPercent = 1.0f;
				}

				AudioManager audioManager = (AudioManager) getContext()
								.getSystemService(Context.AUDIO_SERVICE);
				final int STREAM = AudioManager.STREAM_MUSIC;

				// Max volume will return INDEX of volume not the percent. For example, on my device it is 15
				int maxVolume = audioManager.getStreamMaxVolume(STREAM);
				if (maxVolume == 0) return;

				if (startVolumePercent < 0) {
					// We are getting actual volume index (NOT volume but index). It will be >= 0.
					int curVolume = audioManager.getStreamVolume(STREAM);
					// And counting percents of maximum volume we have now
					startVolumePercent = curVolume * 1.0f / maxVolume;
				}
				// Should be >= 0 and <= 1
				double targetPercent = startVolumePercent + adjustPercent;
				if (targetPercent > 1.0f) {
					targetPercent = 1.0f;
				} else if (targetPercent < 0) {
					targetPercent = 0;
				}

				// Calculating index. Test values are 15 * 0.12 = 1 ( because it's int)
				int index = (int) (maxVolume * targetPercent);
				if (index > maxVolume) {
					index = maxVolume;
				} else if (index < 0) {
					index = 0;
				}
				audioManager.setStreamVolume(STREAM, index, 0);

				indicatorImageView.setImageResource(R.drawable.ic_volume);
				indicatorTextView.setText(index * 100 / maxVolume + "%");

				// Show indicator. It will be hidden once onGestureDone will be called
				showIndicator();
			}

			@Override
			public void adjustVideoPosition(double adjustPercent, boolean forwardDirection) {
				if (adjustPercent < -1.0f) {
					adjustPercent = -1.0f;
				} else if (adjustPercent > 1.0f) {
					adjustPercent = 1.0f;
				}

				int totalTime = videoView.getDuration();

				if (startVideoTime < 0) {
					startVideoTime = videoView.getCurrentPosition();
				}
				// adjustPercent: value from -1 to 1.
				double positiveAdjustPercent = Math.max(adjustPercent,-adjustPercent);
				// End of line makes seek speed not linear
				int targetTime = startVideoTime + (int) (MAX_VIDEO_STEP_TIME * adjustPercent * (positiveAdjustPercent / 0.1));
				if (targetTime > totalTime) {
					targetTime = totalTime;
				}
				if (targetTime < 0) {
					targetTime = 0;
				}

				String targetTimeString = formatDuration(targetTime / 1000);

				if (forwardDirection) {
					indicatorImageView.setImageResource(R.drawable.ic_forward);
					indicatorTextView.setText(targetTimeString);
				} else {
					indicatorImageView.setImageResource(R.drawable.ic_rewind);
					indicatorTextView.setText(targetTimeString);
				}

				showIndicator();

				videoView.seekTo(targetTime);
			}

			@Override
			public Rect viewRect() {
				return new Rect(voidView.getLeft(), voidView.getTop(), voidView.getRight() , voidView.getBottom());
			}
		});

		videoDescriptionDrawer = view.findViewById(R.id.des_drawer);
		videoDescriptionDrawerIconView = view.findViewById(R.id.video_desc_icon_image_view);
		videoDescTitleTextView = view.findViewById(R.id.video_desc_title);
		videoDescChannelThumbnailImageView = view.findViewById(R.id.video_desc_channel_thumbnail_image_view);
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
		videoDescChannelTextView = view.findViewById(R.id.video_desc_channel);
		videoDescViewsTextView = view.findViewById(R.id.video_desc_views);
		videoDescLikesTextView = view.findViewById(R.id.video_desc_likes);
		videoDescDislikesTextView = view.findViewById(R.id.video_desc_dislikes);
		videoDescRatingsDisabledTextView = view.findViewById(R.id.video_desc_ratings_disabled);
		videoDescPublishDateTextView = view.findViewById(R.id.video_desc_publish_date);
		videoDescriptionTextView = view.findViewById(R.id.video_desc_description);
		videoDescLikesBar = view.findViewById(R.id.video_desc_likes_bar);
		videoDescSubscribeButton = view.findViewById(R.id.video_desc_subscribe_button);

		commentsExpandableListView = view.findViewById(R.id.commentsExpandableListView);
		commentsProgressBar = view.findViewById(R.id.comments_progress_bar);
		noVideoCommentsView = view.findViewById(R.id.no_video_comments_text_view);
		commentsDrawer = view.findViewById(R.id.comments_drawer);
		commentsDrawer.setOnDrawerOpenListener(new OnDrawerOpenListener() {
			@Override
			public void onDrawerOpened() {
				if (commentsAdapter == null) {
					commentsAdapter = new CommentsAdapter(getActivity(), videoList.get(currentVideoIndex).getId(), commentsExpandableListView, commentsProgressBar, noVideoCommentsView);
				}
			}
		});
		commentsDrawerIconView = view.findViewById(R.id.comments_icon_image_view);
	}


	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(VIDEO_CURRENT_POSITION, videoCurrentPosition);
	}


	/**
	 * Will asynchronously retrieve additional video information such as channel, avatar ...etc
	 */
	private void getVideoInfoTasks() {
		// get Channel info (e.g. avatar...etc) task
		new GetYouTubeChannelInfoTask(getContext(), new YouTubeChannelInterface() {
			@Override
			public void onGetYouTubeChannel(YouTubeChannel youTubeChannel) {
				YouTubePlayerFragment.this.youTubeChannel = youTubeChannel;

				videoDescSubscribeButton.setChannel(YouTubePlayerFragment.this.youTubeChannel);
				if (youTubeChannel != null) {
					if(getActivity() != null)
						Glide.with(getActivity())
										.load(youTubeChannel.getThumbnailNormalUrl())
										.apply(new RequestOptions().placeholder(R.drawable.channel_thumbnail_default))
										.into(videoDescChannelThumbnailImageView);
				}
			}
		}).executeInParallel(videoList.get(currentVideoIndex).getChannelId());

		// check if the user has subscribed to a channel... if he has, then change the state of
		// the subscribe button
		new CheckIfUserSubbedToChannelTask(videoDescSubscribeButton, videoList.get(currentVideoIndex).getChannelId()).execute();
	}


	@Override
	public void onPrepared(MediaPlayer mediaPlayer) {
		loadingVideoView.setVisibility(View.GONE);
		videoView.seekTo(videoCurrentPosition);

		// was the video player tutorial displayed before?
		if (wasTutorialDisplayedBefore()) {
			videoView.start();
		} else {
			// display the tutorial dialog boxes, then play the video
			displayTutorialDialog(R.string.tutorial_comments_icon, Gravity.TOP | Gravity.RIGHT, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					displayTutorialDialog(R.string.tutorial_video_info_icon, Gravity.BOTTOM | Gravity.LEFT, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							displayTutorialDialog(R.string.tutorial_pause_video, Gravity.CENTER, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									videoView.start();
								}
							});
						}
					});
				}
			});
		}
		showHud();
	}



	@Override
	public void onPause() {
		if (videoView != null && videoView.isPlaying()) {
			videoCurrentPosition = videoView.getCurrentPosition();
		}

		saveCurrentBrightness();
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();

		setupUserPrefs();
	}

	// We can also add volume level or something in the future.
	private void setupUserPrefs() {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
		float brightnessLevel = sp.getFloat(getString(R.string.pref_key_brightness_level), -1.0f);
		setBrightness(brightnessLevel);
	}


	private void saveCurrentBrightness() {
		WindowManager.LayoutParams lp = getActivity().getWindow().getAttributes();
		float brightnessLevel = lp.screenBrightness;
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
		sp.edit().putFloat(getString(R.string.pref_key_brightness_level), brightnessLevel).apply();
	}

	private void setBrightness(float level) {
		if(level <= 0.0f && level > 1.0f) return;

		WindowManager.LayoutParams lp = getActivity().getWindow().getAttributes();
		lp.screenBrightness = level;
		getActivity().getWindow().setAttributes(lp);
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
		if (commentsDrawer.isOpened()) {
			commentsDrawer.animateClose();
		} else if (videoDescriptionDrawer.isOpened()) {
			videoDescriptionDrawer.animateClose();
		} else if (isHudVisible()) {
			hideHud();
		} else {
			showHud();
		}
	}



	/**
	 * Show the HUD (head-up display), i.e. the Action Bar and Media Controller.
	 */
	private void showHud() {
		if (!isHudVisible()) {
			getSupportActionBar().show();
			getSupportActionBar().setTitle(videoList.get(currentVideoIndex) != null ? videoList.get(currentVideoIndex).getTitle() : "");
			mediaController.show(0);

			videoDescriptionDrawer.close();
			videoDescriptionDrawerIconView.setVisibility(View.INVISIBLE);
			commentsDrawer.close();
			commentsDrawerIconView.setVisibility(View.INVISIBLE);

			// hide UI after a certain timeout (defined in HUD_VISIBILITY_TIMEOUT)
			hideHudTimerHandler = new Handler();
			hideHudTimerHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					hideHud();
					hideHudTimerHandler = null;
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

			// if the user wants the IMMERSIVE mode experience...
			if (userWantsImmersiveMode()) {
				// Hide the navigation bar.  Due to Android pre-defined mechanisms, the nav bar can
				// only be hidden after all animation have been rendered (e.g. mediaController is
				// fully closed).  As a result, a delay is needed in order to explicitly hide the
				// nav bar.
				hideVideoDescAndCommentsIconsTimerHandler = new Handler();
				hideVideoDescAndCommentsIconsTimerHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						hideNavigationBar();
						hideVideoDescAndCommentsIconsTimerHandler = null;
					}
				}, NAVBAR_VISIBILITY_TIMEOUT);
			} else {
				videoDescriptionDrawerIconView.setVisibility(View.VISIBLE);
				commentsDrawerIconView.setVisibility(View.VISIBLE);
			}

			// If there is a hideHudTimerHandler running, then cancel it (stop if from running).  This way,
			// if the HUD was hidden on the 5th second, and the user reopens the HUD, this code will
			// prevent the HUD to re-disappear 2 seconds after it was displayed (assuming that
			// HUD_VISIBILITY_TIMEOUT = 5 seconds).
			if (hideHudTimerHandler != null) {
				hideHudTimerHandler.removeCallbacksAndMessages(null);
				hideHudTimerHandler = null;
			}
		}
	}


	private void showIndicator() {
		indicatorView.setVisibility(View.VISIBLE);
	}


	private void hideIndicator() {
		indicatorView.setVisibility(View.GONE);
	}


	// Returns a (localized) string for the given duration (in seconds).
	private String formatDuration(int duration) {
		int h = duration / 3600;
		int m = (duration - h * 3600) / 60;
		int s = duration - (h * 3600 + m * 60);
		String durationValue;
		if (h == 0) {
			durationValue = String.format(Locale.getDefault(),"%1$02d:%2$02d", m, s);
		} else {
			durationValue = String.format(Locale.getDefault(),"%1$d:%2$02d:%3$02d", h, m, s);
		}
		return durationValue;
	}

	protected void stop() {
		videoView.pause();
	}

	/**
	 * Loads the video specified in {@link #videoList} at {@link #currentVideoIndex}
	 */
	protected void loadVideos() {
		// if the video is NOT live
		if (!videoList.get(currentVideoIndex).isLiveStream()) {
			videoView.pause();
			videoView.stopPlayback();
			loadingVideoView.setVisibility(View.VISIBLE);
			if(videoList.get(currentVideoIndex).isDownloaded()) {
				Uri uri = videoList.get(currentVideoIndex).getFileUri();
				File file = new File(uri.getPath());
				// If the file for this video has gone missing, remove it from the Database and then play remotely.
				if(!file.exists()) {
					DownloadedVideosDb.getVideoDownloadsDb().remove(videoList.get(currentVideoIndex));
					Toast.makeText(getContext(),
									getContext().getString(R.string.playing_video_file_missing),
									Toast.LENGTH_LONG).show();
					loadVideos();
				} else {
					Logger.i(YouTubePlayerFragment.this, ">> PLAYING LOCALLY: %s", videoList.get(currentVideoIndex));
					videoView.setVideoURI(uri);
				}
			} else {
				if(videoListUris.get(currentVideoIndex) != null) {
					Logger.d(YouTubePlayerFragment.this, "playing from uri");
					videoView.setVideoURI(videoListUris.get(currentVideoIndex));
				} else {
					videoList.get(currentVideoIndex).getDesiredStream(new GetDesiredStreamListener() {
						@Override
						public void onGetDesiredStream(StreamMetaData desiredStream) {
							videoListUris.set(currentVideoIndex, desiredStream.getUri());
							// play the video
							Logger.i(YouTubePlayerFragment.this, ">> PLAYING: %s", desiredStream.getUri());
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
				}
			}

			// get the video description
			new GetVideoDescriptionTask(videoList.get(currentVideoIndex), new GetVideoDescriptionTask.GetVideoDescriptionTaskListener() {
				@Override
				public void onFinished(String description) {
					videoDescriptionTextView.setText(description);
					SkyTubeApp.interceptYouTubeLinks(getActivity(), videoDescriptionTextView);
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

	/**
	 * Will check whether the video player tutorial was completed before.  If no, it will return
	 * false and will save the value accordingly.
	 *
	 * @return True if the tutorial was completed in the past.
	 */
	private boolean wasTutorialDisplayedBefore() {
		SharedPreferences preferences = SkyTubeApp.getPreferenceManager();
		boolean wasTutorialDisplayedBefore = preferences.getBoolean(TUTORIAL_COMPLETED, false);

		preferences.edit().putBoolean(TUTORIAL_COMPLETED, true).commit();

		return wasTutorialDisplayedBefore;
	}


	/**
	 * Display a tutorial dialog.
	 *
	 * @param messageResId          Message resource ID.
	 * @param dialogGravityFlags    Gravity flags, e.g. Gravity.RIGHT.
	 * @param onClickListener       onClickListener which will be called once the user taps on OK
	 *                              button.
	 */
	private void displayTutorialDialog(int messageResId, int dialogGravityFlags, DialogInterface.OnClickListener onClickListener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setMessage(messageResId);
		builder.setPositiveButton(R.string.ok, onClickListener);

		AlertDialog dialog = builder.create();
		WindowManager.LayoutParams wmlp = dialog.getWindow().getAttributes();

		if (wmlp != null) {
			if (dialogGravityFlags != Gravity.CENTER) {
				wmlp.gravity = dialogGravityFlags;
				wmlp.x = 50;   // x position
				wmlp.y = 50;   // y position
			}

			dialog.show();
		}
	}


	////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * When a video url is clicked on, attempt to open and play it.
	 * @param videoUrl
	 * @param youTubeVideo
	 */
	@Override
	public void onYouTubeVideo(String videoUrl, YouTubeVideo youTubeVideo) {
		if (youTubeVideo == null) {
			// invalid URL error (i.e. we are unable to decode the URL)
			String err = String.format(getString(R.string.error_invalid_url), videoUrl);
			Toast.makeText(getActivity(), err, Toast.LENGTH_LONG).show();

			// log error
			Log.e(TAG, err);

			// close the video player activity
			closeActivity();
		} else {
			videoList = new ArrayList<>();
			videoList.add(youTubeVideo);
			currentVideoIndex = 0;

			// setup the HUD and play the video
			setUpHUD(youTubeVideo);
			loadVideos();

			getVideoInfoTasks();

			// will now check if the video is bookmarked or not (and then update the menu
			// accordingly)
			new IsVideoBookmarkedTask(youTubeVideo, menu).executeInParallel();
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////


	@Override
	public void videoPlaybackStopped() {
		int position = videoView.getCurrentPosition();
		videoView.pause();
		videoView.stopPlayback();
		if(!SkyTubeApp.getPreferenceManager().getBoolean(getString(R.string.pref_key_disable_playback_status), false)) {
			PlaybackStatusDb.getVideoDownloadsDb().setVideoPosition(videoList.get(currentVideoIndex), position);
		}
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		if(autoPlayNextVideo) {
			if(currentVideoIndex + 1 < videoList.size()) {
				currentVideoIndex++;
				loadVideos();
			}
		}
	}
}
