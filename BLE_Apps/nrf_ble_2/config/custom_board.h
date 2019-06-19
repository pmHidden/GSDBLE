#ifndef CUSTOM_BOARD_H
#define CUSTOM_BOARD_H

#ifdef __cplusplus
extern "C" {
#endif

#include "nrf_gpio.h"

// definitions for gsdble
#define LEDS_NUMBER    2

#define LED_1          17
#define LED_2          18

#define LEDS_ACTIVE_STATE 0

#define LEDS_INV_MASK  LEDS_MASK

#define LEDS_LIST { LED_1, LED_2 }

#define BSP_LED_0      LED_1
#define BSP_LED_1      LED_2

#define BUTTONS_NUMBER 1

#define BUTTON_START   13
#define BUTTON_1       13
#define BUTTON_STOP    13
#define BUTTON_PULL    NRF_GPIO_PIN_PULLUP

#define BUTTONS_ACTIVE_STATE 0

#define BUTTONS_LIST { BUTTON_1 }

#define BSP_BUTTON_0   BUTTON_1

#ifdef __cplusplus
}
#endif

#endif // CUSTOM_BOARD_H
