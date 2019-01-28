/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.keyguard;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settingslib.WirelessUtils;
import com.android.systemui.Dependency;
import com.android.systemui.keyguard.WakefulnessLifecycle;

import java.util.List;
import java.util.Objects;

/**
 * Controller that generates text including the carrier names and/or the status of all the SIM
 * interfaces in the device. Through a callback, the updates can be retrieved either as a list or
 * separated by a given separator {@link CharSequence}.
 */
public class CarrierTextController {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "CarrierTextController";

    private final boolean mIsEmergencyCallCapable;
    private boolean mTelephonyCapable;
    private boolean mShowMissingSim;
    private boolean mShowAirplaneMode;
    private KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private WifiManager mWifiManager;
    private boolean[] mSimErrorState = new boolean[TelephonyManager.getDefault().getPhoneCount()];
    private CarrierTextCallback mCarrierTextCallback;
    private Context mContext;
    private CharSequence mSeparator;
    private WakefulnessLifecycle mWakefulnessLifecycle;
    private final WakefulnessLifecycle.Observer mWakefulnessObserver =
            new WakefulnessLifecycle.Observer() {
                @Override
                public void onFinishedWakingUp() {
                    mCarrierTextCallback.finishedWakingUp();
                }

                @Override
                public void onStartedGoingToSleep() {
                    mCarrierTextCallback.startedGoingToSleep();
                }
            };

    private final KeyguardUpdateMonitorCallback mCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onRefreshCarrierInfo() {
            if (DEBUG) {
                Log.d(TAG, "onRefreshCarrierInfo(), mTelephonyCapable: "
                        + Boolean.toString(mTelephonyCapable));
            }
            updateCarrierText();
        }

        @Override
        public void onTelephonyCapable(boolean capable) {
            if (DEBUG) {
                Log.d(TAG, "onTelephonyCapable() mTelephonyCapable: "
                        + Boolean.toString(capable));
            }
            mTelephonyCapable = capable;
            updateCarrierText();
        }

