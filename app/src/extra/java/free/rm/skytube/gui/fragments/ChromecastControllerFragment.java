package free.rm.skytube.gui.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.squareup.picasso.Picasso;

import butterknife.Bind;
import butterknife.ButterKnife;
import free.rm.skytube.R;
import free.rm.skytube.gui.businessobjects.Logger;
import free.rm.skytube.gui.businessobjects.RuntimeView;

public class ChromecastControllerFragment extends ChromecastBaseControllerFragment implements SeekBar.OnSeekBarChangeListener {
	public static final String CHROMECAST_CONTROLLER_FRAGMENT = "free.rm.skytube.CHROMECAST_CONTROLLER_FRAGMENT";

	@Bind(R.id.currentRuntime)
	RuntimeView currentPositionTextView;
	@Bind(R.id.duration)
	RuntimeView durationTextView;
	@Bind(R.id.videoImage)
	ImageView videoImage;


	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		Logger.d("Controller onCreateView");
		View view = inflater.inflate(R.layout.fragment_chromecast_controller, container);
		ButterKnife.bind(this, view);
		((SeekBar)chromecastPlaybackProgressBar).setOnSeekBarChangeListener(this);
		return view;
	}

	@Override
	public void init(RemoteMediaClient client, MediaInfo media, int position) {
		super.init(client, media, position);
//		currentPositionTextView.setMilliseconds(position);
		durationTextView.setMilliseconds(chromecastPlaybackProgressBar.getMax());
		if(media.getMetadata().getImages().size() > 0) {
			Picasso.with(getActivity().getApplicationContext())
							.load(media.getMetadata().getImages().get(0).getUrl().toString())
							.placeholder(R.drawable.thumbnail_default)
							.into(videoImage);
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		Logger.d("on Progress changed: %d, %d", progress, seekBar.getMax());
		if(fromUser) {
		} else {
			if(durationTextView.getMilliseconds() != seekBar.getMax())
				durationTextView.setMilliseconds(seekBar.getMax());
		}
		currentPositionTextView.setMilliseconds(progress);
	}

	@Override
	public void setProgress(int progress) {
		Logger.d("setting seekbar progress to %d", progress);
		super.setProgress(progress);
		currentPositionTextView.setMilliseconds(progress);
	}

	@Override
	public void setDuration(int duration) {
		super.setDuration(duration);
		this.durationTextView.setMilliseconds(duration);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		isSeeking = true;
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		isSeeking = false;
		Logger.d("stopped tracking: %d / %d", seekBar.getProgress(), seekBar.getMax());
		remoteMediaClient.seek(seekBar.getProgress());
		if(remoteMediaClient.getPlayerState() == MediaStatus.PLAYER_STATE_PAUSED)
			remoteMediaClient.play();
	}

	@Override
	protected long getProgressBarPeriod() {
		return 1000;
	}
}
