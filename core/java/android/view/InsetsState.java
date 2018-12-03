/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.view;

import android.annotation.IntDef;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArrayMap;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Holder for state of system windows that cause window insets for all other windows in the system.
 * @hide
 */
public class InsetsState implements Parcelable {

    /**
     * Internal representation of inset source types. This is different from the public API in
     * {@link WindowInsets.Type} as one type from the public API might indicate multiple windows
     * at the same time.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "TYPE", value = {
            TYPE_TOP_BAR,
            TYPE_SIDE_BAR_1,
            TYPE_SIDE_BAR_2,
            TYPE_SIDE_BAR_3,
            TYPE_IME
    })
    public @interface InternalInsetType {}

    static final int FIRST_TYPE = 0;

    /** Top bar. Can be status bar or caption in freeform windowing mode. */
    public static final int TYPE_TOP_BAR = FIRST_TYPE;

    /**
     * Up to 3 side bars that appear on left/right/bottom. On phones there is only one side bar
     * (the navigation bar, see {@link #TYPE_NAVIGATION_BAR}), but other form factors might have
     * multiple, like Android Auto.
     */
    public static final int TYPE_SIDE_BAR_1 = 1;
    public static final int TYPE_SIDE_BAR_2 = 2;
    public static final int TYPE_SIDE_BAR_3 = 3;

    /** Input method window. */
    public static final int TYPE_IME = 4;
    static final int LAST_TYPE = TYPE_IME;

    // Derived types

    /** First side bar is navigation bar. */
    public static final int TYPE_NAVIGATION_BAR = TYPE_SIDE_BAR_1;

    /** A shelf is the same as the navigation bar. */
    public static final int TYPE_SHELF = TYPE_NAVIGATION_BAR;

    private final ArrayMap<Integer, InsetsSource> mSources = new ArrayMap<>();

    public InsetsState() {
    }

    /**
     * Calculates {@link WindowInsets} based on the current source configuration.
     *
     * @param frame The frame to calculate the insets relative to.
     * @return The calculated insets.
     */
    public WindowInsets calculateInsets(Rect frame, boolean isScreenRound,
            boolean alwaysConsumeNavBar, DisplayCutout cutout) {
        Insets systemInsets = Insets.NONE;
        Insets maxInsets = Insets.NONE;
        final Rect relativeFrame = new Rect(frame);
        final Rect relativeFrameMax = new Rect(frame);
        for (int type = FIRST_TYPE; type <= LAST_TYPE; type++) {
            InsetsSource source = mSources.get(type);
            if (source == null) {
                continue;
            }
            systemInsets = processSource(source, systemInsets, relativeFrame,
                    false /* ignoreVisibility */);

            // IME won't be reported in max insets as the size depends on the EditorInfo of the IME
            // target.
            if (source.getType() != TYPE_IME) {
                maxInsets = processSource(source, maxInsets, relativeFrameMax,
                        true /* ignoreVisibility */);
            }
        }
        return new WindowInsets(new Rect(systemInsets), null, new Rect(maxInsets), isScreenRound,
                alwaysConsumeNavBar, cutout);
    }

    private Insets processSource(InsetsSource source, Insets insets, Rect relativeFrame,
            boolean ignoreVisibility) {
        Insets currentInsets = source.calculateInsets(relativeFrame, ignoreVisibility);
        insets = Insets.add(currentInsets, insets);
        relativeFrame.inset(insets);
        return insets;
    }

    public InsetsSource getSource(@InternalInsetType int type) {
        return mSources.computeIfAbsent(type, InsetsSource::new);
    }

    /**
     * Modifies the state of this class to exclude a certain type to make it ready for dispatching
     * to the client.
     *
     * @param type The {@link InternalInsetType} of the source to remove
     */
    public void removeSource(int type) {
        mSources.remove(type);
    }

    public void set(InsetsState other) {
        set(other, false /* copySources */);
    }

    public void set(InsetsState other, boolean copySources) {
        mSources.clear();
        if (copySources) {
            for (int i = 0; i < other.mSources.size(); i++) {
                InsetsSource source = other.mSources.valueAt(i);
                mSources.put(source.getType(), new InsetsSource(source));
            }
        } else {
            mSources.putAll(other.mSources);
        }
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.println(prefix + "InsetsState");
        for (int i = mSources.size() - 1; i >= 0; i--) {
            mSources.valueAt(i).dump(prefix + "  ", pw);
        }
    }

    static String typeToString(int type) {
        switch (type) {
            case TYPE_TOP_BAR:
                return "TYPE_TOP_BAR";
            case TYPE_SIDE_BAR_1:
                return "TYPE_SIDE_BAR_1";
            case TYPE_SIDE_BAR_2:
                return "TYPE_SIDE_BAR_2";
            case TYPE_SIDE_BAR_3:
                return "TYPE_SIDE_BAR_3";
            case TYPE_IME:
                return "TYPE_IME";
            default:
                return "TYPE_UNKNOWN";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        InsetsState state = (InsetsState) o;

        if (mSources.size() != state.mSources.size()) {
            return false;
        }
        for (int i = mSources.size() - 1; i >= 0; i--) {
            InsetsSource source = mSources.valueAt(i);
            InsetsSource otherSource = state.mSources.get(source.getType());
            if (otherSource == null) {
                return false;
            }
            if (!otherSource.equals(source)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return mSources.hashCode();
    }

    public InsetsState(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSources.size());
        for (int i = 0; i < mSources.size(); i++) {
            dest.writeParcelable(mSources.valueAt(i), 0 /* flags */);
        }
    }

    public static final Creator<InsetsState> CREATOR = new Creator<InsetsState>() {

        public InsetsState createFromParcel(Parcel in) {
            return new InsetsState(in);
        }

        public InsetsState[] newArray(int size) {
            return new InsetsState[size];
        }
    };

    public void readFromParcel(Parcel in) {
        mSources.clear();
        final int size = in.readInt();
        for (int i = 0; i < size; i++) {
            final InsetsSource source = in.readParcelable(null /* loader */);
            mSources.put(source.getType(), source);
        }
    }
}
