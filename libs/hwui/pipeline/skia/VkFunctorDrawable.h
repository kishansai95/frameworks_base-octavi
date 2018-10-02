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

#pragma once

#include "FunctorDrawable.h"

#include <utils/RefBase.h>
#include <ui/GraphicBuffer.h>

namespace android {
namespace uirenderer {

namespace skiapipeline {

/**
 * This drawable wraps a Vulkan functor enabling it to be recorded into a list
 * of Skia drawing commands.
 */
class VkFunctorDrawable : public FunctorDrawable {
public:
    VkFunctorDrawable(Functor* functor, GlFunctorLifecycleListener* listener, SkCanvas* canvas)
            : FunctorDrawable(functor, listener, canvas) {}
    virtual ~VkFunctorDrawable();

    void syncFunctor() const override;

    static void vkInvokeFunctor(Functor* functor);

protected:
    virtual void onDraw(SkCanvas* canvas) override;

private:

    // Variables below describe/store temporary offscreen buffer used for Vulkan pipeline.
    sp<GraphicBuffer> mFrameBuffer;
    SkImageInfo mFBInfo;
};

};  // namespace skiapipeline
};  // namespace uirenderer
};  // namespace android