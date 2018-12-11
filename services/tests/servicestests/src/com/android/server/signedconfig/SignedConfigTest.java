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

package com.android.server.signedconfig;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import static java.util.Collections.emptySet;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.collect.Sets;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;


/**
 * Tests for {@link SignedConfig}
 */
@RunWith(AndroidJUnit4.class)
public class SignedConfigTest {

    private static Set<String> setOf(String... values) {
        return Sets.newHashSet(values);
    }

    @Test
    public void testParsePerSdkConfigSdkMinMax() throws JSONException, InvalidConfigException {
        JSONObject json = new JSONObject("{\"minSdk\":2, \"maxSdk\": 3, \"values\": []}");
        SignedConfig.PerSdkConfig config = SignedConfig.parsePerSdkConfig(json, emptySet());
        assertThat(config.minSdk).isEqualTo(2);
        assertThat(config.maxSdk).isEqualTo(3);
    }

    @Test
    public void testParsePerSdkConfigNoMinSdk() throws JSONException {
        JSONObject json = new JSONObject("{\"maxSdk\": 3, \"values\": []}");
        try {
            SignedConfig.parsePerSdkConfig(json, emptySet());
            fail("Expected InvalidConfigException or JSONException");
        } catch (JSONException | InvalidConfigException e) {
            // expected
        }
    }

    @Test
    public void testParsePerSdkConfigNoMaxSdk() throws JSONException {
        JSONObject json = new JSONObject("{\"minSdk\": 1, \"values\": []}");
        try {
            SignedConfig.parsePerSdkConfig(json, emptySet());
            fail("Expected InvalidConfigException or JSONException");
        } catch (JSONException | InvalidConfigException e) {
            // expected
        }
    }

    @Test
    public void testParsePerSdkConfigNoValues() throws JSONException {
        JSONObject json = new JSONObject("{\"minSdk\": 1, \"maxSdk\": 3}");
        try {
            SignedConfig.parsePerSdkConfig(json, emptySet());
            fail("Expected InvalidConfigException or JSONException");
        } catch (JSONException | InvalidConfigException e) {
            // expected
        }
    }

    @Test
    public void testParsePerSdkConfigSdkNullMinSdk() throws JSONException, InvalidConfigException {
        JSONObject json = new JSONObject("{\"minSdk\":null, \"maxSdk\": 3, \"values\": []}");
        try {
            SignedConfig.parsePerSdkConfig(json, emptySet());
            fail("Expected InvalidConfigException or JSONException");
        } catch (JSONException | InvalidConfigException e) {
            // expected
        }
    }

    @Test
    public void testParsePerSdkConfigSdkNullMaxSdk() throws JSONException, InvalidConfigException {
        JSONObject json = new JSONObject("{\"minSdk\":1, \"maxSdk\": null, \"values\": []}");
        try {
            SignedConfig.parsePerSdkConfig(json, emptySet());
            fail("Expected InvalidConfigException or JSONException");
        } catch (JSONException | InvalidConfigException e) {
            // expected
        }
    }

    @Test
    public void testParsePerSdkConfigNullValues() throws JSONException {
        JSONObject json = new JSONObject("{\"minSdk\": 1, \"maxSdk\": 3, \"values\": null}");
        try {
            SignedConfig.parsePerSdkConfig(json, emptySet());
            fail("Expected InvalidConfigException or JSONException");
        } catch (JSONException | InvalidConfigException e) {
            // expected
        }
    }

    @Test
    public void testParsePerSdkConfigZeroValues()
            throws JSONException, InvalidConfigException {
        JSONObject json = new JSONObject("{\"minSdk\": 1, \"maxSdk\": 3, \"values\": []}");
        SignedConfig.PerSdkConfig config = SignedConfig.parsePerSdkConfig(json, setOf("a", "b"));
        assertThat(config.values).hasSize(0);
    }

