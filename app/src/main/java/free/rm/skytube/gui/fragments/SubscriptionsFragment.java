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

package free.rm.skytube.gui.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;

import free.rm.skytube.R;
import free.rm.skytube.businessobjects.GetSubscriptionVideos;
import free.rm.skytube.businessobjects.db.SubscriptionsDb;
import free.rm.skytube.gui.businessobjects.SubscriptionsFragmentListener;

public class SubscriptionsFragment extends VideosGridFragment implements SubscriptionsFragmentListener {
	private int numVideosFetched = 0;
	private int numChannelsFetched = 0;
	private int numChannelsSubscribed = 0;
	private MaterialDialog progressDialog;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = super.onCreateView(inflater, container, savedInstanceState);

		videoGridAdapter.clearList();
		videoGridAdapter.appendList(SubscriptionsDb.getSubscriptionsDb().getSubscriptionVideos());

		return view;
	}

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
	}

	@Override
	public void onRefresh() {
		numVideosFetched = 0;
		numChannelsFetched = 0;
		numChannelsSubscribed = SubscriptionsDb.getSubscriptionsDb().numSubscribedChannels();
		new GetSubscriptionVideos(this).executeInParallel();
		progressDialog = new MaterialDialog.Builder(getActivity())
						.title(R.string.fetching_subscription_videos)
						.content(String.format(getContext().getString(R.string.fetched_videos_from_channels), numVideosFetched, numChannelsFetched, numChannelsSubscribed))
						.progress(true, 0)
						.backgroundColorRes(R.color.colorPrimary)
						.titleColorRes(android.R.color.white)
						.contentColorRes(android.R.color.white)
						.build();

		progressDialog.show();
	}

	@Override
	public void onChannelVideosFetched(int videosFetched) {
		numVideosFetched += videosFetched;
		numChannelsFetched++;
		progressDialog.setContent(String.format(getContext().getString(R.string.fetched_videos_from_channels), numVideosFetched, numChannelsFetched, numChannelsSubscribed));
		if(numChannelsFetched == numChannelsSubscribed) {
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					// Remove the progress bar(s)
					swipeRefreshLayout.setRefreshing(false);
					progressDialog.dismiss();
					if(numVideosFetched > 0) {
						videoGridAdapter.clearList();
						videoGridAdapter.appendList(SubscriptionsDb.getSubscriptionsDb().getSubscriptionVideos());
					} else {
						Toast.makeText(getContext(),
										String.format(getContext().getString(R.string.no_new_videos_found)),
										Toast.LENGTH_LONG).show();
					}
				}
			}, 500);
		}
	}
}
