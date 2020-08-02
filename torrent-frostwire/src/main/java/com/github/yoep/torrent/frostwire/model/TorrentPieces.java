package com.github.yoep.torrent.frostwire.model;

import com.frostwire.jlibtorrent.Priority;
import com.github.yoep.torrent.adapter.TorrentException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.util.Assert;

import java.util.Arrays;

@Getter
@ToString
@EqualsAndHashCode
class TorrentPieces {
    private int firstPieceIndex = -1;
    private int lastPieceIndex = -1;

    /**
     * Get the torrent piece index for the given file piece index.
     * This method calculates the index of the torrent file piece in the while torrent.
     *
     * @param pieceIndex The file piece index.
     * @return Returns the torrent piece index.
     */
    public int getTorrentPieceIndex(int pieceIndex) {
        return pieceIndex + firstPieceIndex;
    }

    /**
     * Verify if the given piece index is within the current download range.
     *
     * @param pieceIndex The piece index to check.
     * @return Returns true if the piece index is within the download range, else false.
     */
    public boolean isInDownloadRange(int pieceIndex) {
        return pieceIndex >= firstPieceIndex && pieceIndex <= lastPieceIndex;
    }

    /**
     * Determine the download piece indexes based on the given torrent priorities.
     * Make sure the file priorities are set before calling this method.
     *
     * @param priorities The torrent priorities.
     */
    public void determineDownloadPieceIndexes(Priority[] priorities) {
        Assert.notNull(priorities, "priorities cannot be null");
        // verify if not all files are ignored
        // if so, it means that the file priorities have not been set correctly
        if (isEverythingIgnored(priorities)) {
            throw new TorrentException("Unable to determine piece indexes, all file priorities are IGNORED");
        }

        this.firstPieceIndex = getFirstPieceIndex(priorities);
        this.lastPieceIndex = getLastPieceIndex(priorities);
    }

    private static boolean isEverythingIgnored(Priority[] priorities) {
        return Arrays.stream(priorities).allMatch(e -> e == Priority.IGNORE);
    }

    private static int getFirstPieceIndex(Priority[] priorities) {
        for (int i = 0; i < priorities.length; i++) {
            var priority = priorities[i];

            if (priority == Priority.NORMAL)
                return i;
        }

        throw new TorrentException("First piece index could not be found");
    }

    private static int getLastPieceIndex(Priority[] priorities) {
        for (int i = priorities.length - 1; i >= 0; i--) {
            var priority = priorities[i];

            if (priority == Priority.NORMAL)
                return i;
        }

        throw new TorrentException("Last piece index could not be found");
    }
}
