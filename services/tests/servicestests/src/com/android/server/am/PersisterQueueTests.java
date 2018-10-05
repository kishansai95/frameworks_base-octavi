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
 *
 */

package com.android.server.am;

import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertSame;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

/**
 * atest PersisterQueueTests
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
@Presubmit
@FlakyTest(detail = "Confirm stable in post-submit before removing")
public class PersisterQueueTests implements PersisterQueue.Listener {
    private static final long INTER_WRITE_DELAY_MS = 50;
    private static final long PRE_TASK_DELAY_MS = 300;
    // We allow at most 1s more than the expected timeout.
    private static final long TIMEOUT_ALLOWANCE = 100;

    private static final Predicate<MatchingTestItem> TEST_ITEM_PREDICATE = item -> item.mMatching;

    private AtomicInteger mItemCount;
    private CountDownLatch mSetUpLatch;
    private volatile CountDownLatch mLatch;
    private List<Boolean> mProbablyDoneResults;

    private PersisterQueue mTarget;

    @Before
    public void setUp() throws Exception {
        mItemCount = new AtomicInteger(0);
        mProbablyDoneResults = new ArrayList<>();
        mSetUpLatch = new CountDownLatch(1);

        mTarget = new PersisterQueue(INTER_WRITE_DELAY_MS, PRE_TASK_DELAY_MS);
        mTarget.addListener(this);
        mTarget.startPersisting();

        assertTrue("Target didn't call callback on start up.",
                mSetUpLatch.await(TIMEOUT_ALLOWANCE, TimeUnit.MILLISECONDS));
    }

    @After
    public void tearDown() throws Exception {
        mTarget.stopPersisting();
    }

    @Test
    public void testCallCallbackOnStartUp() throws Exception {
        // The onPreProcessItem() must be called on start up.
        assertEquals(1, mProbablyDoneResults.size());
        // The last one must be called with probably done being true.
        assertTrue("The last probablyDone must be true.", mProbablyDoneResults.get(0));
    }

    @Test
    public void testProcessOneItem() throws Exception {
        mLatch = new CountDownLatch(1);

        final long dispatchTime = SystemClock.uptimeMillis();
        mTarget.addItem(new TestItem(), false);
        assertTrue("Target didn't call callback enough times.",
                mLatch.await(PRE_TASK_DELAY_MS + TIMEOUT_ALLOWANCE, TimeUnit.MILLISECONDS));
        assertEquals("Target didn't process item.", 1, mItemCount.get());
        final long processDuration = SystemClock.uptimeMillis() - dispatchTime;
        assertTrue("Target didn't wait enough time before processing item. duration: "
                        + processDuration + "ms pretask delay: " + PRE_TASK_DELAY_MS + "ms",
                processDuration >= PRE_TASK_DELAY_MS);

        // Once before processing this item, once after that.
        assertEquals(2, mProbablyDoneResults.size());
        // The last one must be called with probably done being true.
        assertTrue("The last probablyDone must be true.", mProbablyDoneResults.get(1));
    }

    @Test
    public void testProcessOneItem_Flush() throws Exception {
        mLatch = new CountDownLatch(1);

        final long dispatchTime = SystemClock.uptimeMillis();
        mTarget.addItem(new TestItem(), true);
        assertTrue("Target didn't call callback enough times.",
                mLatch.await(TIMEOUT_ALLOWANCE, TimeUnit.MILLISECONDS));
        assertEquals("Target didn't process item.", 1, mItemCount.get());
        final long processDuration = SystemClock.uptimeMillis() - dispatchTime;
        assertTrue("Target didn't process item immediately when flushing. duration: "
                        + processDuration + "ms pretask delay: "
                        + PRE_TASK_DELAY_MS + "ms",
                processDuration < PRE_TASK_DELAY_MS);

        // Once before processing this item, once after that.
        assertEquals(2, mProbablyDoneResults.size());
        // The last one must be called with probably done being true.
        assertTrue("The last probablyDone must be true.", mProbablyDoneResults.get(1));
    }

    @Test
    public void testProcessTwoItems() throws Exception {
        mLatch = new CountDownLatch(2);

        final long dispatchTime = SystemClock.uptimeMillis();
        mTarget.addItem(new TestItem(), false);
        mTarget.addItem(new TestItem(), false);
        assertTrue("Target didn't call callback enough times.",
                mLatch.await(PRE_TASK_DELAY_MS + INTER_WRITE_DELAY_MS + TIMEOUT_ALLOWANCE,
                        TimeUnit.MILLISECONDS));
        assertEquals("Target didn't process all items.", 2, mItemCount.get());
        final long processDuration = SystemClock.uptimeMillis() - dispatchTime;
        assertTrue("Target didn't wait enough time before processing item. duration: "
                        + processDuration + "ms pretask delay: " + PRE_TASK_DELAY_MS
                        + "ms inter write delay: " + INTER_WRITE_DELAY_MS + "ms",
                processDuration >= PRE_TASK_DELAY_MS + INTER_WRITE_DELAY_MS);

        // Once before processing this item, once after that.
        assertEquals(3, mProbablyDoneResults.size());
        // The first one must be called with probably done being false.
        assertFalse("The first probablyDone must be false.", mProbablyDoneResults.get(1));
        // The last one must be called with probably done being true.
        assertTrue("The last probablyDone must be true.", mProbablyDoneResults.get(2));
    }

    @Test
    public void testProcessTwoItems_OneAfterAnother() throws Exception {
        // First item
        mLatch = new CountDownLatch(1);
        long dispatchTime = SystemClock.uptimeMillis();
        mTarget.addItem(new TestItem(), false);
        assertTrue("Target didn't call callback enough times.",
                mLatch.await(PRE_TASK_DELAY_MS + TIMEOUT_ALLOWANCE, TimeUnit.MILLISECONDS));
        long processDuration = SystemClock.uptimeMillis() - dispatchTime;
        assertTrue("Target didn't wait enough time before processing item."
                        + processDuration + "ms pretask delay: "
                        + PRE_TASK_DELAY_MS + "ms",
                processDuration >= PRE_TASK_DELAY_MS);
        assertEquals("Target didn't process item.", 1, mItemCount.get());

        // Second item
        mLatch = new CountDownLatch(1);
        dispatchTime = SystemClock.uptimeMillis();
        // Synchronize on the instance to make sure we schedule the item after it starts to wait for
        // task indefinitely.
        synchronized (mTarget) {
            mTarget.addItem(new TestItem(), false);
        }
        assertTrue("Target didn't call callback enough times.",
                mLatch.await(PRE_TASK_DELAY_MS + TIMEOUT_ALLOWANCE, TimeUnit.MILLISECONDS));
        assertEquals("Target didn't process all items.", 2, mItemCount.get());
        processDuration = SystemClock.uptimeMillis() - dispatchTime;
        assertTrue("Target didn't wait enough time before processing item."
                        + processDuration + "ms pre task delay: "
                        + PRE_TASK_DELAY_MS + "ms",
                processDuration >= PRE_TASK_DELAY_MS);

        // Once before processing this item, once after that.
        assertEquals(3, mProbablyDoneResults.size());
        // The last one must be called with probably done being true.
        assertTrue("The last probablyDone must be true.", mProbablyDoneResults.get(2));
    }

    @Test
    public void testFindLastItemNotReturnDifferentType() throws Exception {
        synchronized (mTarget) {
            mTarget.addItem(new TestItem(), false);
            assertNull(mTarget.findLastItem(TEST_ITEM_PREDICATE, MatchingTestItem.class));
        }
    }

    @Test
    public void testFindLastItemNotReturnMismatchItem() throws Exception {
        synchronized (mTarget) {
            mTarget.addItem(new MatchingTestItem(false), false);
            assertNull(mTarget.findLastItem(TEST_ITEM_PREDICATE, MatchingTestItem.class));
        }
    }

    @Test
    public void testFindLastItemReturnMatchedItem() throws Exception {
        synchronized (mTarget) {
            final MatchingTestItem item = new MatchingTestItem(true);
            mTarget.addItem(item, false);
            assertSame(item, mTarget.findLastItem(TEST_ITEM_PREDICATE, MatchingTestItem.class));
        }
    }

    @Test
    public void testRemoveItemsNotRemoveDifferentType() throws Exception {
        mLatch = new CountDownLatch(1);
        synchronized (mTarget) {
            mTarget.addItem(new TestItem(), false);
            mTarget.removeItems(TEST_ITEM_PREDICATE, MatchingTestItem.class);
        }
        assertTrue("Target didn't call callback enough times.",
                mLatch.await(PRE_TASK_DELAY_MS + TIMEOUT_ALLOWANCE, TimeUnit.MILLISECONDS));
        assertEquals("Target didn't process item.", 1, mItemCount.get());
    }

    @Test
    public void testRemoveItemsNotRemoveMismatchedItem() throws Exception {
        mLatch = new CountDownLatch(1);
        synchronized (mTarget) {
            mTarget.addItem(new MatchingTestItem(false), false);
            mTarget.removeItems(TEST_ITEM_PREDICATE, MatchingTestItem.class);
        }
        assertTrue("Target didn't call callback enough times.",
                mLatch.await(PRE_TASK_DELAY_MS + TIMEOUT_ALLOWANCE, TimeUnit.MILLISECONDS));
        assertEquals("Target didn't process item.", 1, mItemCount.get());
    }

    @Test
    public void testRemoveItemsRemoveMatchedItem() throws Exception {
        mLatch = new CountDownLatch(1);
        synchronized (mTarget) {
            mTarget.addItem(new TestItem(), false);
            mTarget.addItem(new MatchingTestItem(true), false);
            mTarget.removeItems(TEST_ITEM_PREDICATE, MatchingTestItem.class);
        }
        assertTrue("Target didn't call callback enough times.",
                mLatch.await(PRE_TASK_DELAY_MS + TIMEOUT_ALLOWANCE, TimeUnit.MILLISECONDS));
        assertEquals("Target didn't process item.", 1, mItemCount.get());
    }

    @Test
    public void testFlushWaitSynchronously() {
        final long dispatchTime = SystemClock.uptimeMillis();
        mTarget.addItem(new TestItem(), false);
        mTarget.addItem(new TestItem(), false);
        mTarget.flush();
        assertEquals("Flush should wait until all items are processed before return.",
                2, mItemCount.get());
        final long processTime = SystemClock.uptimeMillis() - dispatchTime;
        assertTrue("Flush should trigger immediate flush without delays. processTime: "
                + processTime, processTime < TIMEOUT_ALLOWANCE);
    }

    @Override
    public void onPreProcessItem(boolean queueEmpty) {
        mProbablyDoneResults.add(queueEmpty);

        final CountDownLatch latch = mLatch;
        if (latch != null) {
            latch.countDown();
        }

        mSetUpLatch.countDown();
    }

    private class TestItem implements PersisterQueue.WriteQueueItem {
        @Override
        public void process() {
            mItemCount.getAndIncrement();
        }
    }

    private class MatchingTestItem extends TestItem {
        private boolean mMatching;

        private MatchingTestItem(boolean matching) {
            mMatching = matching;
        }
    }
}