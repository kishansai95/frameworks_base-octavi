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

package android.hardware.biometrics;

import android.os.Bundle;
import android.hardware.biometrics.IBiometricPromptReceiver;
import android.hardware.biometrics.IBiometricServiceReceiver;

/**
 * Communication channel from BiometricPrompt and BiometricManager to BiometricService. The
 * interface does not expose specific biometric modalities. The system will use the default
 * biometric for apps. On devices with more than one, the choice is dictated by user preference in
 * Settings.
 * @hide
 */
interface IBiometricService {
    // Requests authentication. The service choose the appropriate biometric to use, and show
    // the corresponding BiometricDialog.
    void authenticate(IBinder token, long sessionId, int userId,
            IBiometricServiceReceiver receiver, int flags, String opPackageName,
            in Bundle bundle, IBiometricPromptReceiver dialogReceiver);

    // Cancel authentication for the given sessionId
    void cancelAuthentication(IBinder token, String opPackageName);

    // Returns true if the user has at least one enrolled biometric.
    boolean hasEnrolledBiometrics(String opPackageName);
}