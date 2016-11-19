/*
 * SkyTube
 * Copyright (C) 2015  Ramon Mifsud
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

package free.rm.skytube.gui.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import butterknife.Bind;
import butterknife.ButterKnife;
import free.rm.skytube.BuildConfig;
import free.rm.skytube.R;
import free.rm.skytube.businessobjects.ChromecastListener;
import free.rm.skytube.businessobjects.GetVideoDescriptionTask;
import free.rm.skytube.businessobjects.GetVideoDetailsTask;
import free.rm.skytube.businessobjects.VideoStream.StreamMetaData;
import free.rm.skytube.businessobjects.YouTubeChannel;
import free.rm.skytube.businessobjects.YouTubeVideo;
import free.rm.skytube.businessobjects.interfaces.GetDesiredStreamListener;
import free.rm.skytube.businessobjects.interfaces.GetVideoDetailsListener;
import free.rm.skytube.businessobjects.interfaces.MainActivityListener;
import free.rm.skytube.gui.app.SkyTubeApp;
import free.rm.skytube.gui.businessobjects.Logger;
import free.rm.skytube.gui.fragments.ChromecastMiniControllerFragment;

public abstract class BaseActivity extends AppCompatActivity implements MainActivityListener, ChromecastListener {
	public static final String KEY_PUBLISH_DATE = "publishDate";
	public static final String KEY_DESCRIPTION = "description";
	public static final String KEY_RATING = "rating";
	public static final String KEY_DURATION = "duration";
	public static final String KEY_POSITION = "position";
	public static final String KEY_DURATION_IN_SECONDS = "durationInSeconds";

	private MenuItem mediaRouteMenuItem;
	private CastContext mCastContext;
	private CastSession mCastSession;
	private SessionManager mSessionManager;
	private final SessionManagerListener mSessionManagerListener =
					new SessionManagerListenerImpl();
	public ChromecastMiniControllerFragment chromecastMiniControllerFragment;

	private MediaRouter mediaRouter;
	private MediaRouteSelector mediaRouteSelector;
	private Intent externalPlayIntent;

	@Bind((R.id.sliding_layout))
	protected SlidingUpPanelLayout slidingLayout;

	@Nullable
	@Bind(R.id.chromecastLoadingSpinner)
	ProgressBar chromecastLoadingSpinner;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mCastContext = CastContext.getSharedInstance(this);
		mSessionManager = mCastContext.getSessionManager();

		mediaRouter = MediaRouter.getInstance(getApplicationContext());
		mediaRouteSelector = new MediaRouteSelector.Builder()
						.addControlCategory(CastMediaControlIntent.categoryForCast(BuildConfig.CHROMECAST_APP_ID)).build();
		mediaRouter.addCallback(mediaRouteSelector, new MediaRouter.Callback() {
			@Override
			public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo route) {
				if(SkyTubeApp.getInstance().chromecastDevices.get(route.getId()) == null)
					SkyTubeApp.getInstance().chromecastDevices.put(route.getId(), route.getName());
				SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(BaseActivity.this);
				String defaultChromecastId = sharedPref.getString(getString(R.string.pref_key_autocast), getString(R.string.pref_title_chromecast_none));
				if(route.getId().equals(defaultChromecastId)) {
					if(externalPlayIntent != null) {
						mediaRouter.selectRoute(route);
					}
				}
			}

			@Override
			public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo route) {
				if(SkyTubeApp.getInstance().chromecastDevices.get(route.getId()) == null)
					SkyTubeApp.getInstance().chromecastDevices.put(route.getId(), route.getName());
			}

			@Override
			public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo route) {
				SkyTubeApp.getInstance().chromecastDevices.remove(route.getId());
			}
		});
		handleExternalPlayOnChromecast(getIntent());
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		handleExternalPlayOnChromecast(intent);
	}


	/**
	 * If the Preference has been chosen to always launch videos via external apps via the chosen
	 * default Chromecast.
	 */
	private static boolean launchExternalOnDefaultChromecast(Context context) {
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
		return !sharedPref.getString(context.getString(R.string.pref_key_autocast), context.getString(R.string.pref_key_chromecast_none)).equals(context.getString(R.string.pref_key_chromecast_none));
	}

	private void handleExternalPlayOnChromecast(final Intent intent) {
		if(intent != null) {
			// If we're connected to a chromecast when we receive this video to play, play it there, otherwise
			// forward it on to the local player. HOWEVER, we need to skip this if the calling Intent's class name
			// is YouTubePlayerActivity, otherwise we'll get stuck in an endless loop!
			if(intent.getAction() != null &&
							intent.getAction().equals(Intent.ACTION_VIEW) &&
							!intent.getComponent().getClassName().equals(YouTubePlayerActivity.class.getName()) &&
							(intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {


				/**
				 * If a default Chromecast has been set for videos received via an external app (AutoCast), show the
				 * loading spinner and save the intent (which contains the video to play). When the Chromecast (route) is
				 * discovered above, it will find the default Chromecast and connect to it. Once the connection is
				 * established, {@link #handleExternalPlayOnChromecast(Intent)} will be called again, this time with
				 * externalPlayIntent set, which will allow the video to be launched on the Chromecast. Also only do
				 * this if we aren't already connected to a Chromecast.
				 */
				if(launchExternalOnDefaultChromecast(this) && externalPlayIntent == null && !SkyTubeApp.getInstance().connectedToChromecast) {
					showLoadingSpinner();
					externalPlayIntent = intent;
				} else {
					if (SkyTubeApp.getInstance().connectedToChromecast) {
						new GetVideoDetailsTask(intent, true, new GetVideoDetailsListener() {
							@Override
							public void onSuccess(YouTubeVideo video) {
								playVideoOnChromecast(video, 0);
							}

							@Override
							public void onFailure(String videoUrl) {
								Logger.e("Failed to get video details for %s", videoUrl);
								String err = String.format(getString(R.string.error_invalid_url), videoUrl);
								Toast.makeText(BaseActivity.this, err, Toast.LENGTH_LONG).show();
							}
						}).executeInParallel();
					} else {
						Intent i = new Intent(this, YouTubePlayerActivity.class);
						i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
						i.setAction(Intent.ACTION_VIEW);
						i.setData(intent.getData());
						startActivity(i);
						finish(); // Finish this activity, so that the back button returns to the app that launched this video
					}
				}
			}
		}
	}

	/**
	 * This will be called when the options menu has been created. It's needed to set up the cast icon
	 */
	protected void onOptionsMenuCreated(Menu menu) {
		mediaRouteMenuItem = CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
	}

	@Override
	protected void onResume() {
		mCastSession = mSessionManager.getCurrentCastSession();
		mSessionManager.addSessionManagerListener(mSessionManagerListener);
		if(mCastSession != null && mCastSession.getRemoteMediaClient() != null && mCastSession.getRemoteMediaClient().getPlayerState() != MediaStatus.PLAYER_STATE_IDLE) {
			chromecastMiniControllerFragment.init(mCastSession.getRemoteMediaClient());
			showPanel();
		}
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mSessionManager.removeSessionManagerListener(mSessionManagerListener);
		mCastSession = null;
	}

	/**
	 * This is overridden in YouTubePlayerActivity to return true
	 */
	protected boolean isLocalPlayer() {
		return false;
	}

	/**
	 * This is overridden in YouTubePlayerActivity
	 */
	protected void returnToMainAndResume() {
	}

	/**
	 * If we're returning from YouTubePlayerActivity as a result of the user connecting to a Chromecast
	 * in the middle of playback, launch that video on the Chromecast.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode == YouTubePlayerActivity.YOUTUBE_PLAYER_RESUME_RESULT && resultCode == RESULT_OK) {
			// We're returning to the main activity from the local video player, where a chromecast was connected
			// to in the middle of playback. We should launch the youtube player on the chromecast now
			Bundle bundle = data.getExtras();
			YouTubeVideo video = (YouTubeVideo)bundle.getSerializable(YouTubePlayerActivity.YOUTUBE_VIDEO);
			int position = bundle.getInt(YouTubePlayerActivity.YOUTUBE_VIDEO_POSITION);
			playVideoOnChromecast(video, position);
		}
	}

	private class SessionManagerListenerImpl implements SessionManagerListener {
		@Override
		public void onSessionStarted(Session session, String sessionId) {
			mCastSession = CastContext.getSharedInstance(BaseActivity.this).getSessionManager().getCurrentCastSession();
			invalidateOptionsMenu();
			SkyTubeApp.getInstance().connectedToChromecast = true;
			SkyTubeApp.getInstance().connectingToChromecast = false;
			// If we are connecting in YouTubePlayerActivity, finish that activity and launch the currently playing
			// video on the Chromecast.
			if(isLocalPlayer()) {
				returnToMainAndResume();
			} else if(externalPlayIntent != null) {
				// A default Chromecast has been set to handle external intents, and that Chromecast has now been
				// connected to. Play the video (which is stored in externalPlayIntent).
				handleExternalPlayOnChromecast(externalPlayIntent);
				externalPlayIntent = null;
			}
		}

		@Override
		public void onSessionResumed(Session session, boolean wasSuspended) {
			mCastSession = CastContext.getSharedInstance(BaseActivity.this).getSessionManager().getCurrentCastSession();
			Runnable r = new Runnable() {
				@Override
				public void run() {
					if(mCastSession.getRemoteMediaClient().getPlayerState() != MediaStatus.PLAYER_STATE_IDLE) {
						chromecastMiniControllerFragment.init(mCastSession.getRemoteMediaClient());
					} else if(externalPlayIntent != null) {
						// A default Chromecast has been set to handle external intents, and that Chromecast has now been
						// connected to. Play the video (which is stored in externalPlayIntent).
						handleExternalPlayOnChromecast(externalPlayIntent);
						externalPlayIntent = null;
					}
				}
			};
			// Sometimes when we resume a chromecast session, even if media is actually playing, the player state is still idle here.
			// In that case, wait 500ms and check again (above Runnable). But if it's not idle, do the above right away.
			int delay = mCastSession.getRemoteMediaClient().getPlayerState() != MediaStatus.PLAYER_STATE_IDLE ? 0 : 500;
			new Handler().postDelayed(r, delay);

			invalidateOptionsMenu();
			SkyTubeApp.getInstance().connectedToChromecast = true;
			SkyTubeApp.getInstance().connectingToChromecast = false;
		}

		@Override
		public void onSessionEnded(Session session, int error) {
			SkyTubeApp.getInstance().connectedToChromecast = false;
			hidePanel();
		}

		@Override
		public void onSessionSuspended(Session session, int i) {
			SkyTubeApp.getInstance().connectedToChromecast = false;
		}

		@Override
		public void onSessionStarting(Session session) {
			SkyTubeApp.getInstance().connectingToChromecast = true;
		}

		@Override
		public void onSessionStartFailed(Session session, int i) {
			SkyTubeApp.getInstance().connectingToChromecast = false;
		}

		@Override
		public void onSessionEnding(Session session) {

		}

		@Override
		public void onSessionResuming(Session session, String s) {
			SkyTubeApp.getInstance().connectingToChromecast = true;
		}

		@Override
		public void onSessionResumeFailed(Session session, int i) {
			SkyTubeApp.getInstance().connectingToChromecast = false;
		}
	}

	protected void onLayoutSet() {
		ButterKnife.bind(this);
		slidingLayout.setTouchEnabled(false);
		chromecastMiniControllerFragment = (ChromecastMiniControllerFragment)getSupportFragmentManager().findFragmentById(R.id.chromecastMiniControllerFragment);
	}

	/**
	 * When returning to {@link free.rm.skytube.gui.fragments.MainFragment} from a fragment that uses
	 * CoordinatorLayout, redraw the Sliding Panel. This fixes an apparent bug in CoordinatorLayout that
	 * causes the panel to be positioned improperly (the bottom half of the panel ends up below the screen)
	 */
	@Override
	public void redrawPanel() {
		final LinearLayout dragView = (LinearLayout)findViewById(R.id.dragView);
		if(dragView != null) {
			dragView.post(new Runnable() {
				@Override
				public void run() {
					dragView.requestLayout();
				}
			});
		}
	}

	private void showPanel() {
		if(slidingLayout != null)
			slidingLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
	}

	private void hidePanel() {
		if(slidingLayout != null)
			slidingLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
	}

	@Override
	public void onChannelClick(YouTubeChannel channel) {

	}

	@Override
	public void onChannelClick(String channelId) {
	}

	@Override
	public void playVideoOnChromecast(final YouTubeVideo video, final int position) {
		ProgressBar gridBar = (ProgressBar)findViewById(R.id.loading_progress_bar);
		if(gridBar == null || gridBar.getVisibility() != View.VISIBLE)
			showLoadingSpinner();
		if(video.getDescription() == null) {
			new GetVideoDescriptionTask(video, new GetVideoDescriptionTask.GetVideoDescriptionTaskListener() {
				@Override
				public void onFinished(String description) {
					video.setDescription(description);
					playVideoOnChromecast(video, position);
				}
			}).executeInParallel();
		} else {
			video.getDesiredStream(new GetDesiredStreamListener() {
				@Override
				public void onGetDesiredStream(StreamMetaData desiredStream) {
					if(mCastSession == null)
						return;
					final RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
					MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC);
					metadata.putString(MediaMetadata.KEY_TITLE, video.getTitle());
					metadata.putString(MediaMetadata.KEY_SUBTITLE, video.getChannelName());
					metadata.putString(KEY_PUBLISH_DATE, video.getPublishDatePretty());
					metadata.putString(KEY_DESCRIPTION, video.getDescription());
					metadata.putString(KEY_RATING, video.getThumbsUpPercentageStr());
					metadata.putString(KEY_DURATION, video.getDuration());
					metadata.putInt(KEY_POSITION, position);
					metadata.putInt(KEY_DURATION_IN_SECONDS, video.getDurationInSeconds());

					metadata.addImage(new WebImage(Uri.parse(video.getThumbnailUrl())));

					MediaInfo currentPlayingMedia = new MediaInfo.Builder(desiredStream.getUri().toString())
									.setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
									.setContentType(desiredStream.getFormat().mimeType)
									.setMetadata(metadata)
									.build();

					remoteMediaClient.load(currentPlayingMedia, true, 0);
					chromecastMiniControllerFragment.init(remoteMediaClient, currentPlayingMedia, position);
					// If the Controller panel isn't visible, setting the progress of the progressbar in the mini controller won't
					// work until the panel is visible, so do it as soon as the sliding panel is visible. Adding this listener when
					// the panel is not hidden will lead to a java.util.ConcurrentModificationException the next time a video is
					// switching from local playback to chromecast, so we should only do this if the panel is hidden.
					if(slidingLayout.getPanelState() == SlidingUpPanelLayout.PanelState.HIDDEN) {
						slidingLayout.addPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
							@Override
							public void onPanelSlide(View view, float v) {

							}

							@Override
							public void onPanelStateChanged(View view, SlidingUpPanelLayout.PanelState previousState, SlidingUpPanelLayout.PanelState newState) {
								if (newState == SlidingUpPanelLayout.PanelState.COLLAPSED) {
									chromecastMiniControllerFragment.setProgress(position);
									slidingLayout.removePanelSlideListener(this);
								}
							}
						});
					}
				}

				@Override
				public void onGetDesiredStreamError(String errorMessage) {
					if (errorMessage != null) {
						new AlertDialog.Builder(getApplicationContext())
										.setMessage(errorMessage)
										.setTitle(R.string.error_video_play)
										.setCancelable(false)
										.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
											@Override
											public void onClick(DialogInterface dialog, int which) {
											}
										})
										.show();
					}
				}
			});
		}
	}

	/**
	 * When connected to a Chromecast and a video is clicked, this shows a spinner to indicate
	 * that the video is being loaded.
	 */
	@Override
	public void showLoadingSpinner() {
		if(chromecastLoadingSpinner != null)
			chromecastLoadingSpinner.setVisibility(View.VISIBLE);
	}

	/**
	 * Hide the spinner when play has started, and show the panel that contains the Controller
	 */
	@Override
	public void onPlayStarted() {
		if(chromecastLoadingSpinner != null)
			chromecastLoadingSpinner.setVisibility(View.GONE);
		showPanel();
	}

	/**
	 * Chromecast playback has stopped, so hide the panel.
	 */
	@Override
	public void onPlayStopped() {
		hidePanel();
	}
}
