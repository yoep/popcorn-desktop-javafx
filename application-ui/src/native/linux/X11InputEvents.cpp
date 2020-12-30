#include "X11InputEvents.h"

#include "X11/Xlib.h"

#include <xkbcommon/xkbcommon-keysyms.h>

X11InputEvents::X11InputEvents()
{
    this->_display = XOpenDisplay(nullptr);
    this->_window = XDefaultRootWindow(_display);
    this->_eventThread = nullptr;

    init();
}

X11InputEvents::~X11InputEvents()
{
    this->_keepAlive = false;

    // check if the event thread is still joinable
    if (_eventThread != nullptr && _eventThread->joinable()) {
        // wait for the event thread to quit
        _eventThread->join();
    }

    unregisterKeys();
}

void X11InputEvents::onMediaKeyPressed(std::function<void(MediaKeyType)> mediaKeyPressed)
{
    _mediaKeyPressed = mediaKeyPressed;
}

void X11InputEvents::init()
{
    // register keys
    registerKeys();

    std::thread eventThread([this] {
        auto event = new XEvent();

        while (_keepAlive) {
            while (XPending(_display) > 0) {
                XNextEvent(_display, event);

                if (event->type == KeyPress) {
                    processEvent(event);
                }
            }
        }
    });

    this->_eventThread = &eventThread;
}

void X11InputEvents::processEvent(XEvent *event)
{
    auto keycode = event->xkey.keycode;
    MediaKeyType type;

    switch (keycode) {
    case XKB_KEY_XF86AudioPlay:
        type = MediaKeyType::PLAY;
        break;
    case XKB_KEY_XF86AudioStop:
        type = MediaKeyType::STOP;
        break;
    case XKB_KEY_XF86AudioPrev:
        type = MediaKeyType::PREVIOUS;
        break;
    case XKB_KEY_XF86AudioNext:
        type = MediaKeyType::NEXT;
        break;
    case XKB_KEY_XF86AudioLowerVolume:
        type = MediaKeyType::VOLUME_LOWER;
        break;
    case XKB_KEY_XF86AudioRaiseVolume:
        type = MediaKeyType::VOLUME_HIGHER;
        break;
    default:
        // no-op
        break;
    }

    if (_mediaKeyPressed != nullptr) {
        _mediaKeyPressed(type);
    }
}

void X11InputEvents::registerKeys()
{
    auto keys = getKeys();

    for (int i = 0; i < sizeof(keys); ++i) {
        auto key = keys[i];

        XGrabKey(_display, key, 0, _window, true, GrabModeAsync, GrabModeAsync);
    }
}

void X11InputEvents::unregisterKeys()
{
    auto keys = getKeys();

    for (int i = 0; i < sizeof(keys); ++i) {
        auto key = keys[i];

        XUngrabKey(_display, key, 0, _window);
    }
}

int *X11InputEvents::getKeys()
{
    static int keys[6];

    keys[0] = XKB_KEY_XF86AudioPlay;
    keys[1] = XKB_KEY_XF86AudioStop;
    keys[2] = XKB_KEY_XF86AudioPrev;
    keys[3] = XKB_KEY_XF86AudioNext;
    keys[4] = XKB_KEY_XF86AudioLowerVolume;
    keys[5] = XKB_KEY_XF86AudioRaiseVolume;

    return keys;
}
