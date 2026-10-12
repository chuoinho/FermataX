package me.aap.fermata.ui.glass.quickbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * Model representing a shortcut button in the Glass Quick Bar.
 */
public class GlassQuickShortcut {

    private final String id;
    private final String title;
    private final int iconResId;
    private final String type;

    public GlassQuickShortcut(@NonNull String id, @NonNull String title, int iconResId, @NonNull String type) {
        this.id = id;
        this.title = title;
        this.iconResId = iconResId;
        this.type = type;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    public int getIconResId() {
        return iconResId;
    }

    @NonNull
    public String getType() {
        return type;
    }

    @NonNull
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title);
            json.put("iconResId", iconResId);
            json.put("type", type);
        } catch (JSONException ignored) {}
        return json;
    }

    @Nullable
    public static GlassQuickShortcut fromJson(@Nullable JSONObject json) {
        if (json == null) return null;
        String id = json.optString("id", "");
        String title = json.optString("title", "");
        int iconResId = json.optInt("iconResId", 0);
        String type = json.optString("type", "custom");
        if (id.isEmpty()) return null;
        return new GlassQuickShortcut(id, title, iconResId, type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GlassQuickShortcut that = (GlassQuickShortcut) o;
        return iconResId == that.iconResId &&
                Objects.equals(id, that.id) &&
                Objects.equals(title, that.title) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, iconResId, type);
    }

    @NonNull
    @Override
    public String toString() {
        return "GlassQuickShortcut{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", iconResId=" + iconResId +
                ", type='" + type + '\'' +
                '}';
    }
}
