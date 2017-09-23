package free.rm.skytube.businessobjects;

import android.util.Log;

public class Logger {
	private static final String TAG = "SkyTube";

	public static void i(Object object, String format, Object ... args)  {
		String msg = String.format(format, args);
		Log.i(object.getClass().getSimpleName(), msg);
	}

	public static void d(Object object, String format, Object ... args) {
		String msg = String.format(format, args);
		Log.d(object.getClass().getSimpleName(), msg);
	}

	public static void w(Object object, String format, Object ... args) {
		String msg = String.format(format, args);
		Log.w(object.getClass().getSimpleName(), msg);
	}

	public static void e(Object object, String format, Object ... args) {
		String msg = String.format(format, args);
		Log.e(object.getClass().getSimpleName(), msg);
	}
}