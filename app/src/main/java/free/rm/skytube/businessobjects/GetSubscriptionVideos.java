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

package free.rm.skytube.businessobjects;

import android.content.SharedPreferences;
import android.widget.Toast;

import com.google.api.client.util.DateTime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import free.rm.skytube.R;
import free.rm.skytube.businessobjects.VideoStream.GetChannelVideos;
import free.rm.skytube.businessobjects.db.SubscriptionsDb;
import free.rm.skytube.gui.app.SkyTubeApp;
import free.rm.skytube.gui.businessobjects.SubscriptionsFragmentListener;

import static free.rm.skytube.gui.app.SkyTubeApp.getContext;

public class GetSubscriptionVideos extends AsyncTaskParallel<Void, Void, Void> {
	private List<GetChannelVideosTask> tasks = new ArrayList<>();
	private SubscriptionsFragmentListener listener;
	private int numTasksLeft = 0;
	private int numTasksFinished = 0;
	boolean foundVideos = false;

	public GetSubscriptionVideos(SubscriptionsFragmentListener listener) {
		this.listener = listener;
	}

	@Override
	protected Void doInBackground(Void... voids) {
		try {
			List<YouTubeChannel> channels = SubscriptionsDb.getSubscriptionsDb().getSubscribedChannels(false);
			for(YouTubeChannel channel : channels) {
				tasks.add(new GetChannelVideosTask(channel));
			}

			numTasksLeft = tasks.size();

			int numToStart = tasks.size() >= 4 ? 4 : tasks.size();

			for(int i=0;i<numToStart;i++) {
				tasks.get(0).executeInParallel();
				tasks.remove(0);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	class GetChannelVideosTask extends AsyncTaskParallel<Void, Void, List<YouTubeVideo>> {
		private GetChannelVideos getChannelVideos = new GetChannelVideos();
		private YouTubeChannel channel;

		public GetChannelVideosTask(YouTubeChannel channel) {
			try {
				getChannelVideos.init();

				/**
				 * Get the last time all subscriptions were updated, and only fetch videos that were published after this.
				 * Any new channels that have been subscribed to since the last time this refresh was done will have any
				 * videos published after the last published time stored in the database, so we don't need to worry about missing
				 * any.
 				 */
				long l = SkyTubeApp.getPreferenceManager().getLong(SkyTubeApp.KEY_SUBSCRIPTIONS_LAST_UPDATED, -1);
				if(l != -1) {
					DateTime subscriptionsLastUpdated = new DateTime(l);
					if (subscriptionsLastUpdated != null)
						getChannelVideos.setPublishedAfter(subscriptionsLastUpdated);
				}

				getChannelVideos.setQuery(channel.getId());
				this.channel = channel;
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(getContext(),
								String.format(getContext().getString(R.string.could_not_get_videos), channel.getTitle()),
								Toast.LENGTH_LONG).show();
			}
		}

		@Override
		protected List<YouTubeVideo> doInBackground(Void... voids) {
			List<YouTubeVideo> videos = null;

			if (!isCancelled()) {
				videos = getChannelVideos.getNextVideos();
			}

			if(videos != null) {
				foundVideos = true;
				for (YouTubeVideo video : videos)
					channel.addYouTubeVideo(video);
				SubscriptionsDb.getSubscriptionsDb().saveChannelVideos(channel);
			}
			return videos;
		}

		@Override
		protected void onPostExecute(List<YouTubeVideo> youTubeVideos) {
			numTasksFinished++;
			if(numTasksFinished < numTasksLeft) {
				if(tasks.size() > 0) {
					// More tasks to do
					tasks.get(0).executeInParallel();
					tasks.remove(0);
				}
			} else {
				// Update the last time this refresh was done.
				SharedPreferences.Editor editor = SkyTubeApp.getPreferenceManager().edit();
				editor.putLong(SkyTubeApp.KEY_SUBSCRIPTIONS_LAST_UPDATED, new DateTime(new Date()).getValue());
				editor.commit();
			}
			if(listener != null)
				listener.onChannelVideosFetched(youTubeVideos != null ? youTubeVideos.size() : 0);
		}

	}

}
