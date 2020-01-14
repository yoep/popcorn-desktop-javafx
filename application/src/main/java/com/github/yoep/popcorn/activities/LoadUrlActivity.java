package com.github.yoep.popcorn.activities;

public interface LoadUrlActivity extends LoadActivity {
    /**
     * Get the url that needs to be loaded.
     *
     * @return Returns the url that needs to be loaded.
     */
    String getUrl();
}