    @Test
    public void testParsePerSdkConfigSingleKey()
            throws JSONException, InvalidConfigException {
        JSONObject json = new JSONObject(
                "{\"minSdk\": 1, \"maxSdk\": 1, \"values\": [{\"key\":\"a\", \"value\": \"1\"}]}");
        SignedConfig.PerSdkConfig config = SignedConfig.parsePerSdkConfig(json, setOf("a", "b"));
        assertThat(config.values).containsExactly("a", "1");
    }

    @Test
    public void testParsePerSdkConfigMultiKeys()
            throws JSONException, InvalidConfigException {
        JSONObject json = new JSONObject(
                "{\"minSdk\": 1, \"maxSdk\": 1, \"values\": [{\"key\":\"a\", \"value\": \"1\"}, "
                        + "{\"key\":\"c\", \"value\": \"2\"}]}");
        SignedConfig.PerSdkConfig config = SignedConfig.parsePerSdkConfig(
                json, setOf("a", "b", "c"));
        assertThat(config.values).containsExactly("a", "1", "c", "2");
    }

    @Test
    public void testParsePerSdkConfigSingleKeyNotAllowed() throws JSONException {
        JSONObject json = new JSONObject(
                "{\"minSdk\": 1, \"maxSdk\": 1, \"values\": [{\"key\":\"a\", \"value\": \"1\"}]}");
        try {
            SignedConfig.parsePerSdkConfig(json, setOf("b"));
            fail("Expected InvalidConfigException or JSONException");
        } catch (JSONException | InvalidConfigException e) {
            // expected
        }
    }

    @Test
    public void testParsePerSdkConfigSingleKeyNoValue()
            throws JSONException, InvalidConfigException {
        JSONObject json = new JSONObject(
                "{\"minSdk\": 1, \"maxSdk\": 1, \"values\": [{\"key\":\"a\"}]}");
        SignedConfig.PerSdkConfig config = SignedConfig.parsePerSdkConfig(json, setOf("a", "b"));
        assertThat(config.values).containsExactly("a", null);
    }

    @Test
    public void testParsePerSdkConfigValuesInvalid() throws JSONException  {
        JSONObject json = new JSONObject("{\"minSdk\": 1, \"maxSdk\": 1,  \"values\": \"foo\"}");
        try {
            SignedConfig.parsePerSdkConfig(json, emptySet());
            fail("Expected InvalidConfigException or JSONException");
        } catch (JSONException | InvalidConfigException e) {
            // expected
        }
    }

    @Test
    public void testParsePerSdkConfigConfigEntryInvalid() throws JSONException {
        JSONObject json = new JSONObject("{\"minSdk\": 1, \"maxSdk\": 1,  \"values\": [1, 2]}");
        try {
            SignedConfig.parsePerSdkConfig(json, emptySet());
            fail("Expected InvalidConfigException or JSONException");
        } catch (JSONException | InvalidConfigException e) {
            // expected
        }
    }

    @Test
    public void testParsePerSdkConfigConfigEntryNull() throws JSONException {
        JSONObject json = new JSONObject("{\"minSdk\": 1, \"maxSdk\": 1,  \"values\": [null]}");
        try {
            SignedConfig.parsePerSdkConfig(json, emptySet());
            fail("Expected InvalidConfigException or JSONException");
        } catch (JSONException | InvalidConfigException e) {
            // expected
        }
    }

    @Test
    public void testParseVersion() throws InvalidConfigException {
        SignedConfig config = SignedConfig.parse(
                "{\"version\": 1, \"config\": []}", emptySet());
        assertThat(config.version).isEqualTo(1);
    }

    @Test
    public void testParseVersionInvalid() {
        try {
            SignedConfig.parse("{\"version\": \"notanint\", \"config\": []}", emptySet());
            fail("Expected InvalidConfigException");
        } catch (InvalidConfigException e) {
            //expected
        }
    }

    @Test
    public void testParseNoVersion() {
        try {
            SignedConfig.parse("{\"config\": []}", emptySet());
            fail("Expected InvalidConfigException");
        } catch (InvalidConfigException e) {
            //expected
        }
    }

