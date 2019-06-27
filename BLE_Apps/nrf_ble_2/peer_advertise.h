#ifndef PEER_ADVERTISE_H_
#define PEER_ADVERTISE_H_

#include <stdbool.h>
#include <stdint.h>
#include "ble_advertising.h"

/**
@brief Function for initializing the Advertising functionality.
 */
void advertising_init(ble_advertising_t* m_advertising, uint8_t app_ble_conn_cfg_tag, void (*on_advertise_start_p)(void), void (*on_advertise_timeout_p)(void));

/**
@brief Function for the Peer Manager initialization.
 */
void peer_manager_init(void);

/**
@brief Function for starting advertising.
 */
void advertising_start(void);

#endif  