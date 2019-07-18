#ifndef BMI_PROTOCOL_H_
#define BMI_PROTOCOL_H_

#include "bmi160.h"
#include <stdint.h>

void bmi_protocol_init(struct bmi160_dev* dev_ctx);

int8_t bmi_protocol_read(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len);

int8_t bmi_protocol_write(uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len);
 
#endif