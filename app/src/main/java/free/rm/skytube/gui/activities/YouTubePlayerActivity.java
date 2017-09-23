/*
 * SkyTube
 * Copyright (C) 2016  Ramon Mifsud
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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;

import free.rm.skytube.R;
import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.businessobjects.interfaces.YouTubePlayerActivityListener;
import free.rm.skytube.gui.fragments.YouTubePlayerFragment;

/**
 * An {@link Activity} that contains an instance of
 * {@link free.rm.skytube.gui.fragments.YouTubePlayerFragment}.
 */
public class YouTubePlayerActivity extends BaseActivity implements YouTubePlayerActivityListener {
	private YouTubePlayerFragment youTubePlayerFragment;
	public static final String YOUTUBE_VIDEO = "YouTubePlayerActivity.YouTubeVideo";
	public static final String YOUTUBE_VIDEO_POSITION = "YouTubePlayerActivity.YouTubeVideoPosition";
	public static final int YOUTUBE_PLAYER_RESUME_RESULT = 2931;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_video_player);

		youTubePlayerFragment = (YouTubePlayerFragment)getSupportFragmentManager().findFragmentById(R.id.youtube_player_fragment);

		// enable back button (action bar)
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.hide();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			// close this activity when the user clicks on the back button (action bar)
			case android.R.id.home:
				finish();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onOptionsMenuCreated(Menu menu) {
		super.onOptionsMenuCreated(menu);
	}

	@Override
	protected boolean isLocalPlayer() {
		return true;
	}

	@Override
	protected void onStart() {
		super.onStart();
		String str = SkyTubeApp.getPreferenceManager().getString(getString(R.string.pref_key_screen_orientation), "auto");
		int orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
		if("landscape".equals(str)) orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
		if("portrait".equals(str)) orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
		if("sensor".equals(str)) orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR;
		setRequestedOrientation(orientation);
	}

	@Override
	protected void onStop() {
		super.onStop();
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
	}

	@Override
	public void onSessionStarting() {
		youTubePlayerFragment.pause();
	}

	// This is called when connecting to a Chromecast from this activity. It will tell BaseActivity
	// to launch the video that was playing on the Chromecast.
	@Override
	protected void returnToMainAndResume() {
		Bundle bundle = new Bundle();
		bundle.putSerializable(YOUTUBE_VIDEO, youTubePlayerFragment.getYouTubeVideo());
		bundle.putInt(YOUTUBE_VIDEO_POSITION, youTubePlayerFragment.getCurrentVideoPosition());

		if(getIntent() != null && getIntent().getAction() != null && getIntent().getAction().equals(Intent.ACTION_VIEW)) {
			Intent intent = new Intent(YouTubePlayerActivity.this, MainActivity.class);
			intent.putExtras(bundle);
			intent.setData(Uri.parse(youTubePlayerFragment.getYouTubeVideo().getVideoUrl()));
			intent.setAction(Intent.ACTION_VIEW);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
			startActivity(intent);
			finish();
		} else {
			Intent intent = new Intent();
			intent.putExtras(bundle);
			setResult(RESULT_OK, intent);
			finish();
		}
	}

	@Override
	public void onSubscriptionsImported() {
	}
}
