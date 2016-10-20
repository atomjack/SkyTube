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

package free.rm.skytube.gui.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.multidex.MultiDex;
import android.support.multidex.MultiDexApplication;

import java.util.HashMap;
import java.util.Map;

import free.rm.skytube.businessobjects.db.SubscriptionsDb;

/**
 * SkyTube application.
 */
public class SkyTubeApp extends MultiDexApplication {

	/** SkyTube Application instance. */
	protected static SkyTubeApp skyTubeApp = null;
	private static volatile SubscriptionsDb subscriptionsDb = null;

	public boolean connectedToChromecast = false;
	public boolean connectingToChromecast = false;
	public Map<String, String> chromecastDevices = new HashMap<>();

	@Override
	public void onCreate() {
		super.onCreate();
		skyTubeApp = this;
	}

	public static SkyTubeApp getInstance() {
		return skyTubeApp;
	}

	/**
	 * Returns a localised string.
	 *
	 * @param stringResId	String resource id (e.g. R.string.my_string)
	 * @return	Localised string, from the strings XML file.
	 */
	public static String getStr(int stringResId) {
		return skyTubeApp.getString(stringResId);
	}


	/**
	 * Returns the App's {@link SharedPreferences}.
	 *
	 * @return {@link SharedPreferences}
	 */
	public static SharedPreferences getPreferenceManager() {
		return PreferenceManager.getDefaultSharedPreferences(skyTubeApp);
	}


	/**
	 * Returns the dimension value that is specified in R.dimens.*.  This value is NOT converted into
	 * pixels, but rather it is kept as it was originally written (e.g. dp).
	 *
	 * @return The dimension value.
	 */
	public static float getDimension(int dimensionId) {
		return skyTubeApp.getResources().getDimension(dimensionId);
	}


	public static Context getContext() {
		return skyTubeApp.getBaseContext();
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
		MultiDex.install(this);
	}
}
