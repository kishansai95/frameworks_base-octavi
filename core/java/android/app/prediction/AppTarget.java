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
package android.app.prediction;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.content.pm.ShortcutInfo;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

import com.android.internal.util.Preconditions;

/**
 * A representation of a launchable target.
 * @hide
 */
@SystemApi
public final class AppTarget implements Parcelable {

    private final AppTargetId mId;
    private final String mPackageName;
    private final String mClassName;
    private final UserHandle mUser;

    private final ShortcutInfo mShortcutInfo;

    private int mRank;

    /**
     * @hide
     */
    public AppTarget(@NonNull AppTargetId id, @NonNull String packageName,
            @Nullable String className, @NonNull UserHandle user) {
        mId = id;
        mShortcutInfo = null;

        mPackageName = Preconditions.checkNotNull(packageName);
        mClassName = className;
        mUser = Preconditions.checkNotNull(user);
    }

    /**
     * @hide
     */
    public AppTarget(@NonNull AppTargetId id, @NonNull ShortcutInfo shortcutInfo) {
        mId = id;
        mShortcutInfo = Preconditions.checkNotNull(shortcutInfo);

        mPackageName = mShortcutInfo.getPackage();
        mUser = mShortcutInfo.getUserHandle();
        mClassName = null;
    }

    private AppTarget(Parcel parcel) {
        mId = parcel.readTypedObject(AppTargetId.CREATOR);
        mShortcutInfo = parcel.readTypedObject(ShortcutInfo.CREATOR);
        if (mShortcutInfo == null) {
            mPackageName = parcel.readString();
            mClassName = parcel.readString();
            mUser = UserHandle.of(parcel.readInt());
        } else {
            mPackageName = mShortcutInfo.getPackage();
            mUser = mShortcutInfo.getUserHandle();
            mClassName = null;
        }
        mRank = parcel.readInt();
    }

    /**
     * Returns the target id.
     */
    @NonNull
    public AppTargetId getId() {
        return mId;
    }

    /**
     * Returns the class name for the app target.
     */
    @Nullable
    public String getClassName() {
        return mClassName;
    }

    /**
     * Returns the package name for the app target.
     */
    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    /**
     * Returns the user for the app target.
     */
    @NonNull
    public UserHandle getUser() {
        return mUser;
    }

    /**
     * Returns the shortcut info for the target.
     */
    @Nullable
    public ShortcutInfo getShortcutInfo() {
        return mShortcutInfo;
    }

    /**
     * Sets the rank of the for the target.
     * @hide
     */
    public void setRank(int rank) {
        mRank = rank;
    }

    public int getRank() {
        return mRank;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mId, flags);
        dest.writeTypedObject(mShortcutInfo, flags);
        if (mShortcutInfo == null) {
            dest.writeString(mPackageName);
            dest.writeString(mClassName);
            dest.writeInt(mUser.getIdentifier());
        }
        dest.writeInt(mRank);
    }

    /**
     * @see Parcelable.Creator
     */
    public static final Parcelable.Creator<AppTarget> CREATOR =
            new Parcelable.Creator<AppTarget>() {
                public AppTarget createFromParcel(Parcel parcel) {
                    return new AppTarget(parcel);
                }

                public AppTarget[] newArray(int size) {
                    return new AppTarget[size];
                }
            };
}