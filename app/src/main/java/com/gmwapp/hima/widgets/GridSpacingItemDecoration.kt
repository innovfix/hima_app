package com.gmwapp.hima.widgets

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration

/**
 * Even spacing for [GridLayoutManager] so columns align on all screen widths.
 */
class GridSpacingItemDecoration(
    private val spacingPx: Int,
    private val includeEdge: Boolean
) : ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val spans = (parent.layoutManager as? GridLayoutManager)?.spanCount ?: return
        val column = position % spans

        if (includeEdge) {
            outRect.left = spacingPx - column * spacingPx / spans
            outRect.right = (column + 1) * spacingPx / spans
            if (position < spans) {
                outRect.top = spacingPx
            }
            outRect.bottom = spacingPx
        } else {
            outRect.left = column * spacingPx / spans
            outRect.right = spacingPx - (column + 1) * spacingPx / spans
            if (position >= spans) {
                outRect.top = spacingPx
            }
            outRect.bottom = spacingPx
        }
    }
}
