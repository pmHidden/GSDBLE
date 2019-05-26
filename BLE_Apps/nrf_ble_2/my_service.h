#ifndef MY_SERVICE_H_
#define MY_SERVICE_H_

// https://github.com/bjornspockeli/custom_ble_service_example
#include "ble.h"
#include "ble_srv_common.h"
#include <stdbool.h>
#include <stdint.h>

#define CUSTOM_SERVICE_UUID_BASE                       \
  {                                                    \
    0x48, 0x95, 0x51, 0xb0, 0x8e, 0x8c, 0xf7, 0x8b,    \
        0x41, 0xe5, 0xa3, 0xd1, 0x46, 0x3d, 0x15, 0xb2 \
  } // b2153d46-a3d1-41e5-8bf7-8c8eb0519548

#define CUSTOM_SERVICE_UUID 0x4200

#define MY_SERVICE_DEF(_name)       \
  static my_service_t _name;        \
  NRF_SDH_BLE_OBSERVER(_name##_obs, \
      BLE_HRS_BLE_OBSERVER_PRIO,    \
      ble_cus_on_ble_evt, &_name)

/**@brief Service init structure. This contains all options and data needed for
 *        initialization of the service.*/
typedef struct
{
  uint8_t initial_custom_value;                           /**< Initial custom value */
  ble_srv_cccd_security_mode_t custom_value_char_attr_md; /**< Initial security level for Custom characteristics attribute */
} my_service_init_t;

/**@brief Service structure. This contains various status information for the service. */
struct my_service_s {
  uint16_t service_handle;                       /**< Handle of Custom Service (as provided by the BLE stack). */
  ble_gatts_char_handles_t custom_value_handles; /**< Handles related to the Custom Value characteristic. */
  uint16_t conn_handle;                          /**< Handle of the current connection (as provided by the BLE stack, is BLE_CONN_HANDLE_INVALID if not in a connection). */
  uint8_t uuid_type;
}; // Forward declaration of the my_service_t type.
typedef struct my_service_s my_service_t;

/**@brief Function for initializing the Service.
 *
 * @param[out]  p_cus       Service structure. This structure will have to be supplied by
 *                          the application. It will be initialized by this function, and will later
 *                          be used to identify this particular service instance.
 * @param[in]   p_cus_init  Information needed to initialize the service.
 *
 * @return      NRF_SUCCESS on successful initialization of service, otherwise an error code.
 */
uint32_t my_service_init(my_service_t *p_cus, const my_service_init_t *p_cus_init);

/**@brief Function for handling the Application's BLE Stack events.
 *
 * @details Handles all events from the BLE stack of interest to the Service.
 *
 * @param[in]   p_ble_evt  Event received from the BLE stack.
 * @param[in]   p_context  Custom Service structure.
 */
void ble_cus_on_ble_evt(ble_evt_t const *p_ble_evt, void *p_context);

void my_service_loop();
#endif /* MY_SERVICE_H_ */