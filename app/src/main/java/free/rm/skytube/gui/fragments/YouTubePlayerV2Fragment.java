/*
 * SkyTube
 * Copyright (C) 2018  Ramon Mifsud
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package free.rm.skytube.gui.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import free.rm.skytube.R;
import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.businessobjects.AsyncTaskParallel;
import free.rm.skytube.businessobjects.Logger;
import free.rm.skytube.businessobjects.YouTube.GetVideosDetailsByIDs;
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
import free.rm.skytube.gui.businessobjects.PlayerViewGestureDetector;
import free.rm.skytube.gui.businessobjects.adapters.CommentsAdapter;
import hollowsoft.slidingdrawer.OnDrawerOpenListener;

/**
 * A fragment that holds a standalone YouTube player (version 2).
 */
public class YouTubePlayerV2Fragment extends YouTubePlayerBaseFragment {
	private PlayerView          playerView;
	private SimpleExoPlayer     player;
	private ConcatenatingMediaSource mediaSources;
	private ExtractorMediaSource.Factory extMediaSourceFactory;
	private long				playerInitialPosition = 0;
	private List<Boolean> mediaSourceAdded = new ArrayList<>();
	private Map<YouTubeVideo, Integer> mediaSourceVideoIndexes = new HashMap<>();

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		hideNavigationBar();

		// inflate the layout for this fragment
		View view = inflater.inflate(R.layout.fragment_youtube_player_v2, container, false);

		// indicate that this fragment has an action bar menu
		setHasOptionsMenu(true);

//		final View decorView = getActivity().getWindow().getDecorView();
//		decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
//			@Override
//			public void onSystemUiVisibilityChange(int visibility) {
//				hideNavigationBar();
//			}
//		});

		///if (savedInstanceState != null)
		///	videoCurrentPosition = savedInstanceState.getInt(VIDEO_CURRENT_POSITION, 0);

		// initialise the views
		initViews(view);

		// get which video we need to play...
		Bundle bundle = getActivity().getIntent().getExtras();
		if (bundle != null  &&  bundle.getSerializable(YOUTUBE_VIDEO_LIST) != null) {
			// ... either the video details are passed through the previous activity
			currentVideoIndex = bundle.getInt(YOUTUBE_VIDEO_INDEX);
			videoList = (ArrayList<YouTubeVideo>)bundle.getSerializable(YOUTUBE_VIDEO_LIST);
			for(int i=0;i<videoList.size();i++) {
				mediaSourceAdded.add(false);
			}
			setUpHUD(videoList.get(currentVideoIndex));
			checkForInProgress();

			getVideoInfoTasks();
		} else {
			// ... or the video URL is passed to SkyTube via another Android app
			new GetVideoDetailsTask().executeInParallel();
		}


