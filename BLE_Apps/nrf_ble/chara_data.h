#ifndef CHARA_DATA_H_
#define CHARA_DATA_H_

// https://github.com/bjornspockeli/custom_ble_service_example
#include "ble.h"
#include "my_service.h"
#include "ble_srv_common.h"
#include <stdbool.h>
#include <stdint.h>
#include "imu.h"

#define CHARA_DATA_UUID 0x4201

uint32_t chara_data_add(my_service_t *p_cus, const my_service_init_t *p_cus_init);

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
uint32_t chara_data_update(my_service_t *p_cus, imu_data custom_value);

#endif