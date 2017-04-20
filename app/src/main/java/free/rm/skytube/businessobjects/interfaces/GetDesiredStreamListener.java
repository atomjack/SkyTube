package free.rm.skytube.businessobjects.interfaces;

import free.rm.skytube.businessobjects.VideoStream.StreamMetaData;

public interface GetDesiredStreamListener {
	void onGetDesiredStream(StreamMetaData desiredStream);
	void onGetDesiredStreamError(String errorMessage);
}
