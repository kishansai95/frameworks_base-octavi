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

package com.android.settingslib.widget;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class AppEntitiesHeaderControllerTest {

    private static final CharSequence TITLE = "APP_TITLE";
    private static final CharSequence SUMMARY = "APP_SUMMARY";

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private Context mContext;
    private Drawable mIcon;
    private View mAppEntitiesHeaderView;
    private AppEntitiesHeaderController mController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mAppEntitiesHeaderView = LayoutInflater.from(mContext).inflate(
                R.layout.app_entities_header, null /* root */);
        mIcon = mContext.getDrawable(R.drawable.ic_menu);
        mController = AppEntitiesHeaderController.newInstance(mContext,
                mAppEntitiesHeaderView);
    }

    @Test
    public void assert_amountOfMaximumAppsAreThree() {
        assertThat(AppEntitiesHeaderController.MAXIMUM_APPS).isEqualTo(3);
    }

    @Test
    public void setHeaderTitleRes_setTextRes_shouldSetToTitleView() {
        mController.setHeaderTitleRes(R.string.expand_button_title).apply();
        final TextView view = mAppEntitiesHeaderView.findViewById(R.id.header_title);

        assertThat(view.getText()).isEqualTo(mContext.getText(R.string.expand_button_title));
    }

    @Test
    public void setHeaderDetailsRes_setTextRes_shouldSetToDetailsView() {
        mController.setHeaderDetailsRes(R.string.expand_button_title).apply();
        final TextView view = mAppEntitiesHeaderView.findViewById(R.id.header_details);

        assertThat(view.getText()).isEqualTo(mContext.getText(R.string.expand_button_title));
    }

    @Test
    public void setHeaderDetailsClickListener_setClickListener_detailsViewAttachClickListener() {
        mController.setHeaderDetailsClickListener(v -> {
        }).apply();
        final TextView view = mAppEntitiesHeaderView.findViewById(R.id.header_details);

        assertThat(view.hasOnClickListeners()).isTrue();
    }

    @Test
    public void setAppEntity_indexLessThanZero_shouldThrowArrayIndexOutOfBoundsException() {
        thrown.expect(ArrayIndexOutOfBoundsException.class);

        mController.setAppEntity(-1, mIcon, TITLE, SUMMARY);
    }

    @Test
    public void asetAppEntity_indexGreaterThanMaximum_shouldThrowArrayIndexOutOfBoundsException() {
        thrown.expect(ArrayIndexOutOfBoundsException.class);

        mController.setAppEntity(AppEntitiesHeaderController.MAXIMUM_APPS + 1, mIcon, TITLE,
                SUMMARY);
    }

    @Test
    public void setAppEntity_addAppToIndex0_shouldShowAppView1() {
        mController.setAppEntity(0, mIcon, TITLE, SUMMARY).apply();
        final View app1View = mAppEntitiesHeaderView.findViewById(R.id.app1_view);
        final ImageView appIconView = app1View.findViewById(R.id.app_icon);
        final TextView appTitle = app1View.findViewById(R.id.app_title);
        final TextView appSummary = app1View.findViewById(R.id.app_summary);

        assertThat(app1View.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(appIconView.getDrawable()).isNotNull();
        assertThat(appTitle.getText()).isEqualTo(TITLE);
        assertThat(appSummary.getText()).isEqualTo(SUMMARY);
    }

    @Test
    public void setAppEntity_addAppToIndex1_shouldShowAppView2() {
        mController.setAppEntity(1, mIcon, TITLE, SUMMARY).apply();
        final View app2View = mAppEntitiesHeaderView.findViewById(R.id.app2_view);
        final ImageView appIconView = app2View.findViewById(R.id.app_icon);
        final TextView appTitle = app2View.findViewById(R.id.app_title);
        final TextView appSummary = app2View.findViewById(R.id.app_summary);

        assertThat(app2View.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(appIconView.getDrawable()).isNotNull();
        assertThat(appTitle.getText()).isEqualTo(TITLE);
        assertThat(appSummary.getText()).isEqualTo(SUMMARY);
    }

    @Test
    public void setAppEntity_addAppToIndex2_shouldShowAppView3() {
        mController.setAppEntity(2, mIcon, TITLE, SUMMARY).apply();
        final View app3View = mAppEntitiesHeaderView.findViewById(R.id.app3_view);
        final ImageView appIconView = app3View.findViewById(R.id.app_icon);
        final TextView appTitle = app3View.findViewById(R.id.app_title);
        final TextView appSummary = app3View.findViewById(R.id.app_summary);

        assertThat(app3View.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(appIconView.getDrawable()).isNotNull();
        assertThat(appTitle.getText()).isEqualTo(TITLE);
        assertThat(appSummary.getText()).isEqualTo(SUMMARY);
    }

    @Test
    public void removeAppEntity_removeIndex0_shouldNotShowAppView1() {
        mController.setAppEntity(0, mIcon, TITLE, SUMMARY)
                .setAppEntity(1, mIcon, TITLE, SUMMARY).apply();
        final View app1View = mAppEntitiesHeaderView.findViewById(R.id.app1_view);
        final View app2View = mAppEntitiesHeaderView.findViewById(R.id.app2_view);

        assertThat(app1View.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(app2View.getVisibility()).isEqualTo(View.VISIBLE);

        mController.removeAppEntity(0).apply();

        assertThat(app1View.getVisibility()).isEqualTo(View.GONE);
        assertThat(app2View.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void clearAllAppEntities_shouldNotShowAllAppViews() {
        mController.setAppEntity(0, mIcon, TITLE, SUMMARY)
                .setAppEntity(1, mIcon, TITLE, SUMMARY)
                .setAppEntity(2, mIcon, TITLE, SUMMARY).apply();
        final View app1View = mAppEntitiesHeaderView.findViewById(R.id.app1_view);
        final View app2View = mAppEntitiesHeaderView.findViewById(R.id.app2_view);
        final View app3View = mAppEntitiesHeaderView.findViewById(R.id.app3_view);

        assertThat(app1View.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(app2View.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(app3View.getVisibility()).isEqualTo(View.VISIBLE);

        mController.clearAllAppEntities().apply();
        assertThat(app1View.getVisibility()).isEqualTo(View.GONE);
        assertThat(app2View.getVisibility()).isEqualTo(View.GONE);
        assertThat(app3View.getVisibility()).isEqualTo(View.GONE);
    }
}