        public void onSimStateChanged(int subId, int slotId, IccCardConstants.State simState) {
            if (slotId < 0) {
                Log.d(TAG, "onSimStateChanged() - slotId invalid: " + slotId
                        + " mTelephonyCapable: " + Boolean.toString(mTelephonyCapable));
                return;
            }

            if (DEBUG) Log.d(TAG, "onSimStateChanged: " + getStatusForIccState(simState));
            if (getStatusForIccState(simState) == CarrierTextController.StatusMode.SimIoError) {
                mSimErrorState[slotId] = true;
                updateCarrierText();
            } else if (mSimErrorState[slotId]) {
                mSimErrorState[slotId] = false;
                updateCarrierText();
            }
        }
    };

    /**
     * The status of this lock screen. Primarily used for widgets on LockScreen.
     */
    private enum StatusMode {
        Normal, // Normal case (sim card present, it's not locked)
        NetworkLocked, // SIM card is 'network locked'.
        SimMissing, // SIM card is missing.
        SimMissingLocked, // SIM card is missing, and device isn't provisioned; don't allow access
        SimPukLocked, // SIM card is PUK locked because SIM entered wrong too many times
        SimLocked, // SIM card is currently locked
        SimPermDisabled, // SIM card is permanently disabled due to PUK unlock failure
        SimNotReady, // SIM is not ready yet. May never be on devices w/o a SIM.
        SimIoError, // SIM card is faulty
        SimUnknown // SIM card is unknown
    }

    /**
     * Controller that provides updates on text with carriers names or SIM status.
     * Used by {@link CarrierText}.
     *
     * @param separator Separator between different parts of the text
     */
    public CarrierTextController(Context context, CharSequence separator, boolean showAirplaneMode,
            boolean showMissingSim) {
        mContext = context;
        mIsEmergencyCallCapable = context.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);

        mShowAirplaneMode = showAirplaneMode;
        mShowMissingSim = showMissingSim;

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mSeparator = separator;
        mWakefulnessLifecycle = Dependency.get(WakefulnessLifecycle.class);
    }

    /**
     * Checks if there are faulty cards. Adds the text depending on the slot of the card
     *
     * @param text:   current carrier text based on the sim state
     * @param noSims: whether a valid sim card is inserted
     * @return text
     */
    private CharSequence updateCarrierTextWithSimIoError(CharSequence text, boolean noSims) {
        final CharSequence carrier = "";
        CharSequence carrierTextForSimIOError = getCarrierTextForSimState(
                IccCardConstants.State.CARD_IO_ERROR, carrier);
        for (int index = 0; index < mSimErrorState.length; index++) {
            if (mSimErrorState[index]) {
                // In the case when no sim cards are detected but a faulty card is inserted
                // overwrite the text and only show "Invalid card"
                if (noSims) {
                    return concatenate(carrierTextForSimIOError,
                            getContext().getText(
                                    com.android.internal.R.string.emergency_calls_only),
                            mSeparator);
                } else if (index == 0) {
                    // prepend "Invalid card" when faulty card is inserted in slot 0
                    text = concatenate(carrierTextForSimIOError, text, mSeparator);
                } else {
                    // concatenate "Invalid card" when faulty card is inserted in slot 1
                    text = concatenate(text, carrierTextForSimIOError, mSeparator);
                }
            }
        }
        return text;
    }

    /**
     * Sets the listening status of this controller. If the callback is null, it is set to
     * not listening
     *
     * @param callback Callback to provide text updates
     */
    public void setListening(CarrierTextCallback callback) {
        if (callback != null) {
            mCarrierTextCallback = callback;
            if (ConnectivityManager.from(mContext).isNetworkSupported(
                    ConnectivityManager.TYPE_MOBILE)) {
                mKeyguardUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
                mKeyguardUpdateMonitor.registerCallback(mCallback);
                mWakefulnessLifecycle.addObserver(mWakefulnessObserver);
            } else {
                // Don't listen and clear out the text when the device isn't a phone.
                mKeyguardUpdateMonitor = null;
                callback.updateCarrierInfo(new CarrierTextCallbackInfo("", null, false, null));
            }
        } else {
            mCarrierTextCallback = null;
            if (mKeyguardUpdateMonitor != null) {
                mKeyguardUpdateMonitor.removeCallback(mCallback);
                mWakefulnessLifecycle.removeObserver(mWakefulnessObserver);
            }
        }
    }

    protected void updateCarrierText() {
        boolean allSimsMissing = true;
        boolean anySimReadyAndInService = false;
        CharSequence displayText = null;

        List<SubscriptionInfo> subs = mKeyguardUpdateMonitor.getSubscriptionInfo(false);
        final int numSubs = subs.size();
        final int[] subsIds = new int[numSubs];
        if (DEBUG) Log.d(TAG, "updateCarrierText(): " + numSubs);
        for (int i = 0; i < numSubs; i++) {
            int subId = subs.get(i).getSubscriptionId();
            subsIds[i] = subId;
            IccCardConstants.State simState = mKeyguardUpdateMonitor.getSimState(subId);
            CharSequence carrierName = subs.get(i).getCarrierName();
            CharSequence carrierTextForSimState = getCarrierTextForSimState(simState, carrierName);
            if (DEBUG) {
                Log.d(TAG, "Handling (subId=" + subId + "): " + simState + " " + carrierName);
            }
            if (carrierTextForSimState != null) {
                allSimsMissing = false;
                displayText = concatenate(displayText, carrierTextForSimState, mSeparator);
            }
            if (simState == IccCardConstants.State.READY) {
                ServiceState ss = mKeyguardUpdateMonitor.mServiceStates.get(subId);
                if (ss != null && ss.getDataRegState() == ServiceState.STATE_IN_SERVICE) {
                    // hack for WFC (IWLAN) not turning off immediately once
                    // Wi-Fi is disassociated or disabled
                    if (ss.getRilDataRadioTechnology() != ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN
                            || (mWifiManager.isWifiEnabled()
                            && mWifiManager.getConnectionInfo() != null
                            && mWifiManager.getConnectionInfo().getBSSID() != null)) {
                        if (DEBUG) {
                            Log.d(TAG, "SIM ready and in service: subId=" + subId + ", ss=" + ss);
                        }
                        anySimReadyAndInService = true;
                    }
                }
            }
        }
        if (allSimsMissing) {
            if (numSubs != 0) {
                // Shows "No SIM card | Emergency calls only" on devices that are voice-capable.
                // This depends on mPlmn containing the text "Emergency calls only" when the radio
                // has some connectivity. Otherwise, it should be null or empty and just show
                // "No SIM card"
                // Grab the first subscripton, because they all should contain the emergency text,
                // described above.
                displayText = makeCarrierStringOnEmergencyCapable(
                        getMissingSimMessage(), subs.get(0).getCarrierName());
            } else {
                // We don't have a SubscriptionInfo to get the emergency calls only from.
                // Grab it from the old sticky broadcast if possible instead. We can use it
                // here because no subscriptions are active, so we don't have
                // to worry about MSIM clashing.
                CharSequence text =
                        getContext().getText(com.android.internal.R.string.emergency_calls_only);
                Intent i = getContext().registerReceiver(null,
                        new IntentFilter(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION));
                if (i != null) {
                    String spn = "";
                    String plmn = "";
                    if (i.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false)) {
                        spn = i.getStringExtra(TelephonyIntents.EXTRA_SPN);
                    }
                    if (i.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false)) {
                        plmn = i.getStringExtra(TelephonyIntents.EXTRA_PLMN);
                    }
                    if (DEBUG) Log.d(TAG, "Getting plmn/spn sticky brdcst " + plmn + "/" + spn);
                    if (Objects.equals(plmn, spn)) {
                        text = plmn;
                    } else {
                        text = concatenate(plmn, spn, mSeparator);
                    }
                }
                displayText = makeCarrierStringOnEmergencyCapable(getMissingSimMessage(), text);
            }
        }

        displayText = updateCarrierTextWithSimIoError(displayText, allSimsMissing);
        // APM (airplane mode) != no carrier state. There are carrier services
        // (e.g. WFC = Wi-Fi calling) which may operate in APM.
        if (!anySimReadyAndInService && WirelessUtils.isAirplaneModeOn(mContext)) {
            displayText = getAirplaneModeMessage();
        }

        if (mCarrierTextCallback != null) {
            mCarrierTextCallback.updateCarrierInfo(new CarrierTextCallbackInfo(
                    displayText,
                    displayText.toString().split(mSeparator.toString()),
                    anySimReadyAndInService,
                    subsIds));
        }

    }

    private Context getContext() {
        return mContext;
    }

    private String getMissingSimMessage() {
        return mShowMissingSim && mTelephonyCapable
                ? getContext().getString(R.string.keyguard_missing_sim_message_short) : "";
    }

    private String getAirplaneModeMessage() {
        return mShowAirplaneMode
                ? getContext().getString(R.string.airplane_mode) : "";
    }

    /**
     * Top-level function for creating carrier text. Makes text based on simState, PLMN
     * and SPN as well as device capabilities, such as being emergency call capable.
     *
     * @return Carrier text if not in missing state, null otherwise.
     */
    private CharSequence getCarrierTextForSimState(IccCardConstants.State simState,
            CharSequence text) {
        CharSequence carrierText = null;
        CarrierTextController.StatusMode status = getStatusForIccState(simState);
        switch (status) {
            case Normal:
                carrierText = text;
                break;

            case SimNotReady:
                // Null is reserved for denoting missing, in this case we have nothing to display.
                carrierText = ""; // nothing to display yet.
                break;

            case NetworkLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_network_locked_message), text);
                break;

            case SimMissing:
                carrierText = null;
                break;

            case SimPermDisabled:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(
                                R.string.keyguard_permanent_disabled_sim_message_short),
                        text);
                break;

            case SimMissingLocked:
                carrierText = null;
                break;

            case SimLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_locked_message),
                        text);
                break;

            case SimPukLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_puk_locked_message),
                        text);
                break;
            case SimIoError:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_error_message_short),
                        text);
                break;
            case SimUnknown:
                carrierText = null;
                break;
        }

        return carrierText;
    }

    /*
     * Add emergencyCallMessage to carrier string only if phone supports emergency calls.
     */
    private CharSequence makeCarrierStringOnEmergencyCapable(
            CharSequence simMessage, CharSequence emergencyCallMessage) {
        if (mIsEmergencyCallCapable) {
            return concatenate(simMessage, emergencyCallMessage, mSeparator);
        }
        return simMessage;
    }

    /**
     * Determine the current status of the lock screen given the SIM state and other stuff.
     */
    private CarrierTextController.StatusMode getStatusForIccState(IccCardConstants.State simState) {
        // Since reading the SIM may take a while, we assume it is present until told otherwise.
        if (simState == null) {
            return CarrierTextController.StatusMode.Normal;
        }

        final boolean missingAndNotProvisioned =
                !KeyguardUpdateMonitor.getInstance(mContext).isDeviceProvisioned()
                        && (simState == IccCardConstants.State.ABSENT
                        || simState == IccCardConstants.State.PERM_DISABLED);

        // Assume we're NETWORK_LOCKED if not provisioned
        simState = missingAndNotProvisioned ? IccCardConstants.State.NETWORK_LOCKED : simState;
        switch (simState) {
            case ABSENT:
                return CarrierTextController.StatusMode.SimMissing;
            case NETWORK_LOCKED:
                return CarrierTextController.StatusMode.SimMissingLocked;
            case NOT_READY:
                return CarrierTextController.StatusMode.SimNotReady;
            case PIN_REQUIRED:
                return CarrierTextController.StatusMode.SimLocked;
            case PUK_REQUIRED:
                return CarrierTextController.StatusMode.SimPukLocked;
            case READY:
                return CarrierTextController.StatusMode.Normal;
            case PERM_DISABLED:
                return CarrierTextController.StatusMode.SimPermDisabled;
            case UNKNOWN:
                return CarrierTextController.StatusMode.SimUnknown;
            case CARD_IO_ERROR:
                return CarrierTextController.StatusMode.SimIoError;
        }
        return CarrierTextController.StatusMode.SimUnknown;
    }

    private static CharSequence concatenate(CharSequence plmn, CharSequence spn,
            CharSequence separator) {
        final boolean plmnValid = !TextUtils.isEmpty(plmn);
        final boolean spnValid = !TextUtils.isEmpty(spn);
        if (plmnValid && spnValid) {
            return new StringBuilder().append(plmn).append(separator).append(spn).toString();
        } else if (plmnValid) {
            return plmn;
        } else if (spnValid) {
            return spn;
        } else {
            return "";
        }
    }

    private static List<CharSequence> append(List<CharSequence> list, CharSequence string) {
        if (!TextUtils.isEmpty(string)) {
            list.add(string);
        }
        return list;
    }

    private CharSequence getCarrierHelpTextForSimState(IccCardConstants.State simState,
            String plmn, String spn) {
        int carrierHelpTextId = 0;
        CarrierTextController.StatusMode status = getStatusForIccState(simState);
        switch (status) {
            case NetworkLocked:
                carrierHelpTextId = R.string.keyguard_instructions_when_pattern_disabled;
                break;

            case SimMissing:
                carrierHelpTextId = R.string.keyguard_missing_sim_instructions_long;
                break;

            case SimPermDisabled:
                carrierHelpTextId = R.string.keyguard_permanent_disabled_sim_instructions;
                break;

            case SimMissingLocked:
                carrierHelpTextId = R.string.keyguard_missing_sim_instructions;
                break;

            case Normal:
            case SimLocked:
            case SimPukLocked:
                break;
        }

        return mContext.getText(carrierHelpTextId);
    }

    /**
     * Data structure for passing information to CarrierTextController subscribers
     */
    public static final class CarrierTextCallbackInfo {
        public final CharSequence carrierText;
        public final CharSequence[] listOfCarriers;
        public final boolean anySimReady;
        public final int[] subscriptionIds;

        CarrierTextCallbackInfo(CharSequence carrierText, CharSequence[] listOfCarriers,
                boolean anySimReady, int[] subscriptionIds) {
            this.carrierText = carrierText;
            this.listOfCarriers = listOfCarriers;
            this.anySimReady = anySimReady;
            this.subscriptionIds = subscriptionIds;
        }
    }

    /**
     * Callback to communicate to Views
     */
    public interface CarrierTextCallback {
        /**
         * Provides updated carrier information.
         */
        default void updateCarrierInfo(CarrierTextCallbackInfo info) {};

        /**
         * Notifies the View that the device is going to sleep
         */
        default void startedGoingToSleep() {};

        /**
         * Notifies the View that the device finished waking up
         */
        default void finishedWakingUp() {};
    }
}