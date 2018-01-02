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

package free.rm.skytube.gui.businessobjects;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import free.rm.skytube.R;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeAPIKey;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeVideo;
import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.businessobjects.ChromecastListener;
import free.rm.skytube.businessobjects.GetVideoDetailsTask;
import free.rm.skytube.businessobjects.interfaces.GetVideoDetailsListener;
import free.rm.skytube.gui.activities.BaseActivity;
import free.rm.skytube.gui.activities.YouTubePlayerActivity;
import free.rm.skytube.gui.fragments.YouTubePlayerFragment;

/**
 * Launches YouTube player.
 */
public class YouTubePlayer {

	private static final String TAG = YouTubePlayer.class.getSimpleName();

	/**
	 * Launches the YouTube player so that the user can view the selected video.
	 *
	 * @param youTubeVideo Video to be viewed.
	 */
	public static void launch(YouTubeVideo youTubeVideo, Context context) {
		if(SkyTubeApp.getInstance().connectingToChromecast || SkyTubeApp.getInstance().connectedToChromecast) {
			launchOnChromecast(youTubeVideo, context);
		} else {
			// if the user has selected to play the videos using the official YouTube player
			// (in the preferences/settings) ...
			if (useOfficialYouTubePlayer(context)) {
				launchOfficialYouTubePlayer(youTubeVideo.getId(), context);
			} else {
				launchCustomYouTubePlayer(youTubeVideo, context);
			}
		}
	}


	/**
	 * Launches the custom-made YouTube player so that the user can view the selected video.
	 *
	 * @param videoUrl URL of the video to be watched.
	 */
	public static void launch(String videoUrl, final Context context) {
		if(SkyTubeApp.getInstance().connectingToChromecast || SkyTubeApp.getInstance().connectedToChromecast) {
			new GetVideoDetailsTask(videoUrl, new GetVideoDetailsListener() {
				@Override
				public void onSuccess(YouTubeVideo video) {
					launchOnChromecast(video, context);
				}

				@Override
				public void onFailure(String videoUrl) {
					// TODO: Handle failure (show toast?)
				}
			});
		} else {
			// if the user has selected to play the videos using the official YouTube player
			// (in the preferences/settings) ...
			if (useOfficialYouTubePlayer(context)) {
				launchOfficialYouTubePlayer(YouTubeVideo.getYouTubeIdFromUrl(videoUrl), context);
			} else {
				launchCustomYouTubePlayer(videoUrl, context);
			}
		}
	}

	private static void launchOnChromecast(final YouTubeVideo youTubeVideo, final Context context) {
		if(SkyTubeApp.getInstance().connectingToChromecast) {
			((ChromecastListener)context).showLoadingSpinner();
			// In the process of connecting to a chromecast. Wait 500ms and try again
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					launchOnChromecast(youTubeVideo, context);
				}
			}, 500);
		} else {
			if (context instanceof ChromecastListener) {
				((ChromecastListener) context).playVideoOnChromecast(youTubeVideo, 0);
			} else {
				// TODO: Handle this
			}
		}
	}

	/**
	 * Read the user's preferences and determine if the user wants to use the official YouTube video
	 * player or not.
	 *
	 * @return True if the user wants to use the official player; false otherwise.
	 */
	private static boolean useOfficialYouTubePlayer(Context context) {
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
		return sharedPref.getBoolean(context.getString(R.string.pref_key_use_offical_player), false);
	}


	/**
	 * Launch the official (i.e. non-free) YouTube player.
	 */
	private static void launchOfficialYouTubePlayer(String videoId, Context context) {
		try {
			// try to start the YouTube standalone player
			Intent intent = com.google.android.youtube.player.YouTubeStandalonePlayer.createVideoIntent((Activity) context, YouTubeAPIKey.get().getYouTubeAPIKey(), videoId);
			context.startActivity(intent);
		} catch (Exception e) {
			String errorMsg = context.getString(R.string.launch_offical_player_error);

			// log the error
			Log.e(TAG, errorMsg, e);

			// display the error in an AlertDialog
			new AlertDialog.Builder(context)
					.setTitle(R.string.error)
					.setMessage(errorMsg)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setNeutralButton(android.R.string.ok, null)
					.show();
		}
	}


	/**
	 * Launches the custom-made YouTube player so that the user can view the selected video.
	 *
	 * @param youTubeVideo Video to be viewed.
	 */
	public static void launchCustomYouTubePlayer(YouTubeVideo youTubeVideo, Context context) {
		Intent i = new Intent(context, YouTubePlayerActivity.class);
		i.putExtra(YouTubePlayerFragment.YOUTUBE_VIDEO_OBJ, youTubeVideo);
		((BaseActivity)context).startActivityForResult(i, YouTubePlayerActivity.YOUTUBE_PLAYER_RESUME_RESULT);
	}


	/**
	 * Launch the custom-made YouTube player.
	 */
	private static void launchCustomYouTubePlayer(String videoUrl, Context context) {
		Intent i = new Intent(context, YouTubePlayerActivity.class);
		i.setAction(Intent.ACTION_VIEW);
		i.setData(Uri.parse(videoUrl));
		context.startActivity(i);
	}

}
