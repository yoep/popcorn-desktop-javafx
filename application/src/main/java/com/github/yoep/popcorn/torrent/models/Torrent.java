package com.github.yoep.popcorn.torrent.models;

import com.frostwire.jlibtorrent.*;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.BlockFinishedAlert;
import com.frostwire.jlibtorrent.alerts.PieceFinishedAlert;
import com.github.yoep.popcorn.torrent.listeners.TorrentListener;
import com.github.yoep.popcorn.torrent.stream.TorrentInputStream;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

@Slf4j
public class Torrent implements AlertListener {
    private final static Integer MAX_PREPARE_COUNT = 20;
    private final static Integer MIN_PREPARE_COUNT = 4;
    private final static Integer DEFAULT_PREPARE_COUNT = 5;
    private final static Integer SEQUENTIAL_CONCURRENT_PIECES_COUNT = 5;

    public enum State {RETRIEVING_META, STARTING, STREAMING, PAUSED}

    private Integer piecesToPrepare;
    private Integer lastPieceIndex;
    private Integer firstPieceIndex;
    private Integer selectedFileIndex = -1;
    private Integer interestedPieceIndex = 0;

    private Double prepareProgress = 0d;
    private Double progressStep = 0d;
    private List<Integer> preparePieces;
    private Boolean[] hasPieces;

    private final List<WeakReference<TorrentInputStream>> torrentStreamReferences = new ArrayList<>();

    private State state = State.RETRIEVING_META;

    private final TorrentHandle torrentHandle;
    private final TorrentListener listener;
    private final Long prepareSize;

    public Torrent(TorrentHandle torrentHandle, TorrentListener listener, Long prepareSize) {
        log.debug("Creating new torrent for \"{}\"", torrentHandle.name());
        this.torrentHandle = torrentHandle;
        this.listener = listener;
        this.prepareSize = prepareSize;

        setLargestFile();
        startDownload();
    }

    public Torrent(TorrentHandle torrentHandle, TorrentListener listener, Long prepareSize, int fileIndex) {
        log.debug("Creating new torrent for \"{}\"", torrentHandle.name());
        this.torrentHandle = torrentHandle;
        this.listener = listener;
        this.prepareSize = prepareSize;

        setSelectedFileIndex(fileIndex);
        startDownload();
    }

    /**
     * Reset piece priorities of selected file to normal
     */
    private void resetPriorities() {
        Priority[] priorities = torrentHandle.piecePriorities();
        for (int i = 0; i < priorities.length; i++) {
            if (i >= firstPieceIndex && i <= lastPieceIndex) {
                torrentHandle.piecePriority(i, Priority.NORMAL);
            } else {
                torrentHandle.piecePriority(i, Priority.IGNORE);
            }
        }
    }

    /**
     * Get LibTorrent torrent handle of this torrent
     *
     * @return {@link TorrentHandle}
     */
    public TorrentHandle getTorrentHandle() {
        return torrentHandle;
    }

    public File getVideoFile() {
        return new File(torrentHandle.savePath() + "/" + torrentHandle.torrentFile().files().filePath(selectedFileIndex));
    }

    /**
     * Get an InputStream for the video file.
     * Read is be blocked until the requested piece(s) is downloaded.
     *
     * @return {@link InputStream}
     */
    public InputStream getVideoStream() throws FileNotFoundException {
        File file = getVideoFile();
        TorrentInputStream inputStream = new TorrentInputStream(this, new FileInputStream(file));
        torrentStreamReferences.add(new WeakReference<>(inputStream));

        return inputStream;
    }

    /**
     * Get the location of the file that is being downloaded
     *
     * @return {@link File} The file location
     */
    public File getSaveLocation() {
        return new File(torrentHandle.savePath() + "/" + torrentHandle.name());
    }

    /**
     * Resume the torrent download
     */
    public void resume() {
        torrentHandle.resume();
    }

    /**
     * Pause the torrent download
     */
    public void pause() {
        torrentHandle.pause();
        state = State.PAUSED;
    }

    /**
     * Set the selected file index to the largest file in the torrent
     */
    public void setLargestFile() {
        setSelectedFileIndex(-1);
    }

