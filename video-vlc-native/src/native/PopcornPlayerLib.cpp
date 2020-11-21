#include "PopcornPlayerLib.h"

#include "PopcornPlayer.h"

#include <iostream>

using namespace std;

struct popcorn_player_t {
    void *player;
};

popcorn_player_t *popcorn_player_new(int argc, char **argv)
{
    popcorn_player_t *pdp;

    // initialize the return type
    pdp = (typeof(pdp))malloc(sizeof(*pdp));

    // create a new PopcornPlayer instance
    auto *player = new PopcornPlayer(argc, argv);

    // assign the player to the return struct for later use
    pdp->player = player;

    return pdp;
}

int popcorn_player_exec(popcorn_player_t *pdp)
{
    if (pdp == nullptr)
        return -1;

    PopcornPlayer *player;

    player = static_cast<PopcornPlayer *>(pdp->player);
    return player->exec();
}

void popcorn_player_release(popcorn_player_t *pdp)
{
    if (pdp == nullptr)
        return;

    PopcornPlayer *player;

    player = static_cast<PopcornPlayer *>(pdp->player);
    player->close();

    delete static_cast<PopcornPlayer *>(pdp->player);
    free(pdp);
}

void popcorn_player_play(popcorn_player_t *pdp, const char *mrl)
{
    if (pdp == nullptr)
        return;

    PopcornPlayer *player;

    player = static_cast<PopcornPlayer *>(pdp->player);
    player->play(mrl);
}

void popcorn_player_pause(popcorn_player_t *pdp)
{
    if (pdp == nullptr)
        return;

    PopcornPlayer *player;

    player = static_cast<PopcornPlayer *>(pdp->player);
    player->pause();
}

void popcorn_player_resume(popcorn_player_t *pdp)
{
    if (pdp == nullptr)
        return;

    PopcornPlayer *player;

    player = static_cast<PopcornPlayer *>(pdp->player);
    player->resume();
}

void popcorn_player_stop(popcorn_player_t *pdp)
{
    if (pdp == nullptr)
        return;

    PopcornPlayer *player;

    player = static_cast<PopcornPlayer *>(pdp->player);
    player->stop();
}

void popcorn_player_show(popcorn_player_t *pdp)
{
    if (pdp == nullptr)
        return;

    PopcornPlayer *player;

    player = static_cast<PopcornPlayer *>(pdp->player);
    player->show();
}

void popcorn_player_fullscreen(popcorn_player_t *pdp, bool fullscreen)
{
    if (pdp == nullptr)
        return;

    PopcornPlayer *player;

    player = static_cast<PopcornPlayer *>(pdp->player);
    player->setFullscreen(fullscreen);
}

void popcorn_player_subtitle(popcorn_player_t *pdp, const char *uri)
{
    if (pdp == nullptr)
        return;

    PopcornPlayer *player;

    player = static_cast<PopcornPlayer *>(pdp->player);
    player->setSubtitleFile(uri);
}

void popcorn_player_subtitle_delay(popcorn_player_t *pdp, long delay)
{
    if (pdp == nullptr)
        return;

    PopcornPlayer *player;

    player = static_cast<PopcornPlayer *>(pdp->player);
    player->setSubtitleDelay(delay);
}
