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

import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import butterknife.BindView;
import butterknife.ButterKnife;
import free.rm.skytube.R;
import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.businessobjects.YouTubeChannel;
import free.rm.skytube.gui.businessobjects.SubsAdapter;
import free.rm.skytube.gui.businessobjects.YouTubePlayer;
import free.rm.skytube.gui.businessobjects.updates.UpdatesCheckerTask;
import free.rm.skytube.gui.fragments.ChannelBrowserFragment;
import free.rm.skytube.gui.fragments.MainFragment;
import free.rm.skytube.gui.fragments.SearchVideoGridFragment;

/**
 * Main activity (launcher).  This activity holds {@link free.rm.skytube.gui.fragments.VideosGridFragment}.
 */
public class MainActivity extends BaseActivity {
	@BindView(R.id.fragment_container)
	protected FrameLayout fragmentContainer;

	private MainFragment mainFragment;
	private SearchVideoGridFragment searchVideoGridFragment;
	private ChannelBrowserFragment channelBrowserFragment;

	/** Set to true if the UpdatesCheckerTask has run; false otherwise. */
	private static boolean updatesCheckerTaskRan = false;
	public static final String ACTION_VIEW_CHANNEL = "MainActivity.ViewChannel";
	private static final String MAIN_FRAGMENT   = "MainActivity.MainFragment";
	private static final String SEARCH_FRAGMENT = "MainActivity.SearchFragment";
	public static final String CHANNEL_BROWSER_FRAGMENT = "MainActivity.ChannelBrowserFragment";

	private boolean dontAddToBackStack = false;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// check for updates (one time only)
		if (!updatesCheckerTaskRan) {
			new UpdatesCheckerTask(this, false).executeInParallel();
			updatesCheckerTaskRan = true;
		}

		setContentView(R.layout.activity_main);

		// The Extra variant needs to initialize some Fragments that are used for Chromecast control. This is done in onLayoutSet of BaseActivity.
		// The OSS variant has a no-op version of this method, since it doesn't need to do anything else here.
		onLayoutSet();
		
		ButterKnife.bind(this);

