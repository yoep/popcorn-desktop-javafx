#ifndef POPCORNDESKTOPPLAYER_PLAYERWINDOW_H
#define POPCORNDESKTOPPLAYER_PLAYERWINDOW_H


#include "VideoPlayer.h"

class PlayerWindow {
public:
    PlayerWindow(int &argc, char **argv);

    [[nodiscard]] VideoPlayer *getPlayer() const;

    int exec();

    void show();

    void play(const char *mrl);

    void showMaximized();

    void close();

    bool isInitialized();

    bool isMaximized();

    void setMaximized(bool maximized);

private:
    int &argc;
    char **argv;
    QApplication *app;
    QWidget *window;
    VideoPlayer *player;

    static bool isHttpUrl(const char *string);
};


#endif //POPCORNDESKTOPPLAYER_PLAYERWINDOW_H