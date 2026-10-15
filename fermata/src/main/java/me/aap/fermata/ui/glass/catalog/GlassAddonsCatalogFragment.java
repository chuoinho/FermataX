package me.aap.fermata.ui.glass.catalog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;

/**
 * Fullscreen frosted glass Addon Catalog grid fragment without filter tabs.
 */
public class GlassAddonsCatalogFragment extends MainActivityFragment {

    public static class CatalogItem {
        public final int id;
        public final String title;
        public final String description;
        public final int iconRes;
        public final int brandColor;
        public final boolean isAddon;

        public CatalogItem(int id, String title, String description, int iconRes, int brandColor, boolean isAddon) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.iconRes = iconRes;
            this.brandColor = brandColor;
            this.isAddon = isAddon;
        }
    }

    private RecyclerView recyclerView;
    private GridLayoutManager layoutManager;
    private CatalogAdapter adapter;
    private final List<CatalogItem> items = new ArrayList<>();

    public static int computeColumnCount(int widthPx, float density) {
        float widthDp = widthPx / Math.max(0.1f, density);
        int cols = (int) (widthDp / 160f);
        return Math.max(3, Math.min(8, cols));
    }

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
        Context ctx = requireContext();
        boolean isNight = GlassThemeColors.isNight(ctx);
        GlassThemeColors colors = GlassThemeColors.get(ctx, isNight);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(isNight ? 0xFF0A0F1D : 0xFFEBEAE8);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. Header (Back pill, Title + Count, Settings gear)
        FrameLayout header = createHeader(ctx, colors, isNight);
        root.addView(header);

        // 2. Load catalog items
        loadItems(ctx);

        // 3. Grid RecyclerView
        float density = ctx.getResources().getDisplayMetrics().density;
        int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
        int initialCols = computeColumnCount(screenWidth, density);

        recyclerView = new RecyclerView(ctx);
        layoutManager = new GridLayoutManager(ctx, initialCols);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setClipToPadding(false);
        int pad = GlassDimensions.dpToPx(ctx, 16);
        recyclerView.setPadding(pad, pad, pad, pad);

        adapter = new CatalogAdapter(items, isNight, item -> {
            MainActivityDelegate activity = getActivityDelegate();
            if (activity == null) return;
            if (item.isAddon) {
                activity.showFragmentWhenReady(item.id);
            } else {
                activity.showFragment(item.id);
            }
        });
        recyclerView.setAdapter(adapter);

        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        recyclerView.setLayoutParams(rvLp);

        // Update column count when layout width changes
        recyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            if (width > 0) {
                int cols = computeColumnCount(width, density);
                if (layoutManager.getSpanCount() != cols) {
                    layoutManager.setSpanCount(cols);
                }
            }
        });

        root.addView(recyclerView);
        return root;
    }

    private FrameLayout createHeader(Context ctx, GlassThemeColors colors, boolean isNight) {
        FrameLayout header = new FrameLayout(ctx);
        int headerHeight = GlassDimensions.dpToPx(ctx, 56);
        int padH = GlassDimensions.dpToPx(ctx, 16);
        header.setPadding(padH, 0, padH, 0);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, headerHeight));

        // Back / Dashboard button (Left)
        TextView btnBack = new TextView(ctx);
        btnBack.setText("❮ Dashboard");
        btnBack.setTextSize(13f);
        btnBack.setTypeface(Typeface.DEFAULT_BOLD);
        btnBack.setTextColor(colors.getTextPrimary());
        int pillPadH = GlassDimensions.dpToPx(ctx, 16);
        int pillPadV = GlassDimensions.dpToPx(ctx, 8);
        btnBack.setPadding(pillPadH, pillPadV, pillPadH, pillPadV);
        btnBack.setBackground(GlassDrawables.createGlassPillDrawable(ctx, 18f, isNight));
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> {
            MainActivityDelegate activity = getActivityDelegate();
            if (activity != null) {
                activity.onBackPressed();
            }
        });
        header.addView(btnBack);

        // Title + Count (Center)
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("ADDONS CATALOG (" + items.size() + ")");
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(colors.getTextPrimary());
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        tvTitle.setLayoutParams(titleLp);
        header.addView(tvTitle);

        // Settings Button (Right)
        ImageView btnSettings = new ImageView(ctx);
        int gearSize = GlassDimensions.dpToPx(ctx, 36);
        int gearPad = GlassDimensions.dpToPx(ctx, 8);
        FrameLayout.LayoutParams gearLp = new FrameLayout.LayoutParams(
                gearSize, gearSize, Gravity.END | Gravity.CENTER_VERTICAL);
        btnSettings.setLayoutParams(gearLp);
        btnSettings.setPadding(gearPad, gearPad, gearPad, gearPad);
        btnSettings.setImageResource(R.drawable.settings);
        btnSettings.setBackground(GlassDrawables.createGlassPillDrawable(ctx, 18f, isNight));
        btnSettings.setOnClickListener(v -> {
            MainActivityDelegate activity = getActivityDelegate();
            if (activity != null) {
                activity.showFragment(R.id.settings_fragment);
            }
        });
        header.addView(btnSettings);

        return header;
    }

    private void loadItems(Context ctx) {
        items.clear();

        // 1. Addons
        try {
            AddonManager mgr = AddonManager.get();
            if (mgr != null) {
                List<AddonInfo> addons = mgr.getAddonInfos();
                if (addons != null) {
                    for (AddonInfo info : addons) {
                        if (info != null && info.addonName != 0) {
                            String title = ctx.getString(info.addonName);
                            int color = resolveBrandColor(title);
                            items.add(new CatalogItem(
                                    info.addonId,
                                    title,
                                    "Addon Module",
                                    info.icon,
                                    color,
                                    true
                            ));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Core library destinations
        items.add(new CatalogItem(R.id.playlists_fragment, "Playlists", "Saved media collections", R.drawable.playlist, 0xFF388E3C, false));
        items.add(new CatalogItem(R.id.folders_fragment, "Folders", "Local & external storage", R.drawable.add_folder, 0xFF1976D2, false));
        items.add(new CatalogItem(R.id.favorites_fragment, "Favorites", "Quick access starred tracks", R.drawable.favorite, 0xFFE91E63, false));
        items.add(new CatalogItem(R.id.recent_fragment, "Recent", "Recently played history", R.drawable.timer, 0xFFFFA000, false));
    }

    private static int resolveBrandColor(String title) {
        if (title == null) return GlassThemeColors.ACCENT_CYAN;
        String lower = title.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("youtube")) return 0xFFFF0000;
        if (lower.contains("tv")) return 0xFF2196F3;
        if (lower.contains("radio")) return 0xFFFF9800;
        if (lower.contains("web")) return 0xFF4CAF50;
        if (lower.contains("tiktok")) return 0xFF00F2FE;
        if (lower.contains("stremio")) return 0xFF7B1FA2;
        if (lower.contains("podcast")) return 0xFFE91E63;
        if (lower.contains("audiobook")) return 0xFFFF5722;
        return GlassThemeColors.ACCENT_CYAN;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) refreshAppearance();
    }

    public void refreshAppearance() {
        View root = getView();
        if (root == null) return;
        Context ctx = root.getContext();
        boolean isNight = GlassThemeColors.isNight(ctx);
        root.setBackgroundColor(isNight ? 0xFF0A0F1D : 0xFFEBEAE8);
        if (adapter != null) {
            adapter.setNight(isNight);
            adapter.notifyDataSetChanged();
        }
    }

    private static class CatalogAdapter extends RecyclerView.Adapter<GlassAddonCardHolder> {
        private final List<CatalogItem> items;
        private boolean isNight;
        private final java.util.function.Consumer<CatalogItem> listener;

        public CatalogAdapter(List<CatalogItem> items, boolean isNight, java.util.function.Consumer<CatalogItem> listener) {
            this.items = items;
            this.isNight = isNight;
            this.listener = listener;
        }

        public void setNight(boolean night) {
            this.isNight = night;
        }

        @NonNull
        @Override
        public GlassAddonCardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return GlassAddonCardHolder.create(parent.getContext(), isNight);
        }

        @Override
        public void onBindViewHolder(@NonNull GlassAddonCardHolder holder, int position) {
            CatalogItem item = items.get(position);
            holder.bind(item, isNight);
            holder.itemView.setOnClickListener(v -> listener.accept(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
