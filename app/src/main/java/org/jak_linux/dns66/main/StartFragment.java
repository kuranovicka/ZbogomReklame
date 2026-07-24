/* Copyright (C) 2016-2019 Software Freedom Conservancy (author: Julian Andres Klode) <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.MainActivity;
import org.jak_linux.dns66.R;
import org.jak_linux.dns66.vpn.AdVpnService;
import org.jak_linux.dns66.vpn.Command;

import java.io.IOException;
import java.io.InputStreamReader;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

public class StartFragment extends Fragment {
    public static final int REQUEST_START_VPN = 1;
    private static final String TAG = "StartFragment";

    private View statsRootView;
    private final android.os.Handler statsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable statsUpdater = new Runnable() {
        @Override
        public void run() {
            updateStats();
            statsHandler.postDelayed(this, 3000);
        }
    };

    public StartFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_start, container, false);
        statsRootView = rootView;
        Switch switchOnBoot = (Switch) rootView.findViewById(R.id.switch_onboot);

        Button startButton = (Button) rootView.findViewById(R.id.start_button);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startStopService();
            }
        });

        Button refreshButton = (Button) rootView.findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new org.jak_linux.dns66.db.RuleDatabaseUpdateTask(
                        getContext().getApplicationContext(), MainActivity.config, true)
                        .execute();
            }
        });

        updateStatus(rootView, AdVpnService.vpnStatus);

        switchOnBoot.setChecked(MainActivity.config.autoStart);
        switchOnBoot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.config.autoStart = isChecked;
                FileHelper.writeSettings(getContext(), MainActivity.config);
            }
        });

        Switch watchDog = (Switch) rootView.findViewById(R.id.watchdog);
        watchDog.setChecked(MainActivity.config.watchDog);
        watchDog.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.config.watchDog = isChecked;
                FileHelper.writeSettings(getContext(), MainActivity.config);

                if (isChecked) {
                    new AlertDialog.Builder(getActivity())
                            .setIcon(R.drawable.ic_warning)
                            .setTitle(R.string.unstable_feature)
                            .setMessage(R.string.unstable_watchdog_message)
                            .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    watchDog.setChecked(false);
                                    MainActivity.config.watchDog = false;
                                    FileHelper.writeSettings(getContext(), MainActivity.config);
                                }
                            })
                            .setPositiveButton(R.string.button_continue, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    /* Do nothing */
                                }
                            })
                            .show();
                    return;
                } else {
                }
            }
        });

        Switch ipV6Support = (Switch) rootView.findViewById(R.id.ipv6_support);
        ipV6Support.setChecked(MainActivity.config.ipV6Support);
        ipV6Support.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.config.ipV6Support = isChecked;
                FileHelper.writeSettings(getContext(), MainActivity.config);
            }
        });

        ExtraBar.setup(rootView.findViewById(R.id.extra_bar), "start");

        return rootView;
    }

    private boolean startStopService() {
        if (AdVpnService.vpnStatus != AdVpnService.VPN_STATUS_STOPPED) {
            Log.i(TAG, "Attempting to disconnect");

            Intent intent = new Intent(getActivity(), AdVpnService.class);
            intent.putExtra("COMMAND", Command.STOP.ordinal());
            getActivity().startService(intent);
        } else {
            checkHostsFilesAndStartService();
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        statsHandler.post(statsUpdater);
    }

    @Override
    public void onPause() {
        super.onPause();
        statsHandler.removeCallbacks(statsUpdater);
    }

    private void updateStats() {
        if (statsRootView == null || getContext() == null)
            return;
        TextView statsText = (TextView) statsRootView.findViewById(R.id.stats_textview);
        if (statsText == null)
            return;
        org.jak_linux.dns66.vpn.Stats.save(getContext());
        long queries = org.jak_linux.dns66.vpn.DnsPacketProxy.queryCount.get();
        long blocked = org.jak_linux.dns66.vpn.DnsPacketProxy.blockedCount.get();
        if (AdVpnService.vpnStatus == AdVpnService.VPN_STATUS_RUNNING) {
            statsText.setText(getString(R.string.live_stats_status, queries, blocked));
        } else {
            statsText.setText("");
        }

        SimpleBarChartView chart = (SimpleBarChartView) statsRootView.findViewById(R.id.stats_chart);
        if (chart != null) {
            java.util.List<Long> history = new java.util.ArrayList<>(
                    org.jak_linux.dns66.vpn.Stats.getHistory(getContext()));
            history.add(blocked); // today, still live
            int barColor = androidx.core.content.ContextCompat.getColor(getContext(), R.color.colorAccent);
            int textColor = androidx.core.content.ContextCompat.getColor(getContext(), android.R.color.darker_gray);
            chart.setValues(history, barColor, textColor);
        }
    }

    public static void updateStatus(View rootView, int status) {
        TextView stateText = (TextView) rootView.findViewById(R.id.state_textview);
        Button startButton = (Button) rootView.findViewById(R.id.start_button);

        if (stateText == null || startButton == null)
            return;

        stateText.setText(rootView.getContext().getString(AdVpnService.vpnStatusToTextId(status)));
        switch(status) {
            case AdVpnService.VPN_STATUS_RECONNECTING:
            case AdVpnService.VPN_STATUS_STARTING:
            case AdVpnService.VPN_STATUS_STOPPING:
                startButton.setText(R.string.action_stop);
                break;
            case AdVpnService.VPN_STATUS_STOPPED:
                startButton.setText(R.string.action_start);
                break;
            case AdVpnService.VPN_STATUS_RUNNING:
                startButton.setText(R.string.action_stop);
                break;
            case AdVpnService.VPN_STATUS_RECONNECTING_NETWORK_ERROR:
                startButton.setText(R.string.action_stop);
                break;
        }
    }

    private void checkHostsFilesAndStartService() {
        if (!areHostsFilesExistant()) {
            new AlertDialog.Builder(getActivity())
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.missing_hosts_files_title)
                    .setMessage(R.string.missing_hosts_files_message)
                    .setNegativeButton(R.string.button_no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            /* Do nothing */
                        }
                    })
                    .setPositiveButton(R.string.button_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startService();
                        }
                    })
                    .show();
            return;
        }
        startService();
    }

    private void startService() {
        Log.i(TAG, "Attempting to connect");
        Intent intent = VpnService.prepare(getContext());
        if (intent != null) {
            startActivityForResult(intent, REQUEST_START_VPN);
        } else {
            onActivityResult(REQUEST_START_VPN, RESULT_OK, null);
        }
    }

    /**
     * Check if all configured hosts files exist.
     *
     * @return true if all host files exist or no host files were configured.
     */
    private boolean areHostsFilesExistant() {
        if (!MainActivity.config.hosts.enabled)
            return true;

        for (Configuration.Item item : MainActivity.config.hosts.items) {
            if (item.state != Configuration.Item.STATE_IGNORE) {
                try {
                    InputStreamReader reader = FileHelper.openItemFile(getContext(), item);
                    if (reader == null)
                        continue;

                    reader.close();
                } catch (IOException e) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult: Received result=" + resultCode + " for request=" + requestCode);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_START_VPN && resultCode == RESULT_CANCELED) {
            Toast.makeText(getContext(), R.string.could_not_configure_vpn_service, Toast.LENGTH_LONG).show();
        }
        if (requestCode == REQUEST_START_VPN && resultCode == RESULT_OK) {
            Log.d("MainActivity", "onActivityResult: Starting service");
            Intent intent = new Intent(getContext(), AdVpnService.class);
            intent.putExtra("COMMAND", Command.START.ordinal());
            intent.putExtra("NOTIFICATION_INTENT",
                    PendingIntent.getActivity(getContext(), 0,
                            new Intent(getContext(), MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(intent);
            } else {
                getContext().startService(intent);
            }

        }
    }
}
