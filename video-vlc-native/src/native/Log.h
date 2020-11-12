#ifndef POPCORNPLAYER_LOG_H
#define POPCORNPLAYER_LOG_H

#include <string>

class Log {
public:
    ~Log();

    static Log* getInstance();

    void trace(const char* message);

    void debug(const char* message);

    void info(const char* message);

    void info(const std::basic_string<char>& message);

    void warn(const char* message);

    void warn(const std::basic_string<char>& message);

    void error(const char* message);

    void error(const std::basic_string<char>& message);

    void error(const char* message, const std::exception& ex);

    void error(const std::basic_string<char>& message, const std::exception& ex);

private:
    Log();

    static void log(const char* message, const char string[6]);

    static void logToSysError(const char* message, const char string[6]);

    static Log* instance;

    static std::__cxx11::basic_string<char> appName();
};

#endif //POPCORNPLAYER_LOG_H
