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

import android.support.v7.app.AppCompatActivity;
import android.view.Menu;

import free.rm.skytube.businessobjects.YouTubeChannel;
import free.rm.skytube.businessobjects.interfaces.MainActivityListener;

public abstract class BaseActivity extends AppCompatActivity implements MainActivityListener {
	// No-op methods that aren't necessarily needed by all classes that extend this one
	protected void onOptionsMenuCreated(Menu menu) {}
	public void onLayoutSet() {};
	@Override
	public void onChannelClick(YouTubeChannel channel) {}
	@Override
	public void onChannelClick(String channelId) {}
	public void redrawPanel() {}
	protected boolean isLocalPlayer() {
		return false;
	}
	protected void returnToMainAndResume() {}

}
