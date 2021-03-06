package de.dala.simplenews.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.ShareActionProvider;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.nhaarman.listviewanimations.ArrayAdapter;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.view.ViewPropertyAnimator;
import com.rometools.rome.feed.opml.Opml;
import com.rometools.rome.io.impl.OPML20Generator;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;


import org.jdom2.output.XMLOutputter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import de.dala.simplenews.R;
import de.dala.simplenews.common.Category;
import de.dala.simplenews.common.Feed;
import de.dala.simplenews.database.DatabaseHandler;
import de.dala.simplenews.utilities.BaseNavigation;
import de.dala.simplenews.utilities.LightAlertDialog;
import de.dala.simplenews.utilities.OpmlConverter;
import de.dala.simplenews.utilities.UIUtils;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

/**
 * Created by Daniel on 29.12.13.
 */
public class CategoryFeedsFragment extends BaseFragment implements BaseNavigation {


    private static final String CATEGORY_KEY = "category";
    private ListView feedListView;
    private FeedListAdapter adapter;
    private Category category;
    private ActionMode mActionMode;
    private ShareActionProvider shareActionProvider;
    private UpdatingTask task;

    public CategoryFeedsFragment() {
    }

    public static CategoryFeedsFragment newInstance(Category category) {
        CategoryFeedsFragment fragment = new CategoryFeedsFragment();
        Bundle b = new Bundle();
        b.putParcelable(CATEGORY_KEY, category);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.feed_selection, container, false);
        feedListView = (ListView) rootView.findViewById(R.id.listView);
        feedListView.setDivider(null);
        initAdapter();
        ((ActionBarActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        return rootView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        this.category = getArguments().getParcelable(CATEGORY_KEY);
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.feed_selection_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.new_feed:
                createFeedClicked();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initAdapter() {
        adapter = new FeedListAdapter(getActivity(), category.getFeeds());
        feedListView.setAdapter(adapter);
    }

    private void createFeedClicked() {
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.check_valid_rss_dialog, null);
        final ViewGroup inputLayout = (ViewGroup) view.findViewById(R.id.inputLayout);
        final View progress = view.findViewById(R.id.m_progress);
        final Button positive = (Button) inputLayout.findViewById(R.id.positive);
        final Button negative = (Button) inputLayout.findViewById(R.id.negative);
        final EditText input = (EditText) inputLayout.findViewById(R.id.input);

        final AlertDialog dialog = LightAlertDialog.Builder.create(getActivity()).setView(view).setTitle(R.string.new_feed_url_header).create();

        View.OnClickListener dialogClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.positive:
                        task = new UpdatingTask(view, inputLayout, progress, dialog, null);
                        task.execute(input.getText().toString());
                        break;
                    case R.id.negative:
                        dialog.dismiss();
                        break;
                }
            }
        };

        positive.setOnClickListener(dialogClickListener);
        negative.setOnClickListener(dialogClickListener);

        dialog.show();
    }

    private void crossfade(final View firstView, final View secondView) {
        int mShortAnimationDuration = getActivity().getResources().getInteger(
                android.R.integer.config_shortAnimTime);
        // Set the content view to 0% opacity but visible, so that it is visible
        // (but fully transparent) during the animation.
        ViewPropertyAnimator.animate(firstView).alpha(0f);
        firstView.setVisibility(View.VISIBLE);

        // Animate the content view to 100% opacity, and clear any animation
        // listener set on the view.
        ViewPropertyAnimator.animate(firstView).alpha(1f).setDuration(mShortAnimationDuration).setListener(null);

        // Animate the loading view to 0% opacity. After the animation ends,
        // set its visibility to GONE as an optimization step (it won't
        // participate in layout passes, etc.)
        ViewPropertyAnimator.animate(secondView).alpha(0f).setDuration(mShortAnimationDuration).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                secondView.setVisibility(View.GONE);
            }
        });
    }

    private void editClicked(final Feed feed) {
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.check_valid_rss_dialog, null);
        final ViewGroup inputLayout = (ViewGroup) view.findViewById(R.id.inputLayout);
        final View progress = view.findViewById(R.id.m_progress);
        final Button positive = (Button) inputLayout.findViewById(R.id.positive);
        final Button negative = (Button) inputLayout.findViewById(R.id.negative);
        final EditText input = (EditText) inputLayout.findViewById(R.id.input);

        final AlertDialog dialog = LightAlertDialog.Builder.create(getActivity()).setView(view).setTitle(R.string.rename_feed).create();

        input.setText(feed.getXmlUrl());
        View.OnClickListener dialogClickListener = new View.OnClickListener() {

            @Override
            public void onClick(View clickedView) {
                switch (clickedView.getId()) {
                    case R.id.positive:
                        new UpdatingTask(view, inputLayout, progress, dialog, feed.getId()).execute(input.getText().toString());
                        break;
                    case R.id.negative:
                        dialog.dismiss();
                        break;
                }
            }
        };

        positive.setOnClickListener(dialogClickListener);
        negative.setOnClickListener(dialogClickListener);
        dialog.show();
    }

    private class UpdatingTask extends AsyncTask<String, String, Feed> {
        private View view;
        private ViewGroup inputLayout;
        private View progress;
        private AlertDialog dialog;
        private Long isEditingId;

        public UpdatingTask(View view, ViewGroup inputLayout, View progress, AlertDialog dialog, Long isEditingId){
            this.view = view;
            this.inputLayout = inputLayout;
            this.progress = progress;
            this.dialog = dialog;
            this.isEditingId = isEditingId;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            crossfade(progress, inputLayout);
        }

        @Override
        protected void onPostExecute(Feed feed) {
            super.onPostExecute(feed);
            if (feed != null) {
                if (isEditingId == null) {
                    category.getFeeds().add(feed);
                }
                adapter.notifyDataSetChanged();
                dialog.dismiss();
            }else{
                invalidFeedUrl(true);
            }
        }

        @Override
        protected Feed doInBackground(String... params) {
            String feedUrl = params[0];
            if (!feedUrl.startsWith("http://")) {
                feedUrl = "http://" + feedUrl;
            }

            if (UIUtils.isValideUrl(feedUrl)) {
                try {
                    SyndFeedInput input = new SyndFeedInput();
                    SyndFeed syndFeed = input.build(new XmlReader(new URL(feedUrl)));
                    if (syndFeed.getEntries() == null || syndFeed.getEntries().isEmpty()) {
                        return null;
                    } else {
                        Feed feed = new Feed();
                        feed.setId(isEditingId);
                        feed.setCategoryId(category.getId());
                        feed.setTitle(syndFeed.getTitle());
                        feed.setDescription(syndFeed.getDescription());
                        feed.setXmlUrl(feedUrl);
                        feed.setType(syndFeed.getFeedType());
                        //TODO maybe store more information - later
                        long id = DatabaseHandler.getInstance().addFeed(category.getId(), feed, true);
                        feed.setId(id);

                        return feed;
                    }
                } catch (FeedException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (IllegalArgumentException e){
                    e.printStackTrace();
                }
            }
            return null;
        }


        private void invalidFeedUrl(final boolean hideProgressBar) {
            Context context = getActivity();
            if (context != null) {
                Animation shake = AnimationUtils.loadAnimation(context,
                        R.anim.shake);
                if (shake != null) {
                    shake.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            if (hideProgressBar) {
                                crossfade(inputLayout, progress);
                                Crouton.makeText(getActivity(), getActivity().getString(R.string.not_valid_format), Style.ALERT, inputLayout).show();
                            }
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {
                        }
                    });
                    view.startAnimation(shake);
                }
            }
        }
    }
    private void onListItemCheck(int position) {
        adapter.toggleSelection(position);
        boolean hasCheckedItems = adapter.getSelectedCount() > 0;

        if (hasCheckedItems && mActionMode == null) {
            // there are some selected items, start the actionMode
            mActionMode = ((ActionBarActivity) getActivity()).startSupportActionMode(new ActionModeCallBack());
        } else if (!hasCheckedItems && mActionMode != null) {
            // there no selected items, finish the actionMode
            mActionMode.finish();
        }

        if (mActionMode != null) {
            mActionMode.setTitle(String.valueOf(adapter.getSelectedCount()));
        }

        if (shareActionProvider != null) {
            shareActionProvider.setShareIntent(createShareIntent());
        }
    }

    private void removeSelectedFeeds(List<Feed> selectedFeeds) {
        for (Feed feed : selectedFeeds) {
            adapter.remove(feed);
            adapter.notifyDataSetChanged();

            DatabaseHandler.getInstance().removeFeeds(null, feed.getId(), false);
            category.getFeeds().remove(feed);
        }
    }

    @Override
    public String getTitle() {
        Context context = getActivity();
        if (context != null) {
            return String.format(context.getString(R.string.category_feeds_fragment_title), category.getName());
        }
        return "SimpleNews"; //default
    }

    @Override
    public int getNavigationDrawerId() {
        return NavigationDrawerFragment.CATEGORIES;
    }

    private Intent createShareIntent() {
        // retrieve selected items and print them out
        SparseBooleanArray selected = adapter.getSelectedIds();

        ArrayList<Feed> feeds = new ArrayList<Feed>();
        for (int i = 0; i < selected.size(); i++) {
            if (selected.valueAt(i)) {
                Feed feed = adapter.getItem(selected.keyAt(i));
                feeds.add(feed);
            }
        }

        Opml opml = OpmlConverter.convertFeedsToOpml("SimpleNews - OPML", feeds);
        String finalMessage = null;
        try {
            finalMessage = new XMLOutputter().outputString(new OPML20Generator().generate(opml));
        } catch (FeedException e) {
            e.printStackTrace();
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/xml");
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                finalMessage);
        return shareIntent;
    }

    private class FeedListAdapter extends ArrayAdapter<Feed> {

        private Context context;
        private SparseBooleanArray mSelectedItemIds;

        public FeedListAdapter(Context context, List<Feed> feeds) {
            super(feeds);
            this.context = context;
            mSelectedItemIds = new SparseBooleanArray();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).getId();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            Feed feed = getItem(position);

            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(context);
                convertView = inflater.inflate(R.layout.feed_modify_item, parent, false);
                ViewHolder viewHolder = new ViewHolder();
                viewHolder.name = (TextView) convertView.findViewById(R.id.name);
                viewHolder.link = (TextView) convertView.findViewById(R.id.link);
                viewHolder.show = (CheckBox) convertView.findViewById(R.id.show);
                viewHolder.edit = (ImageView) convertView.findViewById(R.id.edit);
                convertView.setTag(viewHolder);
            }
            ViewHolder holder = (ViewHolder) convertView.getTag();
            holder.name.setText(feed.getTitle() == null ? context.getString(R.string.feed_title_not_found) : feed.getTitle());
            holder.link.setText(feed.getXmlUrl());
            holder.show.setOnClickListener(new FeedItemClickListener(feed));
            holder.show.setChecked(feed.isVisible());
            holder.edit.setOnClickListener(new FeedItemClickListener(feed));

            convertView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    onListItemCheck(position);
                    return false;
                }
            });
            convertView.setBackgroundResource(mSelectedItemIds.get(position) ? R.drawable.card_background_blue : R.drawable.card_background_white);
            int pad = getResources().getDimensionPixelSize(R.dimen.card_layout_padding);
            convertView.setPadding(pad, pad, pad, pad);
            return convertView;
        }

        public void toggleSelection(int position) {
            selectView(position, !mSelectedItemIds.get(position));
        }

        public void selectView(int position, boolean value) {
            if (value) {
                mSelectedItemIds.put(position, value);
            } else {
                mSelectedItemIds.delete(position);
            }
            notifyDataSetChanged();
        }

        public int getSelectedCount() {
            return mSelectedItemIds.size();// mSelectedCount;
        }

        public SparseBooleanArray getSelectedIds() {
            return mSelectedItemIds;
        }

        public void removeSelection() {
            mSelectedItemIds = new SparseBooleanArray();
            notifyDataSetChanged();
        }

        class ViewHolder {
            public TextView name;
            public TextView link;
            public CheckBox show;
            public ImageView edit;
        }
    }

    class FeedItemClickListener implements View.OnClickListener {
        private Feed feed;

        public FeedItemClickListener(Feed feed) {
            this.feed = feed;
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.edit:
                    editClicked(feed);
                    break;
                case R.id.show:
                    boolean visible = !feed.isVisible();
                    feed.setVisible(visible);
                    DatabaseHandler.getInstance().updateFeed(feed);
                    break;
            }
        }
    }

    private class ActionModeCallBack implements ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // inflate contextual menu
            mode.getMenuInflater().inflate(R.menu.contextual_feed_selection_menu, menu);
            MenuItem item = menu.findItem(R.id.menu_item_share);
            shareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);
            shareActionProvider.setShareHistoryFileName(
                    ShareActionProvider.DEFAULT_SHARE_HISTORY_FILE_NAME);
            shareActionProvider.setShareIntent(createShareIntent());
            shareActionProvider.setOnShareTargetSelectedListener(new ShareActionProvider.OnShareTargetSelectedListener() {
                @Override
                public boolean onShareTargetSelected(ShareActionProvider shareActionProvider, Intent intent) {
                    return false;
                }
            });
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            // retrieve selected items and print them out
            SparseBooleanArray selected = adapter.getSelectedIds();
            List<Feed> selectedEntries = new ArrayList<Feed>();
            for (int i = 0; i < selected.size(); i++) {
                if (selected.valueAt(i)) {
                    Feed selectedItem = adapter.getItem(selected.keyAt(i));
                    selectedEntries.add(selectedItem);
                }
            }

            boolean shouldFinish = true;

            // close action mode
            switch (item.getItemId()) {
                case R.id.menu_item_remove:
                    removeSelectedFeeds(selectedEntries);
                    break;
            }
            if (shouldFinish) {
                mode.finish();
                adapter.removeSelection();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
            adapter.removeSelection();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mActionMode != null) {
            mActionMode.invalidate();
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mActionMode != null) {
            mActionMode.finish();
        }
        if (task != null){
            task.cancel(true);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mActionMode != null) {
            mActionMode.finish();
        }
        if (task != null){
            task.cancel(true);
        }
    }
}