    /**
     * Set the index of the file that should be downloaded
     * If the given index is -1, then the largest file is chosen
     *
     * @param selectedFileIndex {@link Integer} Index of the file
     */
    public void setSelectedFileIndex(Integer selectedFileIndex) {
        var torrentInfo = torrentHandle.torrentFile();
        var fileStorage = torrentInfo.files();

        if (selectedFileIndex == -1) {
            long highestFileSize = 0;
            int selectedFile = -1;
            for (int i = 0; i < fileStorage.numFiles(); i++) {
                long fileSize = fileStorage.fileSize(i);
                if (highestFileSize < fileSize) {
                    highestFileSize = fileSize;
                    torrentHandle.filePriority(selectedFile, Priority.IGNORE);
                    selectedFile = i;
                    torrentHandle.filePriority(i, Priority.NORMAL);
                } else {
                    torrentHandle.filePriority(i, Priority.IGNORE);
                }
            }
            selectedFileIndex = selectedFile;
        } else {
            for (int i = 0; i < fileStorage.numFiles(); i++) {
                if (i == selectedFileIndex) {
                    torrentHandle.filePriority(i, Priority.NORMAL);
                } else {
                    torrentHandle.filePriority(i, Priority.IGNORE);
                }
            }
        }
        this.selectedFileIndex = selectedFileIndex;

        Priority[] piecePriorities = torrentHandle.piecePriorities();
        int firstPieceIndexLocal = -1;
        int lastPieceIndexLocal = -1;
        for (int i = 0; i < piecePriorities.length; i++) {
            if (piecePriorities[i] != Priority.IGNORE) {
                if (firstPieceIndexLocal == -1) {
                    firstPieceIndexLocal = i;
                }
                piecePriorities[i] = Priority.IGNORE;
            } else {
                if (firstPieceIndexLocal != -1 && lastPieceIndexLocal == -1) {
                    lastPieceIndexLocal = i - 1;
                }
            }
        }

        if (firstPieceIndexLocal == -1) {
            firstPieceIndexLocal = 0;
        }
        if (lastPieceIndexLocal == -1) {
            lastPieceIndexLocal = piecePriorities.length - 1;
        }
        int pieceCount = lastPieceIndexLocal - firstPieceIndexLocal + 1;
        int pieceLength = torrentHandle.torrentFile().pieceLength();
        int activePieceCount;
        if (pieceLength > 0) {
            activePieceCount = (int) (prepareSize / pieceLength);
            if (activePieceCount < MIN_PREPARE_COUNT) {
                activePieceCount = MIN_PREPARE_COUNT;
            } else if (activePieceCount > MAX_PREPARE_COUNT) {
                activePieceCount = MAX_PREPARE_COUNT;
            }
        } else {
            activePieceCount = DEFAULT_PREPARE_COUNT;
        }

        if (pieceCount < activePieceCount) {
            activePieceCount = pieceCount / 2;
        }

        this.firstPieceIndex = firstPieceIndexLocal;
        this.interestedPieceIndex = this.firstPieceIndex;
        this.lastPieceIndex = lastPieceIndexLocal;
        piecesToPrepare = activePieceCount;
    }

    /**
     * Get the filenames of the files in the torrent
     *
     * @return {@link String[]}
     */
    public String[] getFileNames() {
        FileStorage fileStorage = torrentHandle.torrentFile().files();
        String[] fileNames = new String[fileStorage.numFiles()];
        for (int i = 0; i < fileStorage.numFiles(); i++) {
            fileNames[i] = fileStorage.fileName(i);
        }
        return fileNames;
    }

