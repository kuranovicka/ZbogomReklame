/* Copyright (C) 2016-2019 Software Freedom Conservancy (author: Julian Andres Klode) <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.ItemChangedListener;
import org.jak_linux.dns66.MainActivity;
import org.jak_linux.dns66.R;

import java.util.List;

public class ItemRecyclerViewAdapter extends RecyclerView.Adapter<ItemRecyclerViewAdapter.ViewHolder> {
    public final List<Configuration.Item> items;
    private final int stateChoices;
    private Context context;

    public ItemRecyclerViewAdapter(List<Configuration.Item> items, int stateChoices) {
        this.items = items;
        this.stateChoices = stateChoices;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ItemRecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        context = parent.getContext();
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_item, parent, false);

        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.item = items.get(position);
        holder.titleView.setText(items.get(position).title);
        holder.subtitleView.setText(items.get(position).location);

        holder.updateState();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public final View view;
        public final TextView titleView;
        public final TextView subtitleView;
        public final android.widget.Switch switchView;
        public Configuration.Item item;
        private boolean updatingProgrammatically = false;

        public ViewHolder(View view) {
            super(view);
            this.view = view;
            titleView = (TextView) view.findViewById(R.id.item_title);
            subtitleView = (TextView) view.findViewById(R.id.item_subtitle);
            switchView = (android.widget.Switch) view.findViewById(R.id.item_switch);

            view.setOnClickListener(this);
            switchView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (updatingProgrammatically)
                        return;
                    if (isChecked) {
                        // Turning on restores an explicit "Allow" (used for individual
                        // exceptions added via the edit screen), otherwise defaults to
                        // "Deny" (used for the main list and the optional filters).
                        item.state = (item.state == Configuration.Item.STATE_ALLOW)
                                ? Configuration.Item.STATE_ALLOW
                                : Configuration.Item.STATE_DENY;
                        updateState();
                        FileHelper.writeSettings(itemView.getContext(), MainActivity.config);
                        // Download this list right away - otherwise the switch looks "on"
                        // but nothing is actually blocked until the next manual refresh.
                        new org.jak_linux.dns66.db.RuleDatabaseUpdateTask(
                                itemView.getContext().getApplicationContext(), MainActivity.config, true,
                                java.util.Collections.singletonList(item))
                                .execute();
                        return;
                    } else {
                        item.state = Configuration.Item.STATE_IGNORE;
                        updateState();
                        FileHelper.writeSettings(itemView.getContext(), MainActivity.config);
                        // Reload from already-downloaded local files (no network needed)
                        // so this list actually stops blocking right away, instead of
                        // staying active in memory until the next refresh or restart.
                        final Context appContext = itemView.getContext().getApplicationContext();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    org.jak_linux.dns66.db.RuleDatabase.getInstance().initialize(appContext);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }).start();
                        return;
                    }
                }
            });
        }

        void updateState() {
            updatingProgrammatically = true;
            if (stateChoices == 2) {
                switchView.setChecked(item.state == Configuration.Item.STATE_ALLOW);
                switchView.setContentDescription(item.title);
            } else {
                boolean isOn = item.state != Configuration.Item.STATE_IGNORE;
                switchView.setChecked(isOn);
                switchView.setContentDescription(item.title);
            }
            updatingProgrammatically = false;
        }

        @Override
        public void onClick(View v) {
            final int position = getAdapterPosition();
            // Tapping the row (not the switch) opens the full edit screen, where an
            // explicit "Radnja" choice (Blokiraj / Dozvoli / Zanemari) is still
            // available for advanced, individual exceptions.
            MainActivity main = (MainActivity) v.getContext();
            main.editItem(stateChoices, item, new ItemChangedListener() {
                        @Override
                        public void onItemChanged(Configuration.Item changedItem) {
                            if (changedItem == null) {
                                items.remove(position);
                                notifyItemRemoved(position);
                            } else {
                                items.set(position, changedItem);
                                ItemRecyclerViewAdapter.this.notifyItemChanged(position);
                            }
                            FileHelper.writeSettings(itemView.getContext(), MainActivity.config);
                        }
                    }
            );
        }
    }
}