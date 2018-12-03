/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import android.annotation.IntDef;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;

import android.util.Log;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Coordinates with the prototype settings plugin app that uses Settings.Global to allow different
 * prototypes to run in the system. The class will handle communication changes from the settings
 * app and call back to listeners.
 */
public class NavigationPrototypeController extends ContentObserver {
    private static final String HIDE_BACK_BUTTON_SETTING = "quickstepcontroller_hideback";

    static final String NAVBAR_EXPERIMENTS_DISABLED = "navbarexperiments_disabled";
    private final String GESTURE_MATCH_SETTING = "quickstepcontroller_gesture_match_map";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ACTION_DEFAULT, ACTION_QUICKSTEP, ACTION_QUICKSCRUB, ACTION_BACK})
    @interface GestureAction {}
    static final int ACTION_DEFAULT = 0;
    static final int ACTION_QUICKSTEP = 1;
    static final int ACTION_QUICKSCRUB = 2;
    static final int ACTION_BACK = 3;

    private OnPrototypeChangedListener mListener;

    /**
     * Each index corresponds to a different action set in QuickStepController
     * {@see updateSwipeLTRBackSetting}
     */
    private int[] mActionMap = new int[4];

    private final Context mContext;

    public NavigationPrototypeController(Handler handler, Context context) {
        super(handler);
        mContext = context;
        updateSwipeLTRBackSetting();
    }

    public void setOnPrototypeChangedListener(OnPrototypeChangedListener listener) {
        mListener = listener;
    }

    /**
     * Observe all the settings to react to from prototype settings
     */
    public void register() {
        registerObserver(HIDE_BACK_BUTTON_SETTING);
        registerObserver(GESTURE_MATCH_SETTING);
    }

    /**
     * Disable observing settings to react to from prototype settings
     */
    public void unregister() {
        mContext.getContentResolver().unregisterContentObserver(this);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);
        if (!selfChange && mListener != null) {
            try {
                final String path = uri.getPath();
                if (path.endsWith(GESTURE_MATCH_SETTING)) {
                    // Get the settings gesture map corresponding to each action
                    // {@see updateSwipeLTRBackSetting}
                    updateSwipeLTRBackSetting();
                    mListener.onGestureRemap(mActionMap);
                } else if (path.endsWith(HIDE_BACK_BUTTON_SETTING)) {
                    mListener.onBackButtonVisibilityChanged(
                            !getGlobalBool(HIDE_BACK_BUTTON_SETTING));
                }
            } catch (SettingNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Retrieve the action map to apply to the quick step controller
     * @return an action map
     */
    int[] getGestureActionMap() {
        return mActionMap;
    }

    /**
     * Since Settings.Global cannot pass arrays, use a string to represent each character as a
     * gesture map to actions corresponding to {@see GestureAction}. The number is represented as:
     * Number: [up] [down] [left] [right]
     */
    private void updateSwipeLTRBackSetting() {
        String value = Settings.Global.getString(mContext.getContentResolver(),
                GESTURE_MATCH_SETTING);
        if (value != null) {
            for (int i = 0; i < mActionMap.length; ++i) {
                mActionMap[i] = Character.getNumericValue(value.charAt(i));
            }
        }
    }

    private boolean getGlobalBool(String name) throws SettingNotFoundException {
        return Settings.Global.getInt(mContext.getContentResolver(), name) == 1;
    }

    private void registerObserver(String name) {
        mContext.getContentResolver()
                .registerContentObserver(Settings.Global.getUriFor(name), false, this);
    }

    public interface OnPrototypeChangedListener {
        void onGestureRemap(@GestureAction int[] actions);
        void onBackButtonVisibilityChanged(boolean visible);
    }
}