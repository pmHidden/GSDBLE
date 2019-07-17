#ifndef LSM_PROTOCOL_H_
#define LSM_PROTOCOL_H_

#include "lsm6dsl_reg.h"
#include <stdint.h>

void lsm_protocol_init();

int32_t lsm_protocol_read(void *handle, uint8_t reg_addr, uint8_t *data, uint16_t len);

int32_t lsm_protocol_write(void *handle, uint8_t reg_addr, uint8_t *data, uint16_t len);

#endif