#ifndef CHARA_CONF_H_
#define CHARA_CONF_H_

// https://github.com/bjornspockeli/custom_ble_service_example
#include "ble.h"
#include "my_service.h"
#include "ble_srv_common.h"
#include <stdbool.h>
#include <stdint.h>

#define CHARA_CONF_UUID 0x4202

uint32_t chara_conf_add(my_service_t *p_cus, const my_service_init_t *p_cus_init);

#endif