import threading
import queue
from smbus2 import SMBus
from bmp280 import BMP280
import time
import subprocess
from bluepy.btle import Scanner, DefaultDelegate

target_macs = {
    "AA:AA:AA:AA:AA:AA",
    "B8:27:EB:C7:58:11",
    "B8:27:EB:2C:69:C7",
    "B8:27:EB:F9:14:5A",
    "B8:27:EB:94:48:58"
}

raspi_id = 3  # 当前设备的ID

initial_bitmask = 1 << (raspi_id-1)
initial_bitmask_hex = f"{initial_bitmask:02x}"

def ble_advertising(raspi_id, sensor_msg, f_msg):
    print("Advertising")
    while True:
        if not sensor_msg.empty():
            temperature = sensor_msg.get()

            raspi_id_hex = f"{raspi_id:02x}"
            temp_int = int(temperature // 1)
            temp_int_hex = f"{temp_int:02x}"
            temp_dec = int((temperature * 100) % 100)
            temp_dec_hex = f"{temp_dec:02x}"
            def_ttl = 5
            def_ttl_hex = f"{def_ttl:02x}"

            # 设置广告数据，包含位掩码
            subprocess.run(['sudo', 'hcitool', '-i', 'hci0', 'cmd', '0x08', '0x0008',
                            '0A',  # 广告数据总长度10字节
                            '02', '01', '1A',  # Flags字段
                            '06', 'ff', raspi_id_hex, temp_int_hex, temp_dec_hex, def_ttl_hex, initial_bitmask_hex],
                           stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL
                           )

            subprocess.run(['sudo', 'hciconfig', 'hci0', 'leadv', '0'])
            time.sleep(0.1)
            subprocess.run(['sudo', 'hciconfig', 'hci0', 'noleadv'])

        if not f_msg.empty():
            length = f_msg.qsize()
            print(f"队列长度: {length}")
            
            hex_data = f_msg.get()
            if len(hex_data) != 10:  # 确保数据长度正确
                continue

            # 解析接收到的数据
            raspi_id_received = hex_data[0:2]
            temp_int_hex = hex_data[2:4]
            temp_dec_hex = hex_data[4:6]
            ttl_hex = hex_data[6:8]
            bitmask_hex = hex_data[8:10]

            ttl = int(ttl_hex, 16)
            bitmask = int(bitmask_hex, 16)

            # 检查当前设备是否已处理该消息
            current_bit = raspi_id - 1  # 假设ID从1开始对应bit0
            if (bitmask & (1 << current_bit)) != 0:
                continue  # 已处理过，不转发

            if ttl <= 0:
                continue  # TTL过期，不转发

            # 更新位掩码和TTL
            new_bitmask = bitmask | (1 << current_bit)
            new_bitmask_hex = f"{new_bitmask:02x}"
            new_ttl = ttl - 1
            new_ttl_hex = f"{new_ttl:02x}"

            # 构造新的广告数据
            subprocess.run(['sudo', 'hcitool', '-i', 'hci0', 'cmd', '0x08', '0x0008',
                           '0A',  # 广告数据总长度10字节
                           '02', '01', '1A',  # Flags字段
                           '06', 'ff', raspi_id_received, temp_int_hex, temp_dec_hex, new_ttl_hex, new_bitmask_hex],
                          stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL
                          )

            subprocess.run(['sudo', 'hciconfig', 'hci0', 'leadv', '0'])
            time.sleep(0.1)
            subprocess.run(['sudo', 'hciconfig', 'hci0', 'noleadv'])
            print(f"  Forwarded: ID = {raspi_id_received} || TTL = {new_ttl} || Bitmask = {new_bitmask_hex}")

def sensor(sensor_msg):
    bus = SMBus(1)
    bmp280 = BMP280(i2c_dev=bus)
    print("BMP280 OK")

    while True:
        temperature = round(bmp280.get_temperature(), 2)
        sensor_msg.put(temperature)
        time.sleep(1)

class ScanDelegate(DefaultDelegate):
    def __init__(self):
        DefaultDelegate.__init__(self)
        self.hex_data = None

    def handleDiscovery(self, dev, isNewDev, isNewData):
        if dev.addr.lower() in {mac.lower() for mac in target_macs}:
            target_tuple = next((item for item in dev.getScanData() if item[0] == 255 and item[1] == 'Manufacturer'), None)
            if target_tuple:
                self.hex_data = target_tuple[2]
                print(f"Device_Addr: {dev.addr} | Data {self.hex_data}")

def ble_scan(f_msg):
    scan_delegate = ScanDelegate()
    scanner = Scanner().withDelegate(scan_delegate)
    print("Scanning...")
    try:
        while True:
            scanner.scan(0.5)
            if scan_delegate.hex_data and len(scan_delegate.hex_data) == 10:
                f_msg.put(scan_delegate.hex_data)
                scan_delegate.hex_data = None
    except KeyboardInterrupt:
        print("Scan stopped")

def main():
    subprocess.run(['sudo', 'hciconfig', 'hci0', 'down'])
    time.sleep(1)
    subprocess.run(['sudo', 'hciconfig', 'hci0', 'up'])

    sensor_msg = queue.Queue()
    f_msg = queue.Queue()

    sensor_thread = threading.Thread(target=sensor, args=(sensor_msg,))
    ble_adv_thread = threading.Thread(target=ble_advertising, args=(raspi_id, sensor_msg, f_msg))
    ble_scan_thread = threading.Thread(target=ble_scan, args=(f_msg,))

    sensor_thread.start()
    ble_adv_thread.start()
    ble_scan_thread.start()

if __name__ == "__main__":
    main()