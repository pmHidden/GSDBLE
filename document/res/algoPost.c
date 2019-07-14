// Es sollen mindestens 1 und maximal 227 Pakete aus der FIFO geholt werden
uint16_t packets_till_fifo_full_or_buffer_cleared = MAX(1u, MIN(FIFO_MAX_FILL_PACKETS, packets_generated_per_connection_interval(speed)));
uint16_t bytes_till_fifo_full_or_buffer_cleared = BYTES_PER_DATA * DATAS_PER_PACKET * packets_till_fifo_full_or_buffer_cleared;

// Setze Threshold. >> 1 wegen verschiedener Einheiten
LSM_ERROR_CHECK(lsm6dsl_fifo_watermark_set(dev_ctx, bytes_till_fifo_full_or_buffer_cleared >> 1));
// Pro Durchgang können maximal 14 Pakete geholt werden
packets_per_transaction = MIN(packets_till_fifo_full_or_buffer_cleared, MAX_PACKETS_PER_TRANSACTION);
// Setze Geschwindigkeit. + 2 wegen verschiedener Einheiten
LSM_ERROR_CHECK(lsm6dsl_fifo_data_rate_set(dev_ctx, speed + 2));