		return view;
	}


	/**
	 * Initialise the views.
	 *
	 * @param view Fragment view.
	 */
	private void initViews(View view) {
		// setup the toolbar / actionbar
		Toolbar toolbar = view.findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setDisplayShowHomeEnabled(true);

		// setup the player
		final PlayerViewGestureHandler playerViewGestureHandler;
		playerView = view.findViewById(R.id.player_view);
		playerViewGestureHandler = new PlayerViewGestureHandler();
		playerViewGestureHandler.initView(view);
		playerView.setOnTouchListener(playerViewGestureHandler);
		playerView.requestFocus();

		// When previous/next buttons are clicked, save the current position of the current video to the Playback status database
		playerView.setControlDispatcher(new DefaultControlDispatcher() {
			@Override
			public boolean dispatchSeekTo(Player player, int windowIndex, long positionMs) {
				if(windowIndex != player.getCurrentWindowIndex() && !SkyTubeApp.getPreferenceManager().getBoolean(getString(R.string.pref_key_disable_playback_status), false))
					PlaybackStatusDb.getVideoDownloadsDb().setVideoPosition(videoList.get(currentVideoIndex), player.getCurrentPosition());
				return super.dispatchSeekTo(player, windowIndex, positionMs);
			}
		});

		DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

		TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
		DefaultTrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

		player = ExoPlayerFactory.newSimpleInstance(getContext(), trackSelector);
		player.setPlayWhenReady(true);

		// When a video in the playlist is changed, update the UI, and if needed, get the Uri for the previous and next
		// videos in the videoList and add them to the ExoPlayer's playlist
		player.addListener(new Player.DefaultEventListener() {
			@Override
			public void onPositionDiscontinuity(int reason) {
				Logger.d(YouTubePlayerV2Fragment.this, "666 discontinuity, reason: %d", reason);

				/**
				 * 0 = automatic transition from one video to another
				 * 1 = seek
				 */
				if(reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION && !SkyTubeApp.getPreferenceManager().getBoolean(getString(R.string.pref_key_disable_playback_status), false)) {
					PlaybackStatusDb.getVideoDownloadsDb().setVideoWatchedStatus(videoList.get(currentVideoIndex), true);
				}
				if(player.getCurrentWindowIndex() != currentVideoIndex) {
					if(reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION && !autoPlayNextVideo) {
						Logger.d(YouTubePlayerV2Fragment.this, "666 transitioning to next video and autoplay is off, exiting");
						stop();
						return;
					}
					currentVideoIndex = player.getCurrentWindowIndex();
					setUpHUD(videoList.get(currentVideoIndex));
					getVideoInfoTasks();

					if(currentVideoIndex == 0) {
						if(!mediaSourceAdded.get(1)) {
							Logger.d(YouTubePlayerV2Fragment.this, "666 getting uri for video 1");
							setupOtherVideos(1);
						}
					} else if(currentVideoIndex+1 == videoList.size()) { // current video is second to last
						if(!mediaSourceAdded.get(currentVideoIndex-1)) { // uri for last video has not been retrieved
							Logger.d(YouTubePlayerV2Fragment.this, "666 getting uri for video %d", currentVideoIndex - 1);
							setupOtherVideos(currentVideoIndex - 1);
						}
					} else {
						if(!mediaSourceAdded.get(currentVideoIndex-1)) {
							Logger.d(YouTubePlayerV2Fragment.this, "666 getting uri for video %d", currentVideoIndex - 1);
							setupOtherVideos(currentVideoIndex - 1);
						}
						if(!mediaSourceAdded.get(currentVideoIndex+1)) {
							Logger.d(YouTubePlayerV2Fragment.this, "666 getting uri for video %d", currentVideoIndex + 1);
							setupOtherVideos(currentVideoIndex + 1);
						}
					}
				}
			}
		});
		playerView.setPlayer(player);

		loadingVideoView = view.findViewById(R.id.loadingVideoView);

		videoDescriptionDrawer = view.findViewById(R.id.des_drawer);
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
	}


	/**
	 * Will setup the HUD's details according to the contents of the passed {@link YouTubeVideo}.
	 */
	@Override
	protected void setUpHUD(YouTubeVideo youTubeVideo) {
		getSupportActionBar().setTitle(youTubeVideo.getTitle());
		super.setUpHUD(youTubeVideo);
	}

	private void checkForInProgress() {
		if(!SkyTubeApp.getPreferenceManager().getBoolean(getString(R.string.pref_key_disable_playback_status), false) && PlaybackStatusDb.getVideoDownloadsDb().getVideoWatchedStatus(videoList.get(currentVideoIndex)).position > 0) {
			new AlertDialog.Builder(getActivity())
							.setTitle(R.string.should_resume)
							.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									playerInitialPosition = PlaybackStatusDb.getVideoDownloadsDb().getVideoWatchedStatus(videoList.get(currentVideoIndex)).position;
									loadVideos();
								}
							})
							.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialogInterface, int i) {
									loadVideos();
								}
							})
							.show();
		} else {
			loadVideos();
		}
	}

	/**
	 * Loads the video specified in {@link #videoList} at {@link #currentVideoIndex}
	 */
	protected void loadVideos() {
		// if the video is NOT live
		if (!videoList.get(currentVideoIndex).isLiveStream()) {
			loadingVideoView.setVisibility(View.VISIBLE);
			getVideoUri(videoList.get(currentVideoIndex), new GetVideoUriListener() {
				@Override
				public void onUri(Uri uri) {
					// hide the loading video view (progress bar)
					loadingVideoView.setVisibility(View.GONE);

					// play the video
					Logger.i(this, ">> PLAYING: %s", uri);
					playVideo(uri);
				}

				@Override
				public void onError(String errorMessage) {
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

	private interface GetVideoUriListener {
		void onUri(Uri uri);
		void onError(String errorMessage);
	}

	private void getVideoUri(final YouTubeVideo video, final GetVideoUriListener videoUriListener) {
		if(video.isDownloaded()) {
			Uri uri = video.getFileUri();
			File file = new File(uri.getPath());
			// If the file for this video has gone missing, remove it from the Database and then play remotely.
			if(!file.exists()) {
				DownloadedVideosDb.getVideoDownloadsDb().remove(video);
				Toast.makeText(getContext(),
								getContext().getString(R.string.playing_video_file_missing),
								Toast.LENGTH_LONG).show();
				getVideoUri(video, videoUriListener);
			} else {
				loadingVideoView.setVisibility(View.GONE);

				Logger.i(YouTubePlayerV2Fragment.this, ">> PLAYING LOCALLY: %s", video);
				videoUriListener.onUri(uri);
			}
		} else {
			video.getDesiredStream(new GetDesiredStreamListener() {
				@Override
				public void onGetDesiredStream(StreamMetaData desiredStream) {
					videoUriListener.onUri(desiredStream.getUri());
				}

				@Override
				public void onGetDesiredStreamError(String errorMessage) {
					videoUriListener.onError(errorMessage);
				}
			});
		}
	}

	/**
	 * Play video.
	 *
	 * @param videoUri  The Uri of the video that is going to be played.
	 */
	private void playVideo(Uri videoUri) {
		DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(getContext(), "ST. Agent", new DefaultBandwidthMeter());
		extMediaSourceFactory = new ExtractorMediaSource.Factory(dataSourceFactory);
		ExtractorMediaSource mediaSource = extMediaSourceFactory.createMediaSource(videoUri);
		mediaSources = new ConcatenatingMediaSource();
		mediaSources.addMediaSource(mediaSource);
		mediaSourceVideoIndexes.put(videoList.get(currentVideoIndex), 0);
		player.prepare(mediaSources);
		if(playerInitialPosition > 0)
			player.seekTo(playerInitialPosition);
		if(videoList.size() > 1) {
			mediaSourceAdded.set(currentVideoIndex, true);
			if(currentVideoIndex == 0) {
				setupOtherVideos(currentVideoIndex+1);
			} else if(currentVideoIndex == videoList.size()) {
				setupOtherVideos(currentVideoIndex-1);
			} else {
				// When adding the previous and next videos, need to make sure the next video doesn't get added
				// until the previous video has been added.
				setupOtherVideos(currentVideoIndex - 1, new Runnable() {
					@Override
					public void run() {
						setupOtherVideos(currentVideoIndex+1);
					}
				});
			}
		}
	}

	private void setupOtherVideos(final int index) {
		setupOtherVideos(index, null);
	}

	private void setupOtherVideos(final int index, final Runnable onFinished) {
		getVideoUri(videoList.get(index), new GetVideoUriListener() {
			@Override
			public void onUri(Uri uri) {
//				Logger.d(YouTubePlayerV2Fragment.this, "666 got uri: %s, adding at %d", uri, index);
				Logger.d(YouTubePlayerV2Fragment.this, "666 setting up %s at index %d", videoList.get(index).getTitle(), index);
				MediaSource mediaSource = extMediaSourceFactory.createMediaSource(uri);
				mediaSources.addMediaSource(mediaSource, new Runnable() {
					@Override
					public void run() {
						mediaSourceAdded.set(index, true);
						mediaSourceVideoIndexes.put(videoList.get(index), mediaSourceVideoIndexes.size());

						if(mediaSourceVideoIndexes.size() > index) {
							mediaSources.moveMediaSource(mediaSources.getSize()-1, 0);

							mediaSourceVideoIndexes.put(videoList.get(index), 0);
							mediaSourceVideoIndexes.put(videoList.get(currentVideoIndex), 1);
						}

						printMediaSources();
						Logger.d(YouTubePlayerV2Fragment.this, "666 index for %s: %d", videoList.get(index).getTitle(), mediaSourceVideoIndexes.get(videoList.get(index).getId()));

//						if(mediaSourceVideoIndexes.get(videoList.get(index).getId()) < index) {
////							Logger.d(YouTubePlayerV2Fragment.this, "666 ")
//							// We're adding this video but it needs to be before the first in the exoplayer playlist, so move it to position 0
//							mediaSources.moveMediaSource(mediaSources.getSize()-1, 0);
//							printMediaSources();
//						}
						if(onFinished != null)
							onFinished.run();
					}
				});
			}

			@Override
			public void onError(String errorMessage) {
				// TODO: Handle error here
			}
		});
	}

	private void printMediaSources() {
		Logger.d(this, "666 ------");
		for(int i = 0; i< videoList.size(); i++) {
			YouTubeVideo v = videoList.get(i);
			if(mediaSourceVideoIndexes.get(v) != null) {
				Logger.d(this, "666 media: %s %d", v.getTitle(), mediaSourceVideoIndexes.get(v));
			}
		}
		Logger.d(this, "666 ------");
	}

	protected void stop() {
		player.stop();
	}

	/**
	 * Called when the options menu is closed.
	 *
	 * <p>The Navigation Bar is displayed when the Option Menu is visible.  Hence the objective of
	 * this method is to hide the Navigation Bar once the Options Menu is hidden.</p>
	 */
	public void onMenuClosed() {
		hideNavigationBar();
	}


	/**
	 * Will asynchronously retrieve additional video information such as channel avatar ...etc
	 */
	private void getVideoInfoTasks() {
		// get Channel info (e.g. avatar...etc) task
		new GetYouTubeChannelInfoTask(getContext(), new YouTubeChannelInterface() {
			@Override
			public void onGetYouTubeChannel(YouTubeChannel youTubeChannel) {
				YouTubePlayerV2Fragment.this.youTubeChannel = youTubeChannel;

				videoDescSubscribeButton.setChannel(YouTubePlayerV2Fragment.this.youTubeChannel);
				if (youTubeChannel != null) {
					if(getActivity() != null)
						Glide.with(getActivity())
										.load(youTubeChannel.getThumbnailNormalUrl())
										.apply(new RequestOptions().placeholder(R.drawable.channel_thumbnail_default))
										.into(videoDescChannelThumbnailImageView);
				}
			}
		}).executeInParallel(videoList.get(currentVideoIndex).getChannelId());

		// get the video description
		new GetVideoDescriptionTask(videoList.get(currentVideoIndex), new GetVideoDescriptionTask.GetVideoDescriptionTaskListener() {
			@Override
			public void onFinished(String description) {
				videoDescriptionTextView.setText(description);
			}
		}).executeInParallel();

		// check if the user has subscribed to a channel... if he has, then change the state of
		// the subscribe button
		new CheckIfUserSubbedToChannelTask(videoDescSubscribeButton, videoList.get(currentVideoIndex).getChannelId()).execute();
	}


	@Override
	public void onDestroy() {
		super.onDestroy();
		// stop the player from playing (when this fragment is going to be destroyed) and clean up
		player.stop();
		player = null;
		playerView.setPlayer(null);
	}


	////////////////////////////////////////////////////////////////////////////////////////////////


	/**
	 * This task will, from the given video URL, get the details of the video (e.g. video name,
	 * likes ...etc).
	 */
	private class GetVideoDetailsTask extends AsyncTaskParallel<Void, Void, YouTubeVideo> {

		private String videoUrl = null;


		@Override
		protected void onPreExecute() {
			String url = getUrlFromIntent(getActivity().getIntent());

			try {
				// YouTube sends subscriptions updates email in which its videos' URL are encoded...
				// Hence we need to decode them first...
				videoUrl = URLDecoder.decode(url, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				Logger.e(this, "UnsupportedEncodingException on " + videoUrl + " encoding = UTF-8", e);
				videoUrl = url;
			}
		}


		/**
		 * Returns an instance of {@link YouTubeVideo} from the given {@link #videoUrl}.
		 *
		 * @return {@link YouTubeVideo}; null if an error has occurred.
		 */
		@Override
		protected YouTubeVideo doInBackground(Void... params) {
			String videoId = YouTubeVideo.getYouTubeIdFromUrl(videoUrl);
			YouTubeVideo youTubeVideo = null;

			if (videoId != null) {
				try {
					GetVideosDetailsByIDs getVideo = new GetVideosDetailsByIDs();
					getVideo.init(videoId);
					List<YouTubeVideo> youTubeVideos = getVideo.getNextVideos();

					if (youTubeVideos.size() > 0)
						youTubeVideo = youTubeVideos.get(0);
				} catch (IOException ex) {
					Logger.e(this, "Unable to get video details, where id=" + videoId, ex);
				}
			}

			return youTubeVideo;
		}


		@Override
		protected void onPostExecute(YouTubeVideo youTubeVideo) {
			if (youTubeVideo == null) {
				// invalid URL error (i.e. we are unable to decode the URL)
				String err = String.format(getString(R.string.error_invalid_url), videoUrl);
				Toast.makeText(getActivity(), err, Toast.LENGTH_LONG).show();

				// log error
				Logger.e(this, err);

				// close the video player activity
				closeActivity();
			} else {
				videoList = new ArrayList<>();
				videoList.add(youTubeVideo);
				// setup the HUD and play the video
				setUpHUD(youTubeVideo);
				checkForInProgress();

				getVideoInfoTasks();

				// will now check if the video is bookmarked or not (and then update the menu
				// accordingly)
				new IsVideoBookmarkedTask(youTubeVideo, menu).executeInParallel();
			}
		}
	}



	////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * This will handle any gesture swipe event performed by the user on the player view.
	 */
	private class PlayerViewGestureHandler extends PlayerViewGestureDetector {

		private ImageView           indicatorImageView = null;
		private TextView            indicatorTextView = null;
		private RelativeLayout      indicatorView = null;

		private boolean             isControllerVisible = true;
		private float               startBrightness = -1.0f;
		private float               startVolumePercent = -1.0f;
		private long                startVideoTime = -1;

		private static final int    MAX_VIDEO_STEP_TIME = 60 * 1000;
		private static final int    MAX_BRIGHTNESS = 100;


		PlayerViewGestureHandler() {
			super(getContext());

			playerView.setControllerVisibilityListener(new PlayerControlView.VisibilityListener() {
				@Override
				public void onVisibilityChange(int visibility) {
					isControllerVisible = (visibility == View.VISIBLE);
				}
			});
		}


		void initView(View view) {
			indicatorView = view.findViewById(R.id.indicatorView);
			indicatorImageView = view.findViewById(R.id.indicatorImageView);
			indicatorTextView = view.findViewById(R.id.indicatorTextView);
		}


		@Override
		public void onCommentsGesture() {
			commentsDrawer.animateOpen();
		}


		@Override
		public void onVideoDescriptionGesture() {
			videoDescriptionDrawer.animateOpen();
		}


		@Override
		public void onDoubleTap() {
			// if the user is playing a video...
			if (player.getPlayWhenReady()) {
				// pause video
				player.setPlayWhenReady(false);
				player.getPlaybackState();
			} else {
				// play video
				player.setPlayWhenReady(true);
				player.getPlaybackState();
			}

			playerView.hideController();
		}


		@Override
		public boolean onSingleTap() {
			return showOrHideHud();
		}


		/**
		 * Hide or display the HUD depending if the HUD is currently visible or not.
		 */
		private boolean showOrHideHud() {
			if (commentsDrawer.isOpened()) {
				commentsDrawer.animateClose();
				return !isControllerVisible;
			}

			if (videoDescriptionDrawer.isOpened()) {
				videoDescriptionDrawer.animateClose();
				return !isControllerVisible;
			}

			if (isControllerVisible) {
				playerView.hideController();
				hideNavigationBar();
			} else {
				playerView.showController();
			}

			return false;
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

			AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
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
			long totalTime = player.getDuration();

			if (adjustPercent < -1.0f) {
				adjustPercent = -1.0f;
			} else if (adjustPercent > 1.0f) {
				adjustPercent = 1.0f;
			}

			if (startVideoTime < 0) {
				startVideoTime = player.getCurrentPosition();
			}
			// adjustPercent: value from -1 to 1.
			double positiveAdjustPercent = Math.max(adjustPercent, -adjustPercent);
			// End of line makes seek speed not linear
			long targetTime = startVideoTime + (long) (MAX_VIDEO_STEP_TIME * adjustPercent * (positiveAdjustPercent / 0.1));
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

			player.seekTo(targetTime);
		}


		@Override
		public Rect getPlayerViewRect() {
			return new Rect(playerView.getLeft(), playerView.getTop(), playerView.getRight(), playerView.getBottom());
		}


		private void showIndicator() {
			indicatorView.setVisibility(View.VISIBLE);
		}


		private void hideIndicator() {
			indicatorView.setVisibility(View.GONE);
		}


		/**
		 * Returns a (localized) string for the given duration (in seconds).
		 *
		 * @param duration
		 * @return  a (localized) string for the given duration (in seconds).
		 */
		private String formatDuration(long duration) {
			long    h = duration / 3600;
			long    m = (duration - h * 3600) / 60;
			long    s = duration - (h * 3600 + m * 60);
			String  durationValue;

			if (h == 0) {
				durationValue = String.format(Locale.getDefault(),"%1$02d:%2$02d", m, s);
			} else {
				durationValue = String.format(Locale.getDefault(),"%1$d:%2$02d:%3$02d", h, m, s);
			}

			return durationValue;
		}

	}

	@Override
	public void videoPlaybackStopped() {
		player.stop();
		if(!SkyTubeApp.getPreferenceManager().getBoolean(getString(R.string.pref_key_disable_playback_status), false)) {
			PlaybackStatusDb.getVideoDownloadsDb().setVideoPosition(videoList.get(currentVideoIndex), player.getCurrentPosition());
		}
	}
}
