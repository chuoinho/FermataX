package me.aap.fermata.addon.tv.stalker;

import java.util.List;

record StalkerPage<T>(List<T> items, int totalItems, int maxPageItems) {
	boolean hasNext(int page) {
		return (maxPageItems > 0) && ((long) page * maxPageItems < totalItems);
	}
}
