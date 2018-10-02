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

package com.android.server.appbinding.finders;

import static android.provider.Telephony.Sms.Intents.ACTION_DEFAULT_SMS_PACKAGE_CHANGED_INTERNAL;

import android.Manifest.permission;
import android.app.ISmsAppService;
import android.app.SmsAppService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.SmsApplication;

import java.util.function.BiConsumer;

/**
 * Find the SmsAppService service within the default SMS app.
 */
public class SmsAppServiceFinder extends AppServiceFinder<SmsAppService, ISmsAppService> {
    public SmsAppServiceFinder(Context context,
            BiConsumer<AppServiceFinder, Integer> listener,
            Handler callbackHandler) {
        super(context, listener, callbackHandler);
    }

    @Override
    public String getAppDescription() {
        return "[Default SMS app]";
    }

    @Override
    protected Class<SmsAppService> getServiceClass() {
        return SmsAppService.class;
    }

    @Override
    public ISmsAppService asInterface(IBinder obj) {
        return ISmsAppService.Stub.asInterface(obj);
    }

    @Override
    protected String getServiceAction() {
        return TelephonyManager.ACTION_SMS_APP_SERVICE;
    }

    @Override
    protected String getServicePermission() {
        return permission.BIND_SMS_APP_SERVICE;
    }

    @Override
    public String getTargetPackage(int userId) {
        final ComponentName cn = SmsApplication.getDefaultSmsApplicationAsUser(
                mContext, /* updateIfNeeded= */ true, userId);
        return cn == null ? null : cn.getPackageName();
    }

    @Override
    public void startMonitoring() {
        final IntentFilter filter = new IntentFilter(ACTION_DEFAULT_SMS_PACKAGE_CHANGED_INTERNAL);
        mContext.registerReceiverAsUser(mSmsAppChangedWatcher, UserHandle.ALL, filter,
                /* permission= */ null, mHandler);
    }

    private final BroadcastReceiver mSmsAppChangedWatcher = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_DEFAULT_SMS_PACKAGE_CHANGED_INTERNAL.equals(intent.getAction())) {
                mListener.accept(SmsAppServiceFinder.this, getSendingUserId());
            }
        }
    };
}