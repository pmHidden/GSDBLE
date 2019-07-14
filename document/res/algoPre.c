// ein Paket besteht aus 3 DATAs: Beschleunigungssensor, Gyrosensor, Zeitstempel
#define DATAS_PER_PACKET 3u
// jede DATA hat 3 Achsen mit je 2 Byte
#define BYTES_PER_DATA 6u
// die FIFO ist 4096 Byte groß. Bei 18 Byte pro Packet können 4086 Byte genutzt werden
#define FIFO_MAX_FILL_BYTES (4096u - ((4096u % (BYTES_PER_DATA * DATAS_PER_PACKET))))
// Das sind 227 Pakete in der FIFO
#define FIFO_MAX_FILL_PACKETS (FIFO_MAX_FILL_BYTES / BYTES_PER_DATA / DATAS_PER_PACKET)
// Per SPI können 254 Byte pro Durchgang ausgelesen werden, also 14 Pakete
#define MAX_PACKETS_PER_TRANSACTION ((0xFFu - 1u) / (BYTES_PER_DATA * DATAS_PER_PACKET))

// so viele Pakete werden bei jedem Durchlauf später ausgelesen. Ein Output von lsm_get_data_speed_set()
static uint16_t packets_per_transaction = MAX_PACKETS_PER_TRANSACTION;
// die Größe des TX-Buffers wird beim Initialisieren gesetzt
static uint16_t buffer_size;