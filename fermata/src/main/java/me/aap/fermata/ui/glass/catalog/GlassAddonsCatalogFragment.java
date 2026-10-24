package me.aap.fermata.ui.glass.catalog;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;
import me.aap.fermata.ui.view.TopBarController;
import me.aap.fermata.ui.view.TopBarMediatorSupport;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ImageButton;
import me.aap.utils.ui.view.ToolBarView;

/**
 * Fullscreen frosted glass Addon Catalog grid fragment with quick edit mode (reorder & enable/disable).
 */
public class GlassAddonsCatalogFragment extends MainActivityFragment implements me.aap.fermata.ui.activity.MainActivityListener {

    public static final PreferenceStore.Pref<me.aap.utils.function.Supplier<String[]>> PREF_ADDONS_ORDER =
            PreferenceStore.Pref.sa("GLASS_ADDONS_ORDER", (String[]) null);

    public static class CatalogItem {
        public final String key;
        public final int id;
        public final String title;
        public final String description;
        public final int iconRes;
        public final int brandColor;
        public final boolean isAddon;
        @Nullable
        public final AddonInfo addonInfo;
        public boolean isEnabled;

        public CatalogItem(int id, String title, String description, int iconRes, int brandColor, boolean isAddon) {
            this(String.valueOf(id), id, title, description, iconRes, brandColor, isAddon, null, true);
        }

        public CatalogItem(String key, int id, String title, String description, int iconRes,
                           int brandColor, boolean isAddon, @Nullable AddonInfo addonInfo, boolean isEnabled) {
            this.key = key;
            this.id = id;
            this.title = title;
            this.description = description;
            this.iconRes = iconRes;
            this.brandColor = brandColor;
            this.isAddon = isAddon;
            this.addonInfo = addonInfo;
            this.isEnabled = isEnabled;
        }
    }

    private RecyclerView recyclerView;
    private GridLayoutManager layoutManager;
    private CatalogAdapter adapter;
    private final List<CatalogItem> items = new ArrayList<>();
    private boolean isEditMode = false;

    public static int computeColumnCount(int widthPx, float density) {
        float widthDp = widthPx / Math.max(0.1f, density);
        if (widthDp < 320f) return 1;
        if (widthDp < 500f) return 2;
        int cols = (int) (widthDp / 160f);
        return Math.max(2, Math.min(8, cols));
    }

    @Override
    public int getFragmentId() {
        return R.id.glass_addons_catalog_fragment;
    }

    @Override
    public CharSequence getTitle() {
        return getResources().getString(R.string.addons);
    }

    @Override
    public ToolBarView.Mediator getToolBarMediator() {
        return GlassAddonsToolBarMediator.instance;
    }

    public boolean isEditMode() {
        return isEditMode;
    }

