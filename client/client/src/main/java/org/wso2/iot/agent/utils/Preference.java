package org.wso2.iot.agent.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

/**
 * This class handles all the functionality related to data retrieval and saving to 
 * shared preferences.
 */
public class Preference {
	private static final int DEFAULT_INDEX = 0;

	/**
	 * Put string data to shared preferences in private mode.
	 * @param context - The context of activity which is requesting to put data.
	 * @param key     - Used to identify the value.
	 * @param value   - The actual value to be saved.
	 */
	public static void putString(Context context, String key, String value) {
		SharedPreferences mainPref =
				context.getSharedPreferences(Constants.AGENT_PACKAGE, Context.MODE_PRIVATE);
		Editor editor = mainPref.edit();
		editor.putString(key, value);
		editor.apply();
	}

	/**
	 * Retrieve string data from shared preferences in private mode.
	 * @param context - The context of activity which is requesting to put data.
	 * @param key     - Used to identify the value to to be retrieved.
	 */
	public static String getString(Context context, String key) {
		SharedPreferences mainPref =
				context.getSharedPreferences(Constants.AGENT_PACKAGE, Context.MODE_PRIVATE);
		return mainPref.getString(key, null);
	}

	/**
	 * Put float data to shared preferences in private mode.
	 * @param context - The context of activity which is requesting to put data.
	 * @param key     - Used to identify the value.
	 * @param value   - The actual value to be saved.
	 */
	public static void putFloat(Context context, String key, float value) {
		SharedPreferences mainPref =
				context.getSharedPreferences(Constants.AGENT_PACKAGE, Context.MODE_PRIVATE);
		Editor editor = mainPref.edit();
		editor.putFloat(key, value);
		editor.apply();
	}

	/**
	 * Retrieve float data from shared preferences in private mode.
	 * @param context - The context of activity which is requesting to put data.
	 * @param key     - Used to identify the value to to be retrieved.
	 */
	public static float getFloat(Context context, String key) {
		SharedPreferences mainPref =
				context.getSharedPreferences(Constants.AGENT_PACKAGE, Context.MODE_PRIVATE);
		return mainPref.getFloat(key, DEFAULT_INDEX);
	}

	/**
	 * Put integer data to shared preferences in private mode.
	 * @param context - The context of activity which is requesting to put data.
	 * @param key     - Used to identify the value.
	 * @param value   - The actual value to be saved.
	 */
	public static void putInt(Context context, String key, int value) {
		SharedPreferences mainPref =
				context.getSharedPreferences(Constants.AGENT_PACKAGE, Context.MODE_PRIVATE);
		Editor editor = mainPref.edit();
		editor.putInt(key, value);
		editor.apply();
	}

	/**
	 * Retrieve integer data from shared preferences in private mode.
	 * @param context - The context of activity which is requesting to put data.
	 * @param key     - Used to identify the value to to be retrieved.
	 */
	public static int getInt(Context context, String key) {
		SharedPreferences mainPref =
				context.getSharedPreferences(Constants.AGENT_PACKAGE, Context.MODE_PRIVATE);
		return mainPref.getInt(key, DEFAULT_INDEX);
	}

	/**
	 * Put long data to shared preferences in private mode.
	 * @param context - The context of activity which is requesting to put data.
	 * @param key     - Used to identify the value.
	 * @param value   - The actual value to be saved.
	 */
	public static void putLong(Context context, String key, long value) {
		SharedPreferences mainPref =
				context.getSharedPreferences(Constants.AGENT_PACKAGE, Context.MODE_PRIVATE);
		Editor editor = mainPref.edit();
		editor.putLong(key, value);
		editor.apply();
	}

	/**
	 * Retrieve long data from shared preferences in private mode.
	 * @param context - The context of activity which is requesting to put data.
	 * @param key     - Used to identify the value to to be retrieved.
	 */
	public static long getLong(Context context, String key) {
		SharedPreferences mainPref =
				context.getSharedPreferences(Constants.AGENT_PACKAGE, Context.MODE_PRIVATE);
		return mainPref.getLong(key, DEFAULT_INDEX);
	}

	/**
	 * Put boolean data to shared preferences in private mode.
	 * @param context - The context of activity which is requesting to put data.
	 * @param key     - Used to identify the value.
	 * @param value   - The actual value to be saved.
	 */
	public static void putBoolean(Context context, String key, boolean value) {
		SharedPreferences mainPref =
				context.getSharedPreferences(Constants.AGENT_PACKAGE, Context.MODE_PRIVATE);
		Editor editor = mainPref.edit();
		editor.putBoolean(key, value);
		editor.apply();
	}

	/**
	 * Retrieve boolean data from shared preferences in private mode.
	 * @param context - The context of activity which is requesting to put data.
	 * @param key     - Used to identify the value to to be retrieved.
	 */
	public static boolean getBoolean(Context context, String key) {
		SharedPreferences mainPref =
				context.getSharedPreferences(Constants.AGENT_PACKAGE, Context.MODE_PRIVATE);
		return mainPref.getBoolean(key, false);
	}

	/**
	 * Clear data saved in app local shared preferences.
	 * @param context - The context of activity which is requesting to put data.
	 */
	public static void clearPreferences(Context context) {
		SharedPreferences mainPref =
				context.getSharedPreferences(Constants.AGENT_PACKAGE, Context.MODE_PRIVATE);
		mainPref.edit().clear().apply();
	}

	public static boolean hasPreferenceKey(Context context, String key){
		SharedPreferences mainPref =
				context.getSharedPreferences(Constants.AGENT_PACKAGE, Context.MODE_PRIVATE);
		return mainPref.contains(key);
	}

	public static void removePreference(Context context, String key){
		SharedPreferences mainPref =
				context.getSharedPreferences(Constants.AGENT_PACKAGE, Context.MODE_PRIVATE);
		if (mainPref.contains(key)) {
			Editor editor = mainPref.edit();
			editor.remove(key);
			editor.apply();
		}
	}

}
