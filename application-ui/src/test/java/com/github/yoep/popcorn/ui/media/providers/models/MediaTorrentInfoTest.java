package com.github.yoep.popcorn.ui.media.providers.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaTorrentInfoTest {
    @Test
    void testSetPeers_whenInvoked_shouldSetThePeerValue() {
        var peers = 10;
        var mediaTorrent = new MediaTorrentInfo();

        mediaTorrent.setPeers(peers);

        assertEquals(peers, mediaTorrent.getPeer());
    }

    @Test
    void testSetSeeds_whenInvoked_shouldSetTheSeedValue() {
        var seeds = 23;
        var mediaTorrent = new MediaTorrentInfo();

        mediaTorrent.setSeeds(seeds);

        assertEquals(seeds, mediaTorrent.getSeed());
    }
}
