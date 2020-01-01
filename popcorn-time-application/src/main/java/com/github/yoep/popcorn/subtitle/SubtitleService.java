package com.github.yoep.popcorn.subtitle;

import com.github.yoep.popcorn.config.properties.PopcornProperties;
import com.github.yoep.popcorn.media.providers.models.Media;
import com.github.yoep.popcorn.media.providers.models.Movie;
import com.github.yoep.popcorn.media.providers.models.Show;
import com.github.yoep.popcorn.settings.models.SubtitleSettings;
import com.github.yoep.popcorn.subtitle.models.Subtitle;
import com.github.yoep.popcorn.subtitle.models.SubtitleInfo;
import com.github.yoep.popcorn.subtitle.models.SubtitleLanguage;
import com.github.yoep.popcorn.config.properties.SubtitleProperties;
import com.github.yoep.popcorn.media.providers.models.Episode;
import com.github.yoep.popcorn.settings.SettingsService;
import de.timroes.axmlrpc.XMLRPCCallback;
import de.timroes.axmlrpc.XMLRPCClient;
import de.timroes.axmlrpc.XMLRPCException;
import de.timroes.axmlrpc.XMLRPCServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubtitleService {
    private static final Pattern QUALITY_PATTERN = Pattern.compile("([0-9]+p)");

    private final Map<String, List<SubtitleInfo>> cachedSubtitles = new HashMap<>();
    private final PopcornProperties popcornProperties;
    private final SettingsService settingsService;
    private final RestTemplate restTemplate;
    private final XMLRPCClient client;

    //region Methods

    /**
     * Download the SRT file for the given {@link SubtitleInfo}.
     *
     * @param subtitle The subtitle information file to download.
     * @return Returns the downloaded SRT file.
     */
    @Async
    public CompletableFuture<File> download(final SubtitleInfo subtitle) {
        Assert.notNull(subtitle, "subtitle cannot be null");

        return CompletableFuture.completedFuture(internalDownload(subtitle));
    }

    /**
     * Retrieve the subtitle for the given media.
     *
     * @param media The media to retrieve the subtitles of.
     * @return Returns the list of available subtitles for the media.
     */
    @Async
    public CompletableFuture<List<SubtitleInfo>> retrieveSubtitles(final Movie media) {
        // check if the subtitles were already cached
        if (cachedSubtitles.containsKey(media.getImdbId()))
            return CompletableFuture.completedFuture(cachedSubtitles.get(media.getImdbId()));

        var completableFuture = new CompletableFuture<List<SubtitleInfo>>();
        var searchHandler = createSearchCallbackHandler(media, completableFuture);
        var loginHandler = createLoginCallbackHandler(token -> search(media, token, searchHandler), completableFuture);

        completableFuture.thenAccept(e -> cachedSubtitles.put(media.getImdbId(), e));

        login(loginHandler);
        return completableFuture;
    }

    /**
     * Retrieve the subtitle for the given media.
     *
     * @param media   The media to retrieve the subtitles of.
     * @param episode The episode of the media to retrieve the subtitle of.
     * @return Returns the list of available subtitles for the media.
     */
    @Async
    public CompletableFuture<List<SubtitleInfo>> retrieveSubtitles(final Show media, final Episode episode) {
        var cacheId = String.valueOf(episode.getTvdbId());

        // check if the subtitles were already cached
        if (cachedSubtitles.containsKey(cacheId))
            return CompletableFuture.completedFuture(cachedSubtitles.get(cacheId));

        var completableFuture = new CompletableFuture<List<SubtitleInfo>>();
        var searchHandler = createSearchCallbackHandler(media, completableFuture);
        var loginHandler = createLoginCallbackHandler(token -> search(media, episode, token, searchHandler), completableFuture);

        completableFuture.thenAccept(e -> cachedSubtitles.put(cacheId, e));

        login(loginHandler);
        return completableFuture;
    }

    /**
     * Parse the given SRT file to a list of {@link Subtitle}'s.
     *
     * @param file The SRT file to parse.
     * @return Returns the parsed SRT file.
     */
    @Async
    public CompletableFuture<List<Subtitle>> parse(File file) {
        Assert.notNull(file, "file cannot be null");
        if (!file.exists())
            return CompletableFuture.failedFuture(
                    new SubtitleException(String.format("Failed to parse subtitle file, file \"%s\" does not exist", file.getAbsolutePath())));

        return CompletableFuture.completedFuture(internalParse(file));
    }

    /**
     * Download and parse the SRT file for the given {@link SubtitleInfo}.
     *
     * @param subtitleInfo The subtitle info to download and parse.
     * @return Returns the subtitles of the given subtitle info.
     */
    @Async
    public CompletableFuture<List<Subtitle>> downloadAndParse(SubtitleInfo subtitleInfo) {
        Assert.notNull(subtitleInfo, "subtitleInfo cannot be null");
        File file = internalDownload(subtitleInfo);

        return CompletableFuture.completedFuture(internalParse(file));
    }

    //endregion

    //region PostConstruct

    @PostConstruct
    private void init() {
        initializeListeners();
    }

    private void initializeListeners() {
        SubtitleSettings settings = getSettings();

        settings.addListener(evt -> {
            if (SubtitleSettings.DIRECTORY_PROPERTY.equals(evt.getPropertyName())) {
                // clean old directory
                if (settings.isAutoCleaningEnabled())
                    cleanCacheDirectory((File) evt.getOldValue());
            }
        });
    }

    //endregion

    //region PreDestroy

    @PreDestroy
    private void destroy() {
        var settings = getSettings();

        if (settings.isAutoCleaningEnabled() && settings.getDirectory().exists()) {
            cleanCacheDirectory(settings.getDirectory());
        }
    }

    //endregion

    //region Functions

    private XMLRPCCallback createLoginCallbackHandler(Consumer<String> onResponse, CompletableFuture<List<SubtitleInfo>> completableFuture) {
        return new XMLRPCCallback() {
            @Override
            public void onResponse(long id, Object result) {
                log.trace("Received login response from {}", popcornProperties.getSubtitle().getUrl());
                Map<String, Object> response = (Map<String, Object>) result;
                String token = (String) response.get("token");

                if (token != null && !token.isEmpty()) {
                    onResponse.accept(token);
                } else {
                    completableFuture.completeExceptionally(new SubtitleException("Failed to retrieve subtitles, login token is not correct"));
                }
            }

            @Override
            public void onError(long id, XMLRPCException error) {
                completableFuture.completeExceptionally(error);
            }

            @Override
            public void onServerError(long id, XMLRPCServerException error) {
                completableFuture.completeExceptionally(error);
            }
        };
    }

    private XMLRPCCallback createSearchCallbackHandler(final Media media, final CompletableFuture<List<SubtitleInfo>> completableFuture) {
        return new XMLRPCCallback() {
            @Override
            public void onResponse(long id, Object result) {
                List<SubtitleInfo> subtitles = new ArrayList<>();
                Map<String, Object> subData = (Map<String, Object>) result;

                // add default subtitle
                subtitles.add(SubtitleInfo.none());

                if (subData != null && subData.get("data") != null && subData.get("data") instanceof Object[]) {
                    Object[] dataList = (Object[]) subData.get("data");
                    for (Object dataItem : dataList) {
                        Map<String, String> item = (Map<String, String>) dataItem;
                        if (!item.get("SubFormat").equals("srt")) {
                            continue;
                        }

                        // check if the media year matches the returned item
                        if (!item.get("MovieYear").equals(media.getYear())) {
                            continue;
                        }

                        String url = item.get("SubDownloadLink").replace(".gz", ".srt");
                        SubtitleLanguage language = SubtitleLanguage.valueOfCode(item.get("ISO639").replace("pb", "pt-br"));

                        // check if language is known within the subtitle languages
                        // if not known, ignore this subtitle and continue with the next one
                        if (language == null)
                            continue;

                        int downloads = Integer.parseInt(item.get("SubDownloadsCnt"));
                        int score = 0;

                        if (item.get("MatchedBy").equals("tag")) {
                            score += 50;
                        }
                        if (item.get("UserRank").equals("trusted")) {
                            score += 100;
                        }

                        Optional<SubtitleInfo> subtitle = subtitles.stream()
                                .filter(e -> e.getLanguage() == language)
                                .findAny();

                        if (subtitle.isPresent()) {
                            SubtitleInfo sub = subtitle.get();

                            if (score > sub.getScore() || (score == sub.getScore() && downloads > sub.getDownloads())) {
                                sub.setUrl(url);
                                sub.setScore(score);
                                sub.setDownloads(downloads);
                            }
                        } else {
                            subtitles.add(new SubtitleInfo(media.getImdbId(), language, url, score, downloads));
                        }
                    }

                    log.debug("Found {} subtitles for \"{}\" media ({})", subtitles.size(), media.getTitle(), media.getImdbId());
                    completableFuture.complete(subtitles);
                } else {
                    String message = MessageFormat.format("No subtitles found for \"{0}\" media ({1})", media.getTitle(), media.getImdbId());
                    completableFuture.completeExceptionally(new SubtitleException(message));
                }
            }

            @Override
            public void onError(long id, XMLRPCException error) {
                completableFuture.completeExceptionally(error);
            }

            @Override
            public void onServerError(long id, XMLRPCServerException error) {
                completableFuture.completeExceptionally(error);
            }
        };
    }

    /**
     * Login to server and get token
     *
     * @param callback XML RPC callback
     */
    private void login(XMLRPCCallback callback) {
        SubtitleProperties subtitleProperties = popcornProperties.getSubtitle();
        log.trace("Logging in to {}", subtitleProperties.getUrl());

        client.callAsync(callback, "LogIn", "", "", "en", subtitleProperties.getUserAgent());
    }

    /**
     * @param episode  Episode
     * @param token    Login token
     * @param callback XML RPC callback callback
     */
    private void search(Show show, Episode episode, String token, XMLRPCCallback callback) {
        Map<String, String> option = new HashMap<>();
        option.put("imdbid", show.getImdbId().replace("tt", ""));
        option.put("season", String.valueOf(episode.getSeason()));
        option.put("episode", String.valueOf(episode.getEpisode()));
        option.put("sublanguageid", "all");

        client.callAsync(callback, "SearchSubtitles", token, new Object[]{option});
    }

    /**
     * @param movie    Movie
     * @param token    Login token
     * @param callback XML RPC callback callback
     */
    private void search(Movie movie, String token, XMLRPCCallback callback) {
        log.trace("Searching for \"{}\" movie subtitles ({})", movie.getTitle(), movie.getImdbId());
        Map<String, String> option = new HashMap<>();
        option.put("imdbid", movie.getImdbId().replace("tt", ""));
        option.put("sublanguageid", "all");

        client.callAsync(callback, "SearchSubtitles", token, new Object[]{option});
    }

    private File internalDownload(SubtitleInfo subtitle) {
        // check if the given subtitle is the special "none" subtitle, if so, ignore the download
        if (subtitle.isNone()) {
            String message = "subtitle is special type \"none\"";

            log.debug("Skipping subtitle download, {}", message);
            throw new SubtitleException("Failed to download subtitle, " + message);
        }

        SubtitleSettings settings = getSettings();
        File storageFile = getStorageFile(subtitle);
        File subtitleFile;

        // check if the subtitle file was already downloaded before, if so, return the cached file
        if (storageFile.exists()) {
            log.debug("Returning cached subtitle file \"{}\"", storageFile.getAbsolutePath());
            subtitleFile = storageFile;
        } else {
            log.debug("Downloading subtitle file \"{}\" to \"{}\"", subtitle.getUrl(), storageFile.getAbsolutePath());
            subtitleFile = restTemplate.execute(subtitle.getUrl(), HttpMethod.GET, null, response -> {
                StreamUtils.copy(response.getBody(), new FileOutputStream(storageFile));
                return storageFile;
            });
        }

        return subtitleFile;
    }

    private List<Subtitle> internalParse(File file) {
        return SrtParser.parse(file);
    }

    private SubtitleSettings getSettings() {
        return settingsService.getSettings().getSubtitleSettings();
    }

    private File getStorageFile(SubtitleInfo subtitle) {
        String filename = FilenameUtils.getName(subtitle.getUrl());
        File subtitleDirectory = getSettings().getDirectory();

        // make sure the subtitle directory exists
        subtitleDirectory.mkdirs();

        return new File(subtitleDirectory.getAbsolutePath() + File.separator + filename);
    }

    private void cleanCacheDirectory(File directory) {
        try {
            log.info("Cleaning subtitles directory {}", directory);
            FileUtils.cleanDirectory(directory);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    //endregion
}