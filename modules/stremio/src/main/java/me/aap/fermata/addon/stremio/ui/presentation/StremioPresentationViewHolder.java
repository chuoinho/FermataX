package me.aap.fermata.addon.stremio.ui.presentation;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import me.aap.fermata.addon.stremio.presentation.StremioUiModel;

/** Common lifecycle boundary for the native Stremio presentation holders. */
public abstract class StremioPresentationViewHolder extends RecyclerView.ViewHolder {
	protected StremioPresentationViewHolder(@NonNull View itemView) {
		super(itemView);
	}

	abstract void bind(StremioUiModel model);

	void recycle() {
		itemView.setOnClickListener(null);
	}
}
