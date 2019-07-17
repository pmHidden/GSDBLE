#ifndef CHARA_CONF_H_
#define CHARA_CONF_H_

// https://github.com/bjornspockeli/custom_ble_service_example
#include "imu.h"
#include "my_service.h"

#define CHARA_CONF_UUID 0x4202 // b2154202-d1a3-e541-8bf7-8c8eb0519548

uint32_t chara_conf_add(my_service_t *p_cus);

typedef struct {
  uint8_t speed : 3;  // refers to imu_data_t
  uint8_t pause : 1;
  uint8_t unused : 4; // so we have a good size % 8 == 0
} chara_conf_packet_t;

typedef union {
  uint8_t bytes[1];
  chara_conf_packet_t parsed;
} chara_conf_packet_u;

/**@brief Function for updating the custom value.
 *
 * @details The application calls this function when the cutom value should be updated. If
 *          notification has been enabled, the custom value characteristic is sent to the client.
 *
 * @note 
 *       
 * @param[in]   p_cus          Custom Service structure.
 * @param[in]   Custom value 
 *
 * @return      NRF_SUCCESS on success, otherwise an error code.
 */
uint32_t chara_conf_update(my_service_t *p_cus, chara_conf_packet_t new_value);

#endif