#include "MediaPlayer.h"

#include <QList>
#include <QtGui/QWidgetSet>
#include <string>

using namespace std;

MediaPlayer::MediaPlayer(libvlc_instance_t *vlcInstance)
{
    this->_log = Log::instance();

    _log->trace("Creating new media player");
    this->_vlcInstance = vlcInstance;
    this->_vlcMediaPlayer = libvlc_media_player_new(vlcInstance);
    this->_vlcEventManager = nullptr;
    this->_media = nullptr;

    initializeMediaPlayer();
}

MediaPlayer::~MediaPlayer()
{
    unsubscribeEvents();
    releaseMediaPlayerIfNeeded();
}

bool MediaPlayer::play(Media *media)
{
    // check if the media reference is not empty
    // if so, log an error and exit with a failure
    if (media == nullptr) {
        _log->error("Cannot play NULL media");
        return false;
    }

    // connect the media events to this media player
    subscribeToMediaEvents(media);

    // update the active media item
    updateActiveMediaItem(media);

    // set the VLC media in the media player
    libvlc_media_player_set_media(_vlcMediaPlayer, media->vlcMedia());

    // start playing the media
    int result = libvlc_media_player_play(_vlcMediaPlayer);

    // check the result from the playback
    // if it failed, handle the error
    if (result == -1) {
        handleVlcError();
        return false;
    } else {
        return true;
    }
}

void MediaPlayer::seek(long time)
{
    try {
        _log->info(std::string("Seeking time ") + std::to_string(time) + std::string(" in the current media playback"));
        libvlc_media_player_set_time(_vlcMediaPlayer, time);
    } catch (std::exception &ex) {
        _log->error("An error occurred while seeking the time in the media playback", ex);
    }
}

void MediaPlayer::pause()
{
    try {
        _log->info("Pausing media player");
        libvlc_media_player_set_pause(_vlcMediaPlayer, 1);
    } catch (std::exception &ex) {
        _log->error("An error occurred while pausing the media playback", ex);
    }
}

void MediaPlayer::resume()
{
    try {
        _log->info("Resuming media player");
        libvlc_media_player_set_pause(_vlcMediaPlayer, 0);
    } catch (std::exception &ex) {
        _log->error("An error occurred while resuming the media playback", ex);
    }
}

void MediaPlayer::stop()
{
    try {
        _log->info("Stopping media player");
        libvlc_media_player_stop(_vlcMediaPlayer);

        releaseMediaItem();
    } catch (std::exception &ex) {
        _log->error("An error occurred while resuming the media playback", ex);
    }
}

void MediaPlayer::setMediaDuration(long newValue)
{
    _log->debug("Media player duration has changed to " + std::to_string(newValue));
    emit this->durationChanged(newValue);
}

void MediaPlayer::setVideoSurface(WId wid)
{
#if defined(Q_OS_WIN)
    log->trace("Adding Windows window to the VLC media player");
    void *drawable = (void *)wid;
    libvlc_media_player_set_hwnd(vlcMediaPlayer, drawable);
#elif defined(Q_OS_MAC)
    log->trace("Adding Mac window to the VLC media player");
    void *drawable = (void *)wid;
    libvlc_media_player_set_nsobject(vlcMediaPlayer, drawable);
#else
    _log->trace("Adding X window to the VLC media player");
    libvlc_media_player_set_xwindow(_vlcMediaPlayer, wid);
#endif

    _log->debug("Video surface has been updated of the media player");
}

void MediaPlayer::setSubtitleFile(const char *uri)
{
    _log->debug(std::string("Adding new subtitle track: ") + uri);

    if (libvlc_media_player_add_slave(_vlcMediaPlayer, libvlc_media_slave_type_subtitle, uri, true) == 0) {
        _log->info(std::string("Subtitle track \"") + uri + "\" has been added with success");
    } else {
        _log->error(std::string("Failed to add subtitle track ") + uri);
    }
}

void MediaPlayer::setSubtitleDelay(long delay)
{
    _log->debug(std::string("Updating subtitle delay to ") + std::to_string(delay) + "ms");
    libvlc_video_set_spu_delay(_vlcMediaPlayer, delay);
}

void MediaPlayer::releaseMediaPlayerIfNeeded()
{
    if (_vlcMediaPlayer == nullptr) {
        return;
    }

    _log->trace("Releasing current VLC media player resources");
    // stop the current media playback in case any media is still playing
    stop();
    // release the media player which was retained during construction if this media player
    libvlc_media_player_release(_vlcMediaPlayer);
}