    /**
     * Prepare torrent for playback. Prioritize the first {@code piecesToPrepare} pieces and the last {@code piecesToPrepare} pieces
     * from {@code firstPieceIndex} and {@code lastPieceIndex}. Ignore all other pieces.
     */
    public void startDownload() {
        if (state == State.STREAMING || state == State.STARTING)
            return;

        state = State.STARTING;

        log.debug("Starting torrent download");
        state = State.STARTING;

        List<Integer> indices = new ArrayList<>();

        Priority[] priorities = torrentHandle.piecePriorities();
        for (int i = 0; i < priorities.length; i++) {
            if (priorities[i] != Priority.IGNORE) {
                torrentHandle.piecePriority(i, Priority.NORMAL);
            }
        }

        for (int i = 0; i < piecesToPrepare; i++) {
            indices.add(lastPieceIndex - i);
            torrentHandle.piecePriority(lastPieceIndex - i, Priority.SEVEN);
            torrentHandle.setPieceDeadline(lastPieceIndex - i, 1000);
        }

        for (int i = 0; i < piecesToPrepare; i++) {
            indices.add(firstPieceIndex + i);
            torrentHandle.piecePriority(firstPieceIndex + i, Priority.SEVEN);
            torrentHandle.setPieceDeadline(firstPieceIndex + i, 1000);
        }

        preparePieces = indices;
        log.trace("Prioritizing the following pieces: {}", preparePieces);

        hasPieces = new Boolean[lastPieceIndex - firstPieceIndex + 1];
        Arrays.fill(hasPieces, false);

        var torrentInfo = torrentHandle.torrentFile();
        var status = torrentHandle.status();

        double blockCount = (float) indices.size() * torrentInfo.pieceLength() / status.blockSize();

        progressStep = 100 / blockCount;

        torrentStreamReferences.clear();
        torrentHandle.resume();
        listener.onStreamStarted(this);
    }

    /**
     * Check if the piece that contains the specified bytes were downloaded already
     *
     * @param bytes The bytes you're interested in
     * @return {@code true} if downloaded, {@code false} if not
     */
    public boolean hasBytes(long bytes) {
        if (hasPieces == null) {
            return false;
        }

        int pieceIndex = (int) (bytes / torrentHandle.torrentFile().pieceLength());
        return hasPieces[pieceIndex];
    }

