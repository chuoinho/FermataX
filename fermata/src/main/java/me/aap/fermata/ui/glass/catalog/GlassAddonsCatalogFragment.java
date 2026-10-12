package me.aap.fermata.ui.glass.catalog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.ui.fragment.MainActivityFragment;

/**
 * Fullscreen frosted glass Addon Catalog grid fragment.
 */
public class GlassAddonsCatalogFragment extends MainActivityFragment {

    @Override
    public int getFragmentId() {
        return R.id.glass_addons_catalog_fragment;
    }

    @Override
    public CharSequence getTitle() {
        return getResources().getString(R.string.addons);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = new View(getContext());
        return view;
    }
}
