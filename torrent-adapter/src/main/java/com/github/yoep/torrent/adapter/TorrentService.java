package com.github.yoep.torrent.adapter;

import com.github.yoep.torrent.adapter.model.Torrent;
import com.github.yoep.torrent.adapter.model.TorrentFileInfo;
import com.github.yoep.torrent.adapter.model.TorrentHealth;
import com.github.yoep.torrent.adapter.model.TorrentInfo;
import com.github.yoep.torrent.adapter.state.SessionState;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.springframework.scheduling.annotation.Async;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface TorrentService {
    /**
     * Get the state of the torrent session.
     *
     * @return Returns the current session state.
     */
    SessionState getSessionState();

    /**
     * Get the session state property of this torrent service.
     *
     * @return Returns the session state property of this service.
     */
    ReadOnlyObjectProperty<SessionState> sessionStateProperty();

    /**
     * Get the error that occurred in the torrent session.
     * The {@link TorrentException} might only be present if the {@link #getSessionState()} is {@link SessionState#ERROR}.
     *
     * @return Returns the torrent session error.
     */
    Optional<TorrentException> getSessionError();

    /**
     * Get the torrent metadata, either by downloading the .torrent or fetching the magnet.
     *
     * @param torrentUrl The URL to the .torrent file or a magnet link.
     * @return Returns the torrent information.
     */
    @Async
    CompletableFuture<TorrentInfo> getTorrentInfo(String torrentUrl);

    /**
     * Create a new torrent for the given torrent file.
     *
     * @param torrentFile      The torrent file that needs to be downloaded.
     * @param torrentDirectory The directory where the torrent will be stored.
     * @return Returns the torrent for the given torrent file.
     */
    @Async
    CompletableFuture<Torrent> create(TorrentFileInfo torrentFile, File torrentDirectory);

    /**
     * Create a new torrent for the given torrent file.
     *
     * @param torrentFile       The torrent file that needs to be downloaded.
     * @param torrentDirectory  The directory where the torrent will be stored.
     * @param autoStartDownload Set if the download of the torrent should be started automatically.
     * @return Returns the torrent for the given torrent file.
     */
    @Async
    CompletableFuture<Torrent> create(TorrentFileInfo torrentFile, File torrentDirectory, boolean autoStartDownload);

    /**
     * Remove the given torrent from the session.
     *
     * @param torrent The torrent to remove.
     */
    void remove(Torrent torrent);

    /**
     * Calculate the health of a torrent base on it's seeds and peers.
     *
     * @param seeds The number of seeds.
     * @param peers The number of peers.
     * @return Returns the health of the torrent.
     */
    TorrentHealth calculateHealth(int seeds, int peers);
}
