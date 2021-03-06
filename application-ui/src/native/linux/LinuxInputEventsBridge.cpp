#include "LinuxInputEventsBridge.h"

#include "GnomeInputEvents.h"
#include "X11InputEvents.h"

#include <regex>

using namespace std;

LinuxInputEventsBridge::LinuxInputEventsBridge()
    : IInputEventsBridge()
{
    this->_log = Log::instance();

    this->_inputEvents = nullptr;

    init();
}

LinuxInputEventsBridge::~LinuxInputEventsBridge()
{
    _log->trace("Releasing the linux input events bridge resources");
    delete _inputEvents;
}

void LinuxInputEventsBridge::addMediaCallback(std::function<void(MediaKeyType)> callback)
{
    _log->trace("Registering new media key press callback in the linux input events bridge");
    _callback = callback;
}

void LinuxInputEventsBridge::grabMediaKeys()
{
    _log->debug("Grabbing linux media keys");
}

void LinuxInputEventsBridge::releaseMediaKeys()
{
    _log->debug("Releasing linux media keys");
}

void LinuxInputEventsBridge::init()
{
    _log->debug("Using linux inputs event bridge");
    bool gnomeDetected = isGnomeDesktop();

    if (gnomeDetected) {
        useGnomeInputEvents();
    } else {
        useX11InputEvents();
    }

    // listen to the media key pressed within the specific input events
    _inputEvents->onMediaKeyPressed([&](MediaKeyType type) {
        if (_callback != nullptr) {
            _callback(type);
        }
    });

    _log->debug("Linux inputs event bridge initialized");
}

void LinuxInputEventsBridge::useGnomeInputEvents()
{
    _log->info("Using Gnome key input events");
    _inputEvents = new GnomeInputEvents();
}

void LinuxInputEventsBridge::useX11InputEvents()
{
    _log->info("Using X11 key input events");
    _inputEvents = new X11InputEvents();
}

bool LinuxInputEventsBridge::isGnomeDesktop()
{
    char *desktop = getenv("DESKTOP_SESSION");

    // check if the current desktop environment could be found
    // if so, check if we can find the ubuntu indication
    if (desktop != nullptr) {
        _log->trace("Desktop session has been detected");

        if (desktop == std::string("ubuntu")) {
            char *xdgType = getenv("XDG_CURRENT_DESKTOP");
            auto gnomeRegex = std::regex(".*(:gnome)", std::regex_constants::icase);

            if (strlen(xdgType) > 0) {
                _log->trace(std::string("Detected XDG type: \"") + xdgType + "\"");
                return regex_search(xdgType, gnomeRegex);
            }
        } else if (desktop == std::string("LXDE-pi")) {
            _log->trace("Detected LXDE desktop session type");
            return false;
        }
    }

    _log->warn("Unable to detect desktop session, falling back to X11");
    return false;
}
