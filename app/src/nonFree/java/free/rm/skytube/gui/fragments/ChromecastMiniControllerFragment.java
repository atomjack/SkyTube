package free.rm.skytube.gui.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import butterknife.Bind;
import butterknife.ButterKnife;
import free.rm.skytube.R;
import free.rm.skytube.businessobjects.ChromecastListener;
import free.rm.skytube.gui.activities.BaseActivity;
import free.rm.skytube.gui.businessobjects.Logger;

public class ChromecastMiniControllerFragment extends ChromecastBaseControllerFragment {
	public static final String CHROMECAST_MINI_CONTROLLER_FRAGMENT = "free.rm.skytube.CHROMECAST_MINI_CONTROLLER_FRAGMENT";

	/** The {@link free.rm.skytube.businessobjects.ChromecastListener} Activity that will be notified when play has started and stopped */
	private ChromecastListener activityListener;

	@Bind(R.id.videoTitle)
	TextView videoTitle;
	@Bind(R.id.channelName)
	TextView channelName;

	@Bind(R.id.chromecastMiniControllerChevron)
	ImageView chromecastMiniControllerChevron;

	@Bind(R.id.chromecastMiniControllerShareButton)
	ImageButton chromecastMiniControllerShareButton;

	@Bind(R.id.chromecastMiniControllerLeftContainer)
	View chromecastMiniControllerLeftContainer;
	@Bind(R.id.chromecastMiniControllerRightContainer)
	View chromecastMiniControllerRightContainer;

	private SlidingUpPanelLayout slidingLayout;

	private boolean didClickNotification = false;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_chromecast_mini_controller, container);
		ButterKnife.bind(this, view);
		return view;
	}

	@Override
	public void init(RemoteMediaClient client, MediaInfo media, int position) {
		super.init(client, media, position);

		videoTitle.setText(currentPlayingMedia.getMetadata().getString(MediaMetadata.KEY_TITLE));
		channelName.setText(currentPlayingMedia.getMetadata().getString(MediaMetadata.KEY_SUBTITLE));

		// We just either started playback of a video, or resumed the cast session. In the latter case, if there's a video playing, let the activity
		// know so that the panel will appear.
		if(currentPlayerState != MediaStatus.PLAYER_STATE_IDLE) {
			activityListener.onPlayStarted();
		}
		setDuration((int)client.getStreamDuration());

		chromecastMiniControllerShareButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(android.content.Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(android.content.Intent.EXTRA_TEXT, currentPlayingMedia.getMetadata().getString(BaseActivity.KEY_VIDEO_URL));
				startActivity(Intent.createChooser(intent, "Share via"));
			}
		});
	}

	@Override
	protected long getProgressBarPeriod() {
		long period = remoteMediaClient.getStreamDuration() / 100;
		if(period < 1000)
			period = 1000;
		if(period > 10000)
			period = 10000;
		return period;
	}

	@Override
	protected void onPlayStopped() {
		activityListener.onPlayStopped();
	}

	@Override
	protected void onPlayStarted() {
		activityListener.onPlayStarted();
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			activityListener = (ChromecastListener)context;
		} catch (ClassCastException e) {
			throw new ClassCastException(context.toString() + " must implement ChromecastListener to use ChromecastMiniControllerFragment");
		}
	}

	public ImageView getChromecastMiniControllerChevron() {
		return chromecastMiniControllerChevron;
	}


	public void setSlidingLayout(SlidingUpPanelLayout layout) {
		slidingLayout = layout;
		slidingLayout.addPanelSlideListener(panelSlideListener);
		Logger.d("set sliding layout");
		/*
		slidingLayout.addPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
			@Override
			public void onPanelSlide(View panel, float slideOffset) {

			}

			@Override
			public void onPanelStateChanged(View panel, SlidingUpPanelLayout.PanelState previousState, SlidingUpPanelLayout.PanelState newState) {
				if(newState == SlidingUpPanelLayout.PanelState.EXPANDED && (chromecastMiniControllerLeftContainer.getVisibility() == View.VISIBLE || chromecastMiniControllerRightContainer.getVisibility() == View.VISIBLE)) {
					chromecastMiniControllerLeftContainer.setAlpha(0);
					chromecastMiniControllerRightContainer.setAlpha(0);
					chromecastMiniControllerShareButton.setVisibility(View.VISIBLE);
					chromecastPlaybackProgressBar.setVisibility(View.INVISIBLE);
					setDuration((int)currentPlayingMedia.getStreamDuration());
					setProgress((int)remoteMediaClient.getApproximateStreamPosition());
					slidingLayout.removePanelSlideListener(this);
				}
			}
		});
		*/
	}

	private SlidingUpPanelLayout.PanelSlideListener panelSlideListener = new SlidingUpPanelLayout.PanelSlideListener() {
		private boolean wasHidden = true; // hidden by default

		@Override
		public void onPanelSlide(View panel, float slideOffset) {
			// slide offset goes from 0.00xxx to 1.0000 as it is slide from the bottom all the way to the top
			// Fade the buttons in (for sliding down) and out (for sliding up)
			chromecastMiniControllerLeftContainer.setAlpha(1-slideOffset);
			chromecastMiniControllerRightContainer.setAlpha(1-slideOffset);
			if(slideOffset < 1)
				chromecastMiniControllerShareButton.setVisibility(View.INVISIBLE);
		}

		@Override
		public void onPanelStateChanged(View panel, SlidingUpPanelLayout.PanelState previousState, SlidingUpPanelLayout.PanelState newState) {
			// As soon as the panel gets hidden, set wasHidden to true to prevent the animation from showing until we finish going to
			// collapsed from hidden
			if(previousState == SlidingUpPanelLayout.PanelState.HIDDEN)
				wasHidden = true;

			// Hide the mini controller progress bar when the panel is expanded, and show it when it's collapsed
			if(newState == SlidingUpPanelLayout.PanelState.EXPANDED) {
				chromecastPlaybackProgressBar.setVisibility(View.INVISIBLE);
				chromecastMiniControllerShareButton.setVisibility(View.VISIBLE);

				chromecastMiniControllerLeftContainer.setAlpha(0);
				chromecastMiniControllerRightContainer.setAlpha(0);
			} else if(newState == SlidingUpPanelLayout.PanelState.COLLAPSED) {
				chromecastPlaybackProgressBar.setVisibility(View.VISIBLE);
			}

			Logger.d("did click notification: %s", didClickNotification);
			// wasHidden boolean being true will prevent the animation from happening when going from hidden to collapsed
			if ((!wasHidden || didClickNotification) && previousState == SlidingUpPanelLayout.PanelState.DRAGGING && (newState == SlidingUpPanelLayout.PanelState.EXPANDED || newState == SlidingUpPanelLayout.PanelState.COLLAPSED)) {
				didClickNotification = false;
				float begin;
				float end;
				if (newState == SlidingUpPanelLayout.PanelState.EXPANDED) {
					begin = 0f;
					end = 180f;
				} else {
					begin = 180f;
					end = 360f;
				}

				Logger.d("Rotating");
				RotateAnimation anim = new RotateAnimation(begin, end, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
				anim.setInterpolator(new LinearInterpolator());
				anim.setDuration(500);
				anim.setFillAfter(true);
				getChromecastMiniControllerChevron().startAnimation(anim);
			}

			// Since we're now at collapsed, set wasHidden to false to show the animation the next time the state changes.
			if(newState == SlidingUpPanelLayout.PanelState.COLLAPSED)
				wasHidden = false;
		}
	};

	public void setDidClickNotification(boolean didClickNotification) {
		this.didClickNotification = didClickNotification;
	}
}