void MediaPlayer::initializeMediaPlayer()
{
    _log->trace("Initializing media player");
    qRegisterMetaType<MediaPlayerState>("MediaPlayerState");

    libvlc_media_player_retain(_vlcMediaPlayer);
    _vlcEventManager = libvlc_media_player_event_manager(_vlcMediaPlayer);

    subscribeEvents();
    _log->debug("Media player initialized");
}

void MediaPlayer::subscribeEvents()
{
    if (_vlcEventManager == nullptr) {
        _log->warn("Unable to subscribe to VLC events, no VLC event manager present");
        return;
    }

    _log->trace("Subscribing to VLC media events");
    foreach (const libvlc_event_e event, eventList()) {
        libvlc_event_attach(_vlcEventManager, event, vlcCallback, this);
    }
    _log->debug("Subscribed to VLC media events");
}

void MediaPlayer::unsubscribeEvents()
{
    if (_vlcEventManager == nullptr) {
        _log->warn("Unable to unsubscribe from VLC events, no VLC event manager present");
        return;
    }

    _log->trace("Unsubscribing from VLC media events");
    foreach (const libvlc_event_e event, eventList()) {
        libvlc_event_detach(_vlcEventManager, event, vlcCallback, this);
    }
    _log->debug("Unsubscribed from VLC media events");
}

void MediaPlayer::subscribeToMediaEvents(Media *media)
{
    // check if we currently know about a previous media item
    // if so, disconnect the events from the old media first
    if (_media != nullptr) {
        _log->trace("Removing old media item listeners");
        disconnect(_media, &Media::durationChanged,
            this, &MediaPlayer::setMediaDuration);
    }

    _log->trace("Adding listeners to new media item");
    connect(media, &Media::durationChanged,
        this, &MediaPlayer::setMediaDuration);
}

void MediaPlayer::updateState(MediaPlayerState newState)
{
    // check if the old state is the same as the new state
    // if so, ignore the state update
    if (this->_state == newState)
        return;

    this->_state = newState;
    emit stateChanged(newState);
}

void MediaPlayer::updateActiveMediaItem(Media *media)
{
    // check if we know about a previous media item
    // if so, release the old media item first
    if (this->_media != nullptr) {
        releaseMediaItem();
    }

    this->_media = media;
}

void MediaPlayer::releaseMediaItem()
{
    _log->debug("Releasing media item");
    delete _media;
    _media = nullptr;
}

void MediaPlayer::vlcCallback(const libvlc_event_t *event, void *instance)
{
    Log *log = Log::instance();

    // check if the instance is valid
    // if not, throw an error as we'll be unable to do anything with the event
    if (instance == nullptr) {
        log->error("Invalid VLC callback event, instance is NULL");
    }

    auto *mediaPlayer = static_cast<MediaPlayer *>(instance);

    switch (event->type) {
    case libvlc_MediaPlayerPlaying:
        mediaPlayer->updateState(PLAYING);
        break;
    case libvlc_MediaPlayerPaused:
        mediaPlayer->updateState(PAUSED);
        break;
    case libvlc_MediaPlayerBuffering:
        if (event->u.media_player_buffering.new_cache < 100) {
            mediaPlayer->updateState(BUFFERING);
        } else {
            mediaPlayer->updateState(PLAYING);
        }
        break;
    case libvlc_MediaPlayerStopped:
        mediaPlayer->updateState(STOPPED);
        break;
    case libvlc_MediaPlayerTimeChanged:
        emit mediaPlayer->timeChanged(event->u.media_player_time_changed.new_time);
        break;
    default:
        log->warn(std::string("Unknown VLC media player event type ") + std::to_string(event->type));
        break;
    }
}

void MediaPlayer::handleVlcError()
{
    const char *message = libvlc_errmsg();

    if (message != nullptr) {
        _log->error(std::string("Media player encountered a VLC error: ") + message);
    }
}

QList<libvlc_event_e> MediaPlayer::eventList()
{
    QList<libvlc_event_e> eventList;
    eventList << libvlc_MediaPlayerPlaying;
    eventList << libvlc_MediaPlayerPaused;
    eventList << libvlc_MediaPlayerBuffering;
    eventList << libvlc_MediaPlayerStopped;
    eventList << libvlc_MediaPlayerTimeChanged;

    return eventList;
}