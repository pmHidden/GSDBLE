int32_t lsm6dsl_fifo_raw_data_get(lsm6dsl_ctx_t *ctx, uint8_t *buffer, uint8_t len) {
  int32_t ret;
  ret = lsm6dsl_read_reg(ctx, LSM6DSL_FIFO_DATA_OUT_L, buffer, len);
  return ret;
}