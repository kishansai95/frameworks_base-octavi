/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wm;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyFloat;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.res.Configuration;
import android.graphics.Rect;

import org.mockito.invocation.InvocationOnMock;

/**
 * A collection of static functions that can be referenced by other test packages to provide access
 * to WindowManager related test functionality.
 */
public class WindowTestUtils {
    /**
     * Retrieves an instance of a mock {@link WindowManagerService}.
     */
    public static WindowManagerService getMockWindowManagerService() {
        final WindowManagerService service = mock(WindowManagerService.class);
        final WindowHashMap windowMap = new WindowHashMap();
        when(service.getWindowManagerLock()).thenReturn(windowMap);
        return service;
    }

    /**
     * Creates a mock instance of {@link StackWindowController}.
     */
    public static StackWindowController createMockStackWindowContainerController() {
        StackWindowController controller = mock(StackWindowController.class);
        controller.mContainer = mock(TestTaskStack.class);

        // many components rely on the {@link StackWindowController#adjustConfigurationForBounds}
        // to properly set bounds values in the configuration. We must mimick those actions here.
        doAnswer((InvocationOnMock invocationOnMock) -> {
            final Configuration config = invocationOnMock.<Configuration>getArgument(7);
            final Rect bounds = invocationOnMock.<Rect>getArgument(0);
            config.windowConfiguration.setBounds(bounds);
            return null;
        }).when(controller).adjustConfigurationForBounds(any(), any(), any(), any(),
                anyBoolean(), anyBoolean(), anyFloat(), any(), any(), anyInt());

        return controller;
    }

    /**
     * An extension of {@link TestTaskStack}, which overrides package scoped methods that would not
     * normally be mocked out.
     */
    public static class TestTaskStack extends TaskStack {
        TestTaskStack(WindowManagerService service, int stackId) {
            super(service, stackId, null);
        }

        @Override
        void addTask(Task task, int position, boolean showForAllUsers, boolean moveParents) {
            // Do nothing.
        }
    }
}