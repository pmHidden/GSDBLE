#ifndef BOARD_MANAGER_H_
#define BOARD_MANAGER_H_

#include <stdbool.h>
#include <stdint.h>

/**@brief Function for initializing buttons and leds.
 */
void bsp_module_init(void (*disconnect)(), uint16_t (*getConnHandle)());

void bsp_ind_adv(void);
void bsp_ind_idle(void);
void bsp_ind_conn(void);
void bsp_ind_error(void);
void bsp_ready_for_sleep(void);

#endif