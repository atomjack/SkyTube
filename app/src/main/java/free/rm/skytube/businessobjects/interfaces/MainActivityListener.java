package free.rm.skytube.businessobjects.interfaces;

import free.rm.skytube.businessobjects.YouTubeChannel;

public interface MainActivityListener {
  void onChannelClick(String channelId);
  void onChannelClick(YouTubeChannel channel);
}
