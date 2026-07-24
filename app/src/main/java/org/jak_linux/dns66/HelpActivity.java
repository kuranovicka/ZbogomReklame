/* Copyright (C) 2026 ZbogomReklame
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_help);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.action_help);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.action_copy_text);
        menu.add(0, 2, 1, R.string.action_share);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == 1) {
            copyHelpText();
            return true;
        } else if (item.getItemId() == 2) {
            shareHelpTextAsFile();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void copyHelpText() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("ZbogomReklame - Pomoć", getString(R.string.help_text));
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareHelpTextAsFile() {
        try {
            File sharedDir = new File(getCacheDir(), "shared");
            if (!sharedDir.exists())
                sharedDir.mkdirs();
            File file = new File(sharedDir, "ZbogomReklame-pomoc.txt");
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write(getString(R.string.help_text));
            writer.close();

            Uri uri = FileProvider.getUriForFile(this, "org.jak_linux.dns66.fileprovider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)));
        } catch (IOException e) {
            Toast.makeText(this, R.string.generic_error, Toast.LENGTH_SHORT).show();
        }
    }
}
