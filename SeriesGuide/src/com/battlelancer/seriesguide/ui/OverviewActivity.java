/*
 * Copyright 2011 Uwe Trottmann
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

package com.battlelancer.seriesguide.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.view.View;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.MenuItem;
import com.battlelancer.seriesguide.adapters.TabPagerAdapter;
import com.battlelancer.seriesguide.items.Series;
import com.battlelancer.seriesguide.util.DBUtils;
import com.battlelancer.seriesguide.util.TaskManager;
import com.battlelancer.seriesguide.util.UpdateTask;
import com.battlelancer.seriesguide.util.Utils;
import com.battlelancer.thetvdbapi.TheTVDB;
import com.google.analytics.tracking.android.EasyTracker;
import com.uwetrottmann.seriesguide.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Hosts an {@link OverviewFragment}.
 */
public class OverviewActivity extends BaseActivity {

    private int mShowId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.overview);

        mShowId = getIntent().getIntExtra(OverviewFragment.InitBundle.SHOW_TVDBID, -1);
        if (mShowId == -1) {
            finish();
            return;
        }

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        // look if we are on a multi-pane or single-pane layout...
        View pagerView = findViewById(R.id.pager);
        if (pagerView != null && pagerView.getVisibility() == View.VISIBLE) {
            // ...single pane layout with view pager

            // clear up left-over fragments from multi-pane layout
            findAndRemoveFragment(R.id.fragment_overview);
            findAndRemoveFragment(R.id.fragment_seasons);

            setupViewPager(pagerView);
        } else {
            // ...multi-pane overview and seasons fragment

            // clear up left-over fragments from single-pane layout
            boolean isSwitchingLayouts = getActiveFragments().size() != 0;
            for (Fragment fragment : getActiveFragments()) {
                getSupportFragmentManager().beginTransaction().remove(fragment).commit();
            }

            // attach new fragments if there are none or if we just switched
            // layouts
            if (savedInstanceState == null || isSwitchingLayouts) {
                setupPanes();
            }
        }

        // if (AndroidUtils.isICSOrHigher()) {
        // // register for Android Beam
        // mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        // if (mNfcAdapter != null) {
        // mNfcAdapter.setNdefPushMessageCallback(this, this);
        // }
        // }

        // try to update this show
        onUpdate();
    }

    private void setupPanes() {
        Fragment overviewFragment = OverviewFragment.newInstance(mShowId);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setCustomAnimations(R.anim.fade_in, R.anim.fade_out);
        ft.replace(R.id.fragment_overview, overviewFragment);
        ft.commit();

        Fragment seasonsFragment = SeasonsFragment.newInstance(mShowId);
        FragmentTransaction ft2 = getSupportFragmentManager().beginTransaction();
        ft2.setCustomAnimations(R.anim.fade_in, R.anim.fade_out);
        ft2.replace(R.id.fragment_seasons, seasonsFragment);
        ft2.commit();
    }

    private void setupViewPager(View pagerView) {
        final ActionBar actionBar = getSupportActionBar();

        ViewPager pager = (ViewPager) pagerView;

        // setup action bar tabs
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        TabPagerAdapter tabsAdapter = new TabPagerAdapter(getSupportFragmentManager(), this,
                actionBar, pager, getMenu());
        Bundle argsShow = new Bundle();
        argsShow.putInt(ShowInfoFragment.InitBundle.SHOW_TVDBID, mShowId);
        tabsAdapter.addTab(R.string.show, ShowInfoFragment.class, argsShow);

        tabsAdapter.addTab(R.string.description_overview, OverviewFragment.class, getIntent()
                .getExtras());

        Bundle argsSeason = new Bundle();
        argsSeason.putInt(SeasonsFragment.InitBundle.SHOW_TVDBID, mShowId);
        tabsAdapter.addTab(R.string.seasons, SeasonsFragment.class, argsSeason);

        // select overview to be shown initially
        actionBar.setSelectedNavigationItem(1);
    }

    private void findAndRemoveFragment(int fragmentId) {
        Fragment overviewFragment = getSupportFragmentManager().findFragmentById(fragmentId);
        if (overviewFragment != null) {
            getSupportFragmentManager().beginTransaction().remove(overviewFragment).commit();
        }
    }

    List<WeakReference<Fragment>> mFragments = new ArrayList<WeakReference<Fragment>>();

    @Override
    public void onAttachFragment(Fragment fragment) {
        /*
         * View pager fragments have tags set by the pager, we can use this to
         * only add refs to those then, making them available to get removed if
         * we switch to a non-pager layout.
         */
        if (fragment.getTag() != null) {
            mFragments.add(new WeakReference<Fragment>(fragment));
        }
    }

    public ArrayList<Fragment> getActiveFragments() {
        ArrayList<Fragment> ret = new ArrayList<Fragment>();
        for (WeakReference<Fragment> ref : mFragments) {
            Fragment f = ref.get();
            if (f != null) {
                if (f.isAdded()) {
                    ret.add(f);
                }
            }
        }
        return ret;
    }

    @Override
    protected void onStart() {
        super.onStart();
        EasyTracker.getInstance().activityStart(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        EasyTracker.getInstance().activityStop(this);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.shrink_enter, R.anim.shrink_exit);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent upIntent = new Intent(this, ShowsActivity.class);
                upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(upIntent);
                overridePendingTransition(R.anim.shrink_enter, R.anim.shrink_exit);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onUpdate() {
        // only update this show if no global update is running and we have a
        // connection
        if (!TaskManager.getInstance(this).isUpdateTaskRunning(false)
                && Utils.isAllowedConnection(this)) {
            final SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(getApplicationContext());

            // check if auto-update is enabled
            final boolean isAutoUpdateEnabled = prefs.getBoolean(
                    SeriesGuidePreferences.KEY_AUTOUPDATE, true);
            if (isAutoUpdateEnabled) {
                final String showId = String.valueOf(mShowId);
                boolean isTime = TheTVDB.isUpdateShow(showId, System.currentTimeMillis(), this);

                // look if we need to update
                if (isTime) {
                    final Context context = getApplicationContext();
                    Handler handler = new Handler();
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            UpdateTask updateTask = new UpdateTask(showId, context);
                            TaskManager.getInstance(context).tryUpdateTask(updateTask, false, -1);
                        }
                    };
                    handler.postDelayed(r, 1000);
                }
            }

        }
    }

    @Override
    public boolean onSearchRequested() {
        // refine search with the show's title
        final Series show = DBUtils.getShow(this, mShowId);
        final String showTitle = show.getTitle();

        Bundle args = new Bundle();
        args.putString(SearchFragment.InitBundle.SHOW_TITLE, showTitle);
        startSearch(null, false, args, false);
        return true;
    }

    // @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    // @Override
    // public NdefMessage createNdefMessage(NfcEvent event) {
    // final Series show = DBUtils.getShow(this, String.valueOf(mShowId));
    // // send id, also title and overview (both can be empty)
    // NdefMessage msg = new NdefMessage(new NdefRecord[] {
    // createMimeRecord(
    // "application/com.battlelancer.seriesguide.beam", String.valueOf(mShowId)
    // .getBytes()),
    // createMimeRecord("application/com.battlelancer.seriesguide.beam",
    // show.getTitle()
    // .getBytes()),
    // createMimeRecord("application/com.battlelancer.seriesguide.beam", show
    // .getOverview()
    // .getBytes())
    // });
    // return msg;
    // }
    //
    // /**
    // * Creates a custom MIME type encapsulated in an NDEF record
    // *
    // * @param mimeType
    // */
    // @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    // public NdefRecord createMimeRecord(String mimeType, byte[] payload) {
    // byte[] mimeBytes = mimeType.getBytes(Charset.forName("US-ASCII"));
    // NdefRecord mimeRecord = new NdefRecord(
    // NdefRecord.TNF_MIME_MEDIA, mimeBytes, new byte[0], payload);
    // return mimeRecord;
    // }

}