    public void toggleEditMode() {
        this.isEditMode = !this.isEditMode;
        if (!isEditMode) {
            saveOrder();
        }
        Context ctx = getContext();
        if (ctx != null) {
            loadItems(ctx);
            if (adapter != null) {
                adapter.setEditMode(isEditMode);
                adapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        MainActivityDelegate a = getActivityDelegate();
        if (a != null) a.addBroadcastListener(this);
    }

    @Override
    public void onDetach() {
        MainActivityDelegate a = getActivityDelegate();
        if (a != null) a.removeBroadcastListener(this);
        super.onDetach();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        boolean isNight = GlassThemeColors.isNight(ctx);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(isNight ? 0xFF0A0F1D : 0xFFEBEAE8);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. Load catalog items
        loadItems(ctx);

        // 2. Grid RecyclerView (fills available height, TopBar handles title & navigation)
        float density = ctx.getResources().getDisplayMetrics().density;
        int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
        int initialCols = computeColumnCount(screenWidth, density);

        recyclerView = new RecyclerView(ctx);
        layoutManager = new GridLayoutManager(ctx, initialCols);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setClipToPadding(false);
        int pad = GlassDimensions.dpToPx(ctx, 16);
        recyclerView.setPadding(pad, pad, pad, pad);

        adapter = new CatalogAdapter(items, isNight, isEditMode, this::onItemClicked);
        recyclerView.setAdapter(adapter);

        setupItemTouchHelper();

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

    private void setupItemTouchHelper() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return isEditMode;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getBindingAdapterPosition();
                int toPos = target.getBindingAdapterPosition();
                if (fromPos < 0 || toPos < 0 || fromPos == toPos) return false;

                Collections.swap(items, fromPos, toPos);
                adapter.notifyItemMoved(fromPos, toPos);
                saveOrder();
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
    }

    private void onItemClicked(CatalogItem item) {
        if (isEditMode) {
            if (item.addonInfo != null && item.addonInfo.enabledPref != null) {
                item.isEnabled = !item.isEnabled;
                PreferenceStore store = FermataApplication.get().getPreferenceStore();
                store.applyBooleanPref(item.addonInfo.enabledPref, item.isEnabled);
                int pos = items.indexOf(item);
                if (pos >= 0 && adapter != null) {
                    adapter.notifyItemChanged(pos);
                }
            }
            return;
        }

        MainActivityDelegate activity = getActivityDelegate();
        if (activity == null) return;
        if (item.isAddon) {
            activity.showFragmentWhenReady(item.id);
        } else {
            activity.showFragment(item.id);
        }
    }

    private void loadItems(Context ctx) {
        items.clear();
        PreferenceStore store = FermataApplication.get().getPreferenceStore();
        List<CatalogItem> allItems = new ArrayList<>();

        // 1. Addons (excluding retired Google Cast)
        try {
            AddonManager mgr = AddonManager.get();
            if (mgr != null) {
                List<AddonInfo> addons = mgr.getAddonInfos();
                if (addons != null) {
                    for (AddonInfo info : addons) {
                        if (info != null && info.addonName != 0) {
                            if (info.className.contains("cast") || "cast".equalsIgnoreCase(info.moduleName)) {
                                continue;
                            }
                            String title = ctx.getString(info.addonName);
                            int color = resolveBrandColor(title);
                            boolean enabled = (info.enabledPref != null) ? store.getBooleanPref(info.enabledPref) : true;
                            allItems.add(new CatalogItem(
                                    info.className,
                                    info.addonId,
                                    title,
                                    safeGetString(ctx, R.string.glass_addon_module_desc, "Addon Module"),
                                    info.icon,
                                    color,
                                    true,
                                    info,
                                    enabled
                            ));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Core library destinations
        allItems.add(new CatalogItem("playlists", R.id.playlists_fragment,
                safeGetString(ctx, R.string.playlists, "Playlists"),
                safeGetString(ctx, R.string.glass_addon_desc_playlists, "Saved media collections"),
                R.drawable.playlist, 0xFF388E3C, false, null, true));
        allItems.add(new CatalogItem("folders", R.id.folders_fragment,
                safeGetString(ctx, R.string.folders, "Folders"),
                safeGetString(ctx, R.string.glass_addon_desc_folders, "Local & external storage"),
                R.drawable.add_folder, 0xFF1976D2, false, null, true));
        allItems.add(new CatalogItem("favorites", R.id.favorites_fragment,
                safeGetString(ctx, R.string.favorites, "Favorites"),
                safeGetString(ctx, R.string.glass_addon_desc_favorites, "Quick access starred tracks"),
                R.drawable.favorite, 0xFFE91E63, false, null, true));
        allItems.add(new CatalogItem("recent", R.id.recent_fragment,
                safeGetString(ctx, R.string.recent, "Recent"),
                safeGetString(ctx, R.string.glass_addon_desc_recent, "Recently played history"),
                R.drawable.timer, 0xFFFFA000, false, null, true));

        // 3. Sort according to saved order
        String[] savedOrder = store.getStringArrayPref(PREF_ADDONS_ORDER);
        if (savedOrder != null && savedOrder.length > 0) {
            Map<String, Integer> orderMap = new HashMap<>();
            for (int i = 0; i < savedOrder.length; i++) {
                orderMap.put(savedOrder[i], i);
            }
            allItems.sort((a, b) -> {
                int orderA = orderMap.getOrDefault(a.key, Integer.MAX_VALUE);
                int orderB = orderMap.getOrDefault(b.key, Integer.MAX_VALUE);
                return Integer.compare(orderA, orderB);
            });
        }

        // 4. In normal mode, only show enabled items; in edit mode, show all
        for (CatalogItem ci : allItems) {
            if (isEditMode || ci.isEnabled) {
                items.add(ci);
            }
        }
    }

    private void saveOrder() {
        PreferenceStore store = FermataApplication.get().getPreferenceStore();
        String[] order = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            order[i] = items.get(i).key;
        }
        store.applyStringArrayPref(PREF_ADDONS_ORDER, order);
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

    @Override
    public void onActivityEvent(me.aap.fermata.ui.activity.MainActivityDelegate activity, long event) {
        if (event == me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED) {
            refreshAppearance();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAppearance();
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
        private boolean isEditMode;
        private final java.util.function.Consumer<CatalogItem> listener;

        public CatalogAdapter(List<CatalogItem> items, boolean isNight, boolean isEditMode,
                              java.util.function.Consumer<CatalogItem> listener) {
            this.items = items;
            this.isNight = isNight;
            this.isEditMode = isEditMode;
            this.listener = listener;
        }

        public void setNight(boolean night) {
            this.isNight = night;
        }

        public void setEditMode(boolean editMode) {
            this.isEditMode = editMode;
        }

        @NonNull
        @Override
        public GlassAddonCardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return GlassAddonCardHolder.create(parent.getContext(), isNight);
        }

        @Override
        public void onBindViewHolder(@NonNull GlassAddonCardHolder holder, int position) {
            CatalogItem item = items.get(position);
            holder.bind(item, isNight, isEditMode);
            holder.itemView.setOnClickListener(v -> listener.accept(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    public static class GlassAddonsToolBarMediator implements ToolBarView.Mediator.BackTitle {
        public static final GlassAddonsToolBarMediator instance = new GlassAddonsToolBarMediator();

        @Override
        public void enable(ToolBarView tb, ActivityFragment f) {
            TopBarMediatorSupport.installBackTitle(tb, f, this);
            GlassAddonsCatalogFragment catalog = (GlassAddonsCatalogFragment) f;
            ImageButton edit = addButton(tb, catalog.isEditMode() ? R.drawable.done : R.drawable.edit,
                    GlassAddonsToolBarMediator::onEditClick, R.id.catalog_addons_edit);
            edit.setContentDescription(tb.getContext().getString(
                    catalog.isEditMode() ? R.string.done : R.string.edit_dashboard));
            TopBarController.refresh(MainActivityDelegate.get(tb.getContext()), f);
        }

        @Override
        public void onActivityEvent(ToolBarView tb, ActivityDelegate activity, long event) {
            if (event == me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED) {
                ActivityFragment f = activity.getActiveFragment();
                if (f instanceof GlassAddonsCatalogFragment catalog) {
                    ImageButton edit = tb.findViewById(R.id.catalog_addons_edit);
                    if (edit != null) {
                        edit.setImageResource(catalog.isEditMode() ? R.drawable.done : R.drawable.edit);
                        edit.setContentDescription(tb.getContext().getString(
                                catalog.isEditMode() ? R.string.done : R.string.edit_dashboard));
                    }
                }
            }
        }

        private static void onEditClick(View v) {
            ActivityFragment fragment = ActivityDelegate.get(v.getContext()).getActiveFragment();
            if (fragment instanceof GlassAddonsCatalogFragment catalog) {
                catalog.toggleEditMode();
                ToolBarView tb = ActivityDelegate.get(v.getContext()).getToolBar();
                if (tb != null) {
                    ImageButton edit = tb.findViewById(R.id.catalog_addons_edit);
                    if (edit != null) {
                        edit.setImageResource(catalog.isEditMode() ? R.drawable.done : R.drawable.edit);
                        edit.setContentDescription(tb.getContext().getString(
                                catalog.isEditMode() ? R.string.done : R.string.edit_dashboard));
                    }
                }
            }
        }
    }

    private static String safeGetString(@Nullable Context context, int resId, String fallback) {
        if (context == null) return fallback;
        try {
            return context.getString(resId);
        } catch (Throwable e) {
            return fallback;
        }
    }
}
