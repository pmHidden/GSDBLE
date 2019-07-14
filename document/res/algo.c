// Pakete die in einem Connection Interval generiert werden
#define packets_generated_per_connection_interval(speed_param) ((uint16_t)(connection_interval_ms / (1000.0f / ((float)(25 * (0b1 << speed_param))))))

bool didGoLowerOnceAndNowOk = false;
while (packets_generated_per_connection_interval(speed) > buffer_size) {
  if (speed > 0) {
    didGoLowerOnceAndNowOk = true;
    speed--;
  } else {
    didGoLowerOnceAndNowOk = false;
    break;
  }
}
if (didGoLowerOnceAndNowOk) speed++;