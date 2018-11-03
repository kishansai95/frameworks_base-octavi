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

package android.rolecontrollerservice;

import android.app.role.IRoleManagerCallback;

/**
 * @hide
 */
oneway interface IRoleControllerService {

    void onAddRoleHolder(in String roleName, in String packageName,
                         in IRoleManagerCallback callback);

    void onRemoveRoleHolder(in String roleName, in String packageName,
                           in IRoleManagerCallback callback);

    void onClearRoleHolders(in String roleName, in IRoleManagerCallback callback);
}