		if(fragmentContainer != null) {
			if(savedInstanceState != null) {
				mainFragment = (MainFragment)getSupportFragmentManager().getFragment(savedInstanceState, MAIN_FRAGMENT);
				searchVideoGridFragment = (SearchVideoGridFragment) getSupportFragmentManager().getFragment(savedInstanceState, SEARCH_FRAGMENT);
				channelBrowserFragment = (ChannelBrowserFragment) getSupportFragmentManager().getFragment(savedInstanceState, CHANNEL_BROWSER_FRAGMENT);
			}

			// If this Activity was called to view a particular channel, display that channel via ChannelBrowserFragment, instead of MainFragment
			String action = getIntent().getAction();
			if(action != null && action.equals(ACTION_VIEW_CHANNEL)) {
				//
				dontAddToBackStack = true;
				YouTubeChannel channel = (YouTubeChannel) getIntent().getSerializableExtra(ChannelBrowserFragment.CHANNEL_OBJ);
				onChannelClick(channel);
			} else {
				if(mainFragment == null) {
					mainFragment = new MainFragment();
					getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, mainFragment).commit();
				}
			}
		}
	}

	/**
	 * When subscriptions are imported from a Google/YouTube account, via the Preferences screen (PreferencesActivity),
	 * the boolean {@link SkyTubeApp.updateFeedTab} will get set to true. When we return to this activity afterwards,
	 * reset the boolean and update the Feed tab so that the videos from the newly subscribed channels are shown.
	 */
	@Override
	protected void onResume() {
		super.onResume();
		if(SkyTubeApp.getInstance().updateFeedTab) {
			SkyTubeApp.getInstance().updateFeedTab = false;
			onSubscriptionsImported();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		if(mainFragment != null)
			getSupportFragmentManager().putFragment(outState, MAIN_FRAGMENT, mainFragment);
		if(searchVideoGridFragment != null && searchVideoGridFragment.isVisible())
			getSupportFragmentManager().putFragment(outState, SEARCH_FRAGMENT, searchVideoGridFragment);
		if(channelBrowserFragment != null && channelBrowserFragment.isVisible())
			getSupportFragmentManager().putFragment(outState, CHANNEL_BROWSER_FRAGMENT, channelBrowserFragment);
		super.onSaveInstanceState(outState);
	}


	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity_menu, menu);

		onOptionsMenuCreated(menu);

		// setup the SearchView (actionbar)
		final MenuItem searchItem = menu.findItem(R.id.menu_search);
		final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

		searchView.setQueryHint(getString(R.string.search_videos));
		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextChange(String newText) {
				return false;
			}

			@Override
			public boolean onQueryTextSubmit(String query) {
				// hide the keyboard
				searchView.clearFocus();

				// open SearchVideoGridFragment and display the results
				searchVideoGridFragment = new SearchVideoGridFragment();
				Bundle bundle = new Bundle();
				bundle.putString(SearchVideoGridFragment.QUERY, query);
				searchVideoGridFragment.setArguments(bundle);
				switchToFragment(searchVideoGridFragment);

				return true;
			}
		});

		return true;
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_preferences:
				Intent i = new Intent(this, PreferencesActivity.class);
				startActivity(i);
				return true;
			case R.id.menu_enter_video_url:
				displayEnterVideoUrlDialog();
				return true;
			case android.R.id.home:
				if(mainFragment == null || !mainFragment.isVisible()) {
					onBackPressed();
					return true;
				}
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
	}

	/**
	 * Display the Enter Video URL dialog.
	 */
	private void displayEnterVideoUrlDialog() {
		final AlertDialog alertDialog = new AlertDialog.Builder(this)
			.setView(R.layout.dialog_enter_video_url)
			.setTitle(R.string.enter_video_url)
			.setPositiveButton(R.string.play, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// get the inputted URL string
					final String videoUrl = ((EditText)((AlertDialog) dialog).findViewById(R.id.dialog_url_edittext)).getText().toString();

					// play the video
					YouTubePlayer.launch(videoUrl, MainActivity.this);
				}
			})
			.setNegativeButton(R.string.cancel, null)
			.show();

		// paste whatever there is in the clipboard (hopefully it is a video url)
		((EditText) alertDialog.findViewById(R.id.dialog_url_edittext)).setText(getClipboardItem());

		// clear URL edittext button
		alertDialog.findViewById(R.id.dialog_url_clear_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				((EditText) alertDialog.findViewById(R.id.dialog_url_edittext)).setText("");
			}
		});
	}


	/**
	 * Return the last item stored in the clipboard.
	 *
	 * @return	{@link String}
	 */
	private String getClipboardItem() {
		String item = "";

		ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		if (clipboard.hasPrimaryClip()) {
			android.content.ClipDescription description = clipboard.getPrimaryClipDescription();
			android.content.ClipData data = clipboard.getPrimaryClip();
			if (data != null && description != null && description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN))
				item = String.valueOf(data.getItemAt(0).getText());
		}

		return item;
	}

	/**
	 * For the extra variant, if the Chromecast Controller is visible and expanded, we want to collapse it. So, we must
	 * intercept onBackPressed to make sure it doesn't return us to the homescreen. shouldMinimizeOnBack will take care
	 * of this - on the Extra variant, if the Chromecast Controller is visible and expanded, it will collapse it, and
	 * return false, thus the app will not exit nor will it return to the homescreen. If it's collapsed, or not visible,
	 * it will return true, which then will check if the mainFragment is visible (as opposed to searchFragment). If it is,
	 * it will return to the home screen without exiting, otherwise it will do super.onBackPressed (so in searchFragment,
	 * it will exit from that and return to mainFragment).
	 *
	 * On the OSS variant, shouldMinimizeOnBack will always return true, and the normal checks for mainFragment being visible
	 * will be done.
	 */
	public void onBackPressed() {
		if(shouldMinimizeOnBack()) {
			if (mainFragment != null && mainFragment.isVisible()) {
				// On Android, when the user presses back button, the Activity is destroyed and will be
				// recreated when the user relaunches the app.
				// We do not want that behaviour, instead when the back button is pressed, the app will
				// be **minimized**.
				Intent startMain = new Intent(Intent.ACTION_MAIN);
				startMain.addCategory(Intent.CATEGORY_HOME);
				startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(startMain);
			} else {
				super.onBackPressed();
			}
		}
	}

	private void switchToFragment(Fragment fragment) {
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

		transaction.replace(R.id.fragment_container, fragment);
		if(!dontAddToBackStack)
			transaction.addToBackStack(null);
		else
			dontAddToBackStack = false;
		transaction.commit();
	}



	@Override
	public void onChannelClick(YouTubeChannel channel) {
		Bundle args = new Bundle();
		args.putSerializable(ChannelBrowserFragment.CHANNEL_OBJ, channel);
		switchToChannelBrowserFragment(args);
	}

	@Override
	public void onChannelClick(String channelId) {
		Bundle args = new Bundle();
		args.putString(ChannelBrowserFragment.CHANNEL_ID, channelId);
		switchToChannelBrowserFragment(args);
	}

	private void switchToChannelBrowserFragment(Bundle args) {
		channelBrowserFragment = new ChannelBrowserFragment();
		channelBrowserFragment.setArguments(args);
		switchToFragment(channelBrowserFragment);
	}

	@Override
	public void onSubscriptionsImported() {
		mainFragment.refreshSubscriptionsFeed();
		SubsAdapter.get(MainActivity.this).refreshSubsList();
	}

}
