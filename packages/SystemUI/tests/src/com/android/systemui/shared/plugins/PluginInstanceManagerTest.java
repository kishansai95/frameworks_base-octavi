/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.shared.plugins;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.plugins.Plugin;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.shared.plugins.PluginInstanceManager.PluginInfo;
import com.android.systemui.shared.plugins.VersionInfo.InvalidVersionException;
import com.android.systemui.plugins.annotations.Requires;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.support.test.annotation.UiThreadTest;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PluginInstanceManagerTest extends SysuiTestCase {

    private static final String WHITELISTED_PACKAGE = "com.android.systemui";
    // Static since the plugin needs to be generated by the PluginInstanceManager using newInstance.
    private static Plugin sMockPlugin;

    private HandlerThread mHandlerThread;
    private Context mContextWrapper;
    private PackageManager mMockPm;
    private PluginListener mMockListener;
    private PluginInstanceManager mPluginInstanceManager;
    private PluginManagerImpl mMockManager;
    private VersionInfo mMockVersionInfo;

    @Before
    public void setup() throws Exception {
        mHandlerThread = new HandlerThread("test_thread");
        mHandlerThread.start();
        mContextWrapper = new MyContextWrapper(getContext());
        mMockPm = mock(PackageManager.class);
        mMockListener = mock(PluginListener.class);
        mMockManager = mock(PluginManagerImpl.class);
        when(mMockManager.getClassLoader(any(), any()))
                .thenReturn(getClass().getClassLoader());
        mMockVersionInfo = mock(VersionInfo.class);
        mPluginInstanceManager = new PluginInstanceManager(mContextWrapper, mMockPm, "myAction",
                mMockListener, true, mHandlerThread.getLooper(), mMockVersionInfo,
                mMockManager, true, new String[0]);
        sMockPlugin = mock(Plugin.class);
        when(sMockPlugin.getVersion()).thenReturn(1);
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quit();
        sMockPlugin = null;
    }

    @UiThreadTest
    @Test
    public void testGetPlugin() throws Exception {
        setupFakePmQuery();
        PluginInfo p = mPluginInstanceManager.getPlugin();
        assertNotNull(p.mPlugin);
        verify(sMockPlugin).onCreate(any(), any());
    }

    @Test
    public void testNoPlugins() throws Exception {
        when(mMockPm.queryIntentServices(any(), anyInt())).thenReturn(
                Collections.emptyList());
        mPluginInstanceManager.loadAll();

        waitForIdleSync(mPluginInstanceManager.mPluginHandler);
        waitForIdleSync(mPluginInstanceManager.mMainHandler);

        verify(mMockListener, Mockito.never()).onPluginConnected(any(), any());
    }

    @Test
    public void testPluginCreate() throws Exception {
        createPlugin();

        // Verify startup lifecycle
        verify(sMockPlugin).onCreate(ArgumentCaptor.forClass(Context.class).capture(),
                ArgumentCaptor.forClass(Context.class).capture());
        verify(mMockListener).onPluginConnected(any(), any());
    }

    @Test
    public void testPluginDestroy() throws Exception {
        createPlugin(); // Get into valid created state.

        mPluginInstanceManager.destroy();

        waitForIdleSync(mPluginInstanceManager.mPluginHandler);
        waitForIdleSync(mPluginInstanceManager.mMainHandler);

        // Verify shutdown lifecycle
        verify(mMockListener).onPluginDisconnected(ArgumentCaptor.forClass(Plugin.class).capture());
        verify(sMockPlugin).onDestroy();
    }

    @Test
    public void testIncorrectVersion() throws Exception {
        NotificationManager nm = mock(NotificationManager.class);
        mContext.addMockSystemService(Context.NOTIFICATION_SERVICE, nm);
        setupFakePmQuery();
        doThrow(new InvalidVersionException("", false)).when(mMockVersionInfo).checkVersion(any());

        mPluginInstanceManager.loadAll();

        waitForIdleSync(mPluginInstanceManager.mPluginHandler);
        waitForIdleSync(mPluginInstanceManager.mMainHandler);

        // Plugin shouldn't be connected because it is the wrong version.
        verify(mMockListener, Mockito.never()).onPluginConnected(any(), any());
        verify(nm).notifyAsUser(eq(TestPlugin.class.getName()), eq(SystemMessage.NOTE_PLUGIN),
                any(), eq(UserHandle.ALL));
    }

    @Test
    public void testReloadOnChange() throws Exception {
        createPlugin(); // Get into valid created state.

        mPluginInstanceManager.onPackageChange("com.android.systemui");

        waitForIdleSync(mPluginInstanceManager.mPluginHandler);
        waitForIdleSync(mPluginInstanceManager.mMainHandler);

        // Verify the old one was destroyed.
        verify(mMockListener).onPluginDisconnected(ArgumentCaptor.forClass(Plugin.class).capture());
        verify(sMockPlugin).onDestroy();
        // Also verify we got a second onCreate.
        verify(sMockPlugin, Mockito.times(2)).onCreate(
                ArgumentCaptor.forClass(Context.class).capture(),
                ArgumentCaptor.forClass(Context.class).capture());
        verify(mMockListener, Mockito.times(2)).onPluginConnected(any(), any());
    }

    @Test
    public void testNonDebuggable() throws Exception {
        // Create a version that thinks the build is not debuggable.
        mPluginInstanceManager = new PluginInstanceManager(mContextWrapper, mMockPm, "myAction",
                mMockListener, true, mHandlerThread.getLooper(), mMockVersionInfo,
                mMockManager, false, new String[0]);
        setupFakePmQuery();

        mPluginInstanceManager.loadAll();

        waitForIdleSync(mPluginInstanceManager.mPluginHandler);
        waitForIdleSync(mPluginInstanceManager.mMainHandler);;

        // Non-debuggable build should receive no plugins.
        verify(mMockListener, Mockito.never()).onPluginConnected(any(), any());
    }

    @Test
    public void testNonDebuggable_whitelist() throws Exception {
        // Create a version that thinks the build is not debuggable.
        mPluginInstanceManager = new PluginInstanceManager(mContextWrapper, mMockPm, "myAction",
                mMockListener, true, mHandlerThread.getLooper(), mMockVersionInfo,
                mMockManager, false, new String[] {WHITELISTED_PACKAGE});
        setupFakePmQuery();

        mPluginInstanceManager.loadAll();

        waitForIdleSync(mPluginInstanceManager.mPluginHandler);
        waitForIdleSync(mPluginInstanceManager.mMainHandler);

        // Verify startup lifecycle
        verify(sMockPlugin).onCreate(ArgumentCaptor.forClass(Context.class).capture(),
                ArgumentCaptor.forClass(Context.class).capture());
        verify(mMockListener).onPluginConnected(any(), any());
    }

    @Test
    public void testCheckAndDisable() throws Exception {
        createPlugin(); // Get into valid created state.

        // Start with an unrelated class.
        boolean result = mPluginInstanceManager.checkAndDisable(Activity.class.getName());
        assertFalse(result);
        verify(mMockPm, Mockito.never()).setComponentEnabledSetting(
                ArgumentCaptor.forClass(ComponentName.class).capture(),
                ArgumentCaptor.forClass(int.class).capture(),
                ArgumentCaptor.forClass(int.class).capture());

        // Now hand it a real class and make sure it disables the plugin.
        result = mPluginInstanceManager.checkAndDisable(TestPlugin.class.getName());
        assertTrue(result);
        verify(mMockPm).setComponentEnabledSetting(
                ArgumentCaptor.forClass(ComponentName.class).capture(),
                ArgumentCaptor.forClass(int.class).capture(),
                ArgumentCaptor.forClass(int.class).capture());
    }

    @Test
    public void testDisableAll() throws Exception {
        createPlugin(); // Get into valid created state.

        mPluginInstanceManager.disableAll();

        verify(mMockPm).setComponentEnabledSetting(
                ArgumentCaptor.forClass(ComponentName.class).capture(),
                ArgumentCaptor.forClass(int.class).capture(),
                ArgumentCaptor.forClass(int.class).capture());
    }

    private void setupFakePmQuery() throws Exception {
        List<ResolveInfo> list = new ArrayList<>();
        ResolveInfo info = new ResolveInfo();
        info.serviceInfo = mock(ServiceInfo.class);
        info.serviceInfo.packageName = "com.android.systemui";
        info.serviceInfo.name = TestPlugin.class.getName();
        when(info.serviceInfo.loadLabel(any())).thenReturn("Test Plugin");
        list.add(info);
        when(mMockPm.queryIntentServices(any(), Mockito.anyInt())).thenReturn(list);
        when(mMockPm.getServiceInfo(any(), anyInt())).thenReturn(info.serviceInfo);

        when(mMockPm.checkPermission(Mockito.anyString(), Mockito.anyString())).thenReturn(
                PackageManager.PERMISSION_GRANTED);

        ApplicationInfo appInfo = getContext().getApplicationInfo();
        when(mMockPm.getApplicationInfo(Mockito.anyString(), Mockito.anyInt())).thenReturn(
                appInfo);
    }

    private void createPlugin() throws Exception {
        setupFakePmQuery();

        mPluginInstanceManager.loadAll();

        waitForIdleSync(mPluginInstanceManager.mPluginHandler);
        waitForIdleSync(mPluginInstanceManager.mMainHandler);
    }

    // Real context with no registering/unregistering of receivers.
    private static class MyContextWrapper extends ContextWrapper {
        public MyContextWrapper(Context base) {
            super(base);
        }

        @Override
        public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
            return null;
        }

        @Override
        public void unregisterReceiver(BroadcastReceiver receiver) {
        }

        @Override
        public void sendBroadcast(Intent intent) {
            // Do nothing.
        }
    }

    // This target class doesn't matter, it just needs to have a Requires to hit the flow where
    // the mock version info is called.
    @Requires(target = PluginManagerTest.class, version = 1)
    public static class TestPlugin implements Plugin {
        @Override
        public int getVersion() {
            return sMockPlugin.getVersion();
        }

        @Override
        public void onCreate(Context sysuiContext, Context pluginContext) {
            sMockPlugin.onCreate(sysuiContext, pluginContext);
        }

        @Override
        public void onDestroy() {
            sMockPlugin.onDestroy();
        }
    }
}