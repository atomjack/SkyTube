package free.rm.skytube.gui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import free.rm.skytube.R;
import free.rm.skytube.businessobjects.ChromecastListener;
import free.rm.skytube.gui.businessobjects.FragmentEx;

public class ChromecastMiniControllerFragment extends FragmentEx {
	/** {@link com.google.android.gms.cast.MediaInfo} object that contains all the metadata for the currently playing video. */
	private MediaInfo currentPlayingMedia;
	/** {@link RemoteMediaClient} representing the Chromecast this fragment controls. */
	private RemoteMediaClient remoteMediaClient;
	/** The {@link free.rm.skytube.businessobjects.ChromecastListener} Activity that will be notified when play has started and stopped */
	private ChromecastListener activityListener;
	/** The current playback state of the Chromecast */
	private int currentPlayerState = MediaStatus.PLAYER_STATE_IDLE;

	private boolean isSeeking = false;

	/** Playback buttons */
	@Bind(R.id.playButton)
	ImageButton playButton;
	@Bind(R.id.pauseButton)
	ImageButton pauseButton;
	@Bind(R.id.bufferingSpinner)
	View bufferingSpinner;
	@Bind(R.id.videoTitle)
	TextView videoTitle;
	@Bind(R.id.channelName)
	TextView channelName;

	@Bind(R.id.chromecastPlaybackProgressBar)
	ProgressBar chromecastPlaybackProgressBar;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_chromecast_mini_controller, container);
		ButterKnife.bind(this, view);
		return view;
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

	public void init(RemoteMediaClient client) {
		init(client, client.getMediaInfo(), 0);
	}

	public void init(RemoteMediaClient client, MediaInfo media, int position) {
		remoteMediaClient = client;
		currentPlayingMedia = media;
		currentPlayerState = remoteMediaClient.getPlayerState();

		videoTitle.setText(currentPlayingMedia.getMetadata().getString(MediaMetadata.KEY_TITLE));
		channelName.setText(currentPlayingMedia.getMetadata().getString(MediaMetadata.KEY_SUBTITLE));

		// We just either started playback of a video, or resumed the cast session. In the latter case, if there's a video playing, let the activity
		// know so that the panel will appear.
		if(currentPlayerState != MediaStatus.PLAYER_STATE_IDLE) {
			mediaListener.onMetadataUpdated();
			updateButtons();
			activityListener.onPlayStarted();
		}
		remoteMediaClient.addListener(mediaListener);
		setProgressBarUpdater();
		chromecastPlaybackProgressBar.setProgress(position);
	}

	public void setProgress(int progress) {
		chromecastPlaybackProgressBar.setProgress(progress);
	}

	RemoteMediaClient.ProgressListener progressBarUpdater = new RemoteMediaClient.ProgressListener() {
		@Override
		public void onProgressUpdated(long progress, long duration) {
			if(chromecastPlaybackProgressBar.getMax() != duration)
				chromecastPlaybackProgressBar.setMax((int)duration);
			chromecastPlaybackProgressBar.setProgress((int)progress);
		}
	};

	private RemoteMediaClient.Listener mediaListener = new RemoteMediaClient.Listener() {
		@Override
		public void onStatusUpdated() {
			MediaStatus status = remoteMediaClient.getMediaStatus();
			int oldState = currentPlayerState;
			currentPlayerState = status.getPlayerState();

			/** If the new playback state is idle and it is because playback finished or was stopped, let the activity
			 * know that playback has stopped.
 			 */
			if(status.getPlayerState() == MediaStatus.PLAYER_STATE_IDLE && (status.getIdleReason() == MediaStatus.IDLE_REASON_FINISHED || status.getIdleReason() == MediaStatus.IDLE_REASON_CANCELED)) {
				activityListener.onPlayStopped();
				return;
			}

			updateButtons();

			if(isSeeking) {
				isSeeking = false;
				chromecastPlaybackProgressBar.setProgress((int)remoteMediaClient.getApproximateStreamPosition());
			}
			/** If the previous playback state of the Chromecast was idle, and it is no longer idle, let the activity
			 * know that playback has started.
			 */
			if (oldState == MediaStatus.PLAYER_STATE_IDLE && currentPlayerState != MediaStatus.PLAYER_STATE_IDLE) {
				currentPlayingMedia = remoteMediaClient.getMediaInfo();

				/**
				 * Reset the ProgressBar with the new media, and add a progress listener to update the progress bar.
				 * If the video is under 100 seconds long, it will update every second. If the video is over 16.6 minutes
				 * long, it will update every 10 seconds. Inbetween those, it will update in exactly 100 steps.
				 */
				setProgressBarUpdater();
				activityListener.onPlayStarted();
			}
		}

		@Override
		public void onMetadataUpdated() {
		}

		@Override
		public void onQueueStatusUpdated() {

		}

		@Override
		public void onPreloadStatusUpdated() {

		}

		@Override
		public void onSendingRemoteMediaRequest() {

		}
	};

	private void setProgressBarUpdater() {
		long period = remoteMediaClient.getStreamDuration() / 100;
		if(period < 1000)
			period = 1000;
		if(period > 10000)
			period = 10000;
		remoteMediaClient.removeProgressListener(progressBarUpdater);
		remoteMediaClient.addProgressListener(progressBarUpdater, period);
	}

	@Override
	public void onResume() {
		super.onResume();
		if(remoteMediaClient != null) {
			setProgressBarUpdater();
			chromecastPlaybackProgressBar.setProgress((int)remoteMediaClient.getApproximateStreamPosition());
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if(remoteMediaClient != null)
			remoteMediaClient.removeProgressListener(progressBarUpdater);
	}

	/**
	 * Change the visibility of the play/pause/buffering buttons depending on the current playback state.
	 */
	private void updateButtons() {
		if(currentPlayerState == MediaStatus.PLAYER_STATE_PLAYING) {
			playButton.setVisibility(View.GONE);
			pauseButton.setVisibility(View.VISIBLE);
			bufferingSpinner.setVisibility(View.GONE);
		} else if(currentPlayerState == MediaStatus.PLAYER_STATE_PAUSED) {
			pauseButton.setVisibility(View.GONE);
			playButton.setVisibility(View.VISIBLE);
			bufferingSpinner.setVisibility(View.GONE);
		} else if(currentPlayerState == MediaStatus.PLAYER_STATE_BUFFERING) {
			pauseButton.setVisibility(View.GONE);
			playButton.setVisibility(View.GONE);
			bufferingSpinner.setVisibility(View.VISIBLE);
		}
	}

	@OnClick(R.id.playButton)
	public void play(View v) {
		remoteMediaClient.play();
	}

	@OnClick(R.id.pauseButton)
	public void pause(View v) {
		remoteMediaClient.pause();
	}

	@OnClick(R.id.forwardButton)
	public void forward(View v) {
		if(remoteMediaClient.getApproximateStreamPosition() + 30000 < remoteMediaClient.getStreamDuration()) {
			isSeeking = true;
			remoteMediaClient.seek(remoteMediaClient.getApproximateStreamPosition() + 30000);
		}
	}

	@OnClick(R.id.rewindButton)
	public void rewind(View v) {
		isSeeking = true;
		remoteMediaClient.seek(remoteMediaClient.getApproximateStreamPosition() - 10000);
	}

	@OnClick(R.id.stopButton)
	public void stop(View v) {
		remoteMediaClient.stop();
	}
}