    /**
     * Set the bytes of the selected file that you're interested in
     * The piece of that specific offset is selected and that piece plus the 1 preceding and the 3 after it.
     * These pieces will then be prioritised, which results in continuing the sequential download after that piece
     *
     * @param bytes The bytes you're interested in
     */
    public void setInterestedBytes(long bytes) {
        if (hasPieces == null && bytes >= 0) {
            return;
        }

        int pieceIndex = (int) (bytes / torrentHandle.torrentFile().pieceLength());
        interestedPieceIndex = pieceIndex;
        if (!hasPieces[pieceIndex] && torrentHandle.piecePriority(pieceIndex + firstPieceIndex) != Priority.SEVEN) {
            interestedPieceIndex = pieceIndex;
            int pieces = 5;
            for (int i = pieceIndex; i < hasPieces.length; i++) {
                // Set full priority to first found piece that is not confirmed finished
                if (!hasPieces[i]) {
                    torrentHandle.piecePriority(i + firstPieceIndex, Priority.SEVEN);
                    torrentHandle.setPieceDeadline(i + firstPieceIndex, 1000);
                    pieces--;
                    if (pieces == 0) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Checks if the interesting pieces are downloaded already
     *
     * @return {@code true} if the 5 pieces that were selected using `setInterestedBytes` are all reported complete including the `nextPieces`, {@code false}
     * if not
     */
    public boolean hasInterestedBytes(int nextPieces) {
        for (int i = 0; i < 5 + nextPieces; i++) {
            int index = interestedPieceIndex + i;
            if (hasPieces.length <= index || index < 0) {
                continue;
            }

            if (!hasPieces[interestedPieceIndex + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the interesting pieces are downloaded already
     *
     * @return {@code true} if the 5 pieces that were selected using `setInterestedBytes` are all reported complete, {@code false} if not
     */
    public boolean hasInterestedBytes() {
        return hasInterestedBytes(5);
    }

    /**
     * Get the index of the piece we're currently interested in
     *
     * @return Interested piece index
     */
    public int getInterestedPieceIndex() {
        return interestedPieceIndex;
    }

    /**
     * Get amount of pieces to prepare
     *
     * @return Amount of pieces to prepare
     */
    public Integer getPiecesToPrepare() {
        return piecesToPrepare;
    }

    /**
     * Start sequential mode downloading
     */
    private void startSequentialMode() {
        resetPriorities();

        if (hasPieces == null) {
            torrentHandle.setFlags(torrentHandle.flags().and_(TorrentFlags.SEQUENTIAL_DOWNLOAD));
        } else {
            for (int i = firstPieceIndex + piecesToPrepare; i < firstPieceIndex + piecesToPrepare + SEQUENTIAL_CONCURRENT_PIECES_COUNT; i++) {
                torrentHandle.piecePriority(i, Priority.SEVEN);
                torrentHandle.setPieceDeadline(i, 1000);
            }
        }
    }

    /**
     * Get current torrent state
     *
     * @return {@link State}
     */
    public State getState() {
        return state;
    }

    private void pieceFinished(PieceFinishedAlert alert) {
        var pieceIndex = alert.pieceIndex() - firstPieceIndex;

        if (hasPieces != null && (pieceIndex < 0 || pieceIndex > hasPieces.length))
            return;

        if (state == State.STREAMING && hasPieces != null) {
            hasPieces[pieceIndex] = true;

            if (pieceIndex >= interestedPieceIndex) {
                for (int i = pieceIndex; i < hasPieces.length; i++) {
                    // Set full priority to first found piece that is not confirmed finished
                    if (!hasPieces[i]) {
                        torrentHandle.piecePriority(i + firstPieceIndex, Priority.SEVEN);
                        torrentHandle.setPieceDeadline(i + firstPieceIndex, 1000);
                        break;
                    }
                }
            }
        } else {
            preparePieces.removeIf(index -> index == alert.pieceIndex());
            log.trace("Priority piece \"{}\" completed, waiting for another {} priority piece(s) to complete", alert.pieceIndex(), preparePieces.size());

            if (hasPieces != null) {
                hasPieces[alert.pieceIndex() - firstPieceIndex] = true;
            }

            if (preparePieces.size() == 0) {
                startSequentialMode();

                prepareProgress = 100d;
                state = State.STREAMING;

                if (listener != null) {
                    listener.onStreamReady(this);
                }
            }
        }
    }

    private void blockFinished(BlockFinishedAlert alert) {
        for (Integer index : preparePieces) {
            if (index == alert.pieceIndex()) {
                prepareProgress += progressStep;
                break;
            }
        }
    }

    private void sendStreamProgress() {
        var status = torrentHandle.status();

        if (listener != null && prepareProgress >= 1) {
            listener.onStreamProgress(this, StreamStatus.builder()
                    .progress(status.progress())
                    .downloadSpeed(status.downloadRate())
                    .uploadSpeed(status.uploadRate())
                    .seeds(status.numSeeds())
                    .downloaded(status.totalWantedDone())
                    .totalSize(status.totalWanted())
                    .build());
        }
    }

    @Override
    public int[] types() {
        return new int[]{
                AlertType.PIECE_FINISHED.swig(),
                AlertType.BLOCK_FINISHED.swig(),
                AlertType.STATS.swig()
        };
    }

    @Override
    public void alert(Alert<?> alert) {
        handleAlert(alert);
        invokeTorrentStreamAlerts(alert);
    }

    private void handleAlert(Alert<?> alert) {
        try {
            switch (alert.type()) {
                case PIECE_FINISHED:
                    pieceFinished((PieceFinishedAlert) alert);
                    break;
                case BLOCK_FINISHED:
                    blockFinished((BlockFinishedAlert) alert);
                    break;
                case STATS:
                    sendStreamProgress();
                    break;
                default:
                    break;
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    //TODO: rework as the torrent service needs to be responsible for registering the input stream to the alerts
    private void invokeTorrentStreamAlerts(Alert<?> alert) {
        try {
            Iterator<WeakReference<TorrentInputStream>> i = torrentStreamReferences.iterator();

            while (i.hasNext()) {
                WeakReference<TorrentInputStream> reference = i.next();
                TorrentInputStream inputStream = reference.get();

                if (inputStream == null) {
                    i.remove();
                    continue;
                }

                inputStream.alert(alert);
            }
        } catch (Exception ex) {
            log.error("Failed to invoke torrent streams, " + ex.getMessage(), ex);
        }
    }
}
