package com.example.localfilewebview.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.example.localfilewebview.R;
import com.example.localfilewebview.ui.home.HomeFragment;

public class DashboardFragment extends HomeFragment {

    protected void load() {
        String url = "file:///android_asset/hello-pwa/index.html" +
                "?partner=8c31b93c-24bd-4dfa-aa16-db96c0296b3a" +
                "&session=21f1ad49644b424266cb28f2953928be" +
                "&mode=real&turbo=true";
        urlView.setText(url);
        super.load();
    }
}