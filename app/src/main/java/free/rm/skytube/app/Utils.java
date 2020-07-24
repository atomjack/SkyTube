/*
 * SkyTube
 * Copyright (C) 2019  Zsombor Gegesy
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
package free.rm.skytube.app;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

public class Utils {

    private static final NumberFormat speedFormatter = new DecimalFormat("0.0x");

    public static String formatSpeed(double speed) {
        return speedFormatter.format(speed);
    }


    // TODO: Eliminate when API level 19 is used.
    public static void requireNonNull(Object obj, String message) {
        if (obj == null) {
            throw new NullPointerException(message);
        }
    }

    public static void isTrue(boolean flag, String message) {
        if (!flag) {
            throw new IllegalArgumentException("Error: "+message);
        }
    }

    public static boolean equals(Object a, Object b) {
        return  a == b || (a != null && a.equals(b));
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static Integer min(Integer a, Integer b) {
        if (a == null) {
            return b;
        } else {
            if (b != null) {
                return Math.min(a, b);
            } else {
                return a;
            }
        }
    }

    public static int hash(Object... obj) {
        return Arrays.hashCode(obj);
    }

    /**
     * Some channelIds have prefix in them.
     * We need to remove them otherwise channel ids might not match in different places and cause problems.
     * @param channelId to check for prefix
     * @return channelId without prefix
     */
    public static String removeChannelIdPrefix(String channelId){
        if (channelId.contains("channel/")){
            channelId = channelId.split("channel/")[1];
        }
        return  channelId;
    }

    /**
     * Parse the passed url and return the YouTubeVideo ID, if it is a valid Video url.
     *
     * @param url
     * @return The video id (String), or null if it is an invalid url.
     */
    @Nullable
    public static String youTubeVideoIdFromUrl(String url) {
        Matcher m = Pattern.compile("(?:https?:\\/\\/)?(?:youtu\\.be\\/|(?:www\\.|m\\.)?youtube\\.com\\/(?:watch|v|embed)(?:\\.php)?(?:\\?.*v=|\\/))([a-zA-Z0-9\\-_]+)").matcher(url);
        if(m.find()) {
            return m.group(1);
        }
        return null;
    }
}
