#ifndef POPCORNTIME_X11INPUTEVENTS_H
#define POPCORNTIME_X11INPUTEVENTS_H

#include <IInputEvents.h>
#include <X11/Xlib.h>
#include <atomic>
#include <thread>

using namespace std;

class X11InputEvents : public IInputEvents {
public:
    X11InputEvents();

    ~X11InputEvents();

    void onMediaKeyPressed(std::function<void(MediaKeyType)> mediaKeyPressed) override;

private:
    Display *_display;
    Window _window;
    std::thread *_eventThread;
    std::atomic<bool> _keepAlive = true;
    std::function<void(MediaKeyType)> _mediaKeyPressed;

    void init();

    void processEvent(XEvent *event);

    void registerKeys();

    void unregisterKeys();

    static int *getKeys();
};

#endif //POPCORNTIME_X11INPUTEVENTS_H
