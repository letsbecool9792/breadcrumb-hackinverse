package com.lbc.breadcrumb.ui.home

/**
 * Lifecycle status of a search as it moves from local FTS word results
 * to server ranked retrieval, or degrades to offline/unavailable.
 */
enum class SearchStatus {
    RANKING,
    RANKED,
    OFFLINE,
    UNAVAILABLE,
}