    @Test
    public void testParseNoConfig() {
        try {
            SignedConfig.parse("{\"version\": 1}", emptySet());
            fail("Expected InvalidConfigException");
        } catch (InvalidConfigException e) {
            //expected
        }
    }

    @Test
    public void testParseConfigNull() {
        try {
            SignedConfig.parse("{\"version\": 1, \"config\": null}", emptySet());
            fail("Expected InvalidConfigException");
        } catch (InvalidConfigException e) {
            //expected
        }
    }

    @Test
    public void testParseVersionNull() {
        try {
            SignedConfig.parse("{\"version\": null, \"config\": []}", emptySet());
            fail("Expected InvalidConfigException");
        } catch (InvalidConfigException e) {
            //expected
        }
    }

    @Test
    public void testParseConfigInvalidEntry() {
        try {
            SignedConfig.parse("{\"version\": 1, \"config\": [{}]}", emptySet());
            fail("Expected InvalidConfigException");
        } catch (InvalidConfigException e) {
            //expected
        }
    }

    @Test
    public void testParseSdkConfigSingle() throws InvalidConfigException {
        SignedConfig config = SignedConfig.parse(
                "{\"version\": 1, \"config\":[{\"minSdk\": 1, \"maxSdk\": 1, \"values\": []}]}",
                emptySet());
        assertThat(config.perSdkConfig).hasSize(1);
    }

    @Test
    public void testParseSdkConfigMultiple() throws InvalidConfigException {
        SignedConfig config = SignedConfig.parse(
                "{\"version\": 1, \"config\":[{\"minSdk\": 1, \"maxSdk\": 1, \"values\": []}, "
                        + "{\"minSdk\": 2, \"maxSdk\": 2, \"values\": []}]}", emptySet());
        assertThat(config.perSdkConfig).hasSize(2);
    }

    @Test
    public void testGetMatchingConfigFirst() {
        SignedConfig.PerSdkConfig sdk1 = new SignedConfig.PerSdkConfig(
                1, 1, Collections.emptyMap());
        SignedConfig.PerSdkConfig sdk2 = new SignedConfig.PerSdkConfig(
                2, 2, Collections.emptyMap());
        SignedConfig config = new SignedConfig(0, Arrays.asList(sdk1, sdk2));
        assertThat(config.getMatchingConfig(1)).isEqualTo(sdk1);
    }

    @Test
    public void testGetMatchingConfigSecond() {
        SignedConfig.PerSdkConfig sdk1 = new SignedConfig.PerSdkConfig(
                1, 1, Collections.emptyMap());
        SignedConfig.PerSdkConfig sdk2 = new SignedConfig.PerSdkConfig(
                2, 2, Collections.emptyMap());
        SignedConfig config = new SignedConfig(0, Arrays.asList(sdk1, sdk2));
        assertThat(config.getMatchingConfig(2)).isEqualTo(sdk2);
    }

    @Test
    public void testGetMatchingConfigInRange() {
        SignedConfig.PerSdkConfig sdk13 = new SignedConfig.PerSdkConfig(
                1, 3, Collections.emptyMap());
        SignedConfig.PerSdkConfig sdk46 = new SignedConfig.PerSdkConfig(
                4, 6, Collections.emptyMap());
        SignedConfig config = new SignedConfig(0, Arrays.asList(sdk13, sdk46));
        assertThat(config.getMatchingConfig(2)).isEqualTo(sdk13);
    }

    @Test
    public void testGetMatchingConfigNoMatch() {
        SignedConfig.PerSdkConfig sdk1 = new SignedConfig.PerSdkConfig(
                1, 1, Collections.emptyMap());
        SignedConfig.PerSdkConfig sdk2 = new SignedConfig.PerSdkConfig(
                2, 2, Collections.emptyMap());
        SignedConfig config = new SignedConfig(0, Arrays.asList(sdk1, sdk2));
        assertThat(config.getMatchingConfig(3)).isNull();
    }

}