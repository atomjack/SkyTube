package free.rm.skytube.gui.fragments.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.widget.Toast;

import free.rm.skytube.R;
import free.rm.skytube.businessobjects.db.PlaybackStatusDb;
import free.rm.skytube.businessobjects.db.SearchHistoryDb;

public class PrivacyPreferenceFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preference_privacy);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(getString(R.string.pref_key_disable_search_history))) {
			CheckBoxPreference disableSearchHistoryPreference = (CheckBoxPreference)findPreference(key);
			// If Search History is disabled, clear the Search History database.
			if(disableSearchHistoryPreference.isChecked()) {
				SearchHistoryDb.getSearchHistoryDb().deleteAllSearchHistory();
				Toast.makeText(getActivity(), getString(R.string.pref_disable_search_history_deleted), Toast.LENGTH_LONG).show();
			}
		} else if (key.equals(getString(R.string.pref_key_disable_playback_status))) {
			CheckBoxPreference disablePlaybackStatusPreference = (CheckBoxPreference)findPreference(key);
			if(disablePlaybackStatusPreference.isChecked()) {
				PlaybackStatusDb.getVideoDownloadsDb().deleteAllPlaybackHistory();
				Toast.makeText(getActivity(), getString(R.string.pref_disable_playback_status_deleted), Toast.LENGTH_LONG).show();
			}
		}
	}
}
