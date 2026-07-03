#ifndef WIFI_MANAGER_H
#define WIFI_MANAGER_H

#include <stdbool.h>

void wifi_manager_init(void);
void wifi_manager_connect(const char *ssid, const char *password);
void wifi_manager_disconnect(void);
bool wifi_manager_is_connected(void);

#endif /* WIFI_MANAGER_H */
