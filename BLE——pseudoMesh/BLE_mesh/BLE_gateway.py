import threading
import queue
import requests
import json
import copy
from smbus2 import SMBus
from bmp280 import BMP280
import time
import subprocess
from bluepy.btle import Scanner, DefaultDelegate

raspi_id = 5

data_lock = threading.Lock()

target_macs = {
    "AA:AA:AA:AA:AA:AA",
    "B8:27:EB:C7:58:11",
    "B8:27:EB:2C:69:C7",
    "B8:27:EB:F9:14:5A",
    "B8:27:EB:94:48:58"
}

ACCESS_TOKEN = "WkZASQU06aDnTxwugCa2"  # 令牌，若要修改
# ACCESS_TOKEN_2 = "olbTWTFpzOX2YE6s8UfA"
# ACCESS_TOKEN_3 = "expkkdfk7xzydzgz0nyi"
# ACCESS_TOKEN_4 = "Nk1PY2kRTPQayocbhb1c"
# ACCESS_TOKEN_5 = "bftH7ComBcI0MIxWKv1W"

THINGSBOARD_HOST = "https://eu.thingsboard.cloud"

temperature_mapping = {
    "1": "temperature_1",
    "2": "temperature_2",
    "3": "temperature_3",
    "4": "temperature_4",
    "5": "temperature_5"
}

rssi_mapping = {
    "1": "rssi_1",
    "2": "rssi_2",
    "3": "rssi_3",
    "4": "rssi_4",
    "5": "rssi_5"
}

initial_data = [
    [1, 0.0, 0],
    [2, 0.0, 0],
    [3, 0.0, 0],
    [4, 0.0, 0],
    [5, 0.0, 0]
]

current_data = copy.deepcopy(initial_data)

def update_cloud():
    # 生成 TELEMETRY_URL
    TELEMETRY_URL = f"{THINGSBOARD_HOST}/api/v1/{ACCESS_TOKEN}/telemetry"

    while True:
        # 等待 1 分钟（60 秒）
        time.sleep(30)
        
        # 加锁，保护共享数据
        with data_lock:
            global current_data
            print("current_data: [ID, temperature, hop]\n", current_data)

            # 根据 raspi_id 获取对应的 ACCESS_TOKEN
            for item in current_data:
                if str(item[0]) in temperature_mapping:
                    data_temperature = {temperature_mapping[str(item[0])]: item[1]}

                    # 发送数据到 ThingsBoard
                    headers = {"Content-Type": "application/json"}
                    response = requests.post(TELEMETRY_URL, data= json.dumps(data_temperature), headers=headers)
                    # 仅检查响应
#                     if response.status_code == 200:
#                         print("Data sent successfully:", data_temperature)
#                     else:
#                         print("Failed to send data. Status code:", response.status_code)

                else:
                    print(f"Unknown raspi_id: {raspi_id}")

            current_data = copy.deepcopy(initial_data)
            print("    !!!Data updated and reseted!!!    \n")


def local_update(f_msg, sensor_msg):
    while True:

        if not sensor_msg.empty():
            temperature = sensor_msg.get()
            current_data[raspi_id - 1] = [raspi_id, temperature, 5]
        if not f_msg.empty():
            hex_data = f_msg.get()

            pi_id_hex = hex_data[:2]  # '01'
            temp_int_hex = hex_data[2:4]  # '19'
            temp_dec_hex = hex_data[4:6]  # '46'
            ttl_hex = hex_data[6:8]  # '05'

            temp_int = int(temp_int_hex, 16)  # 25
            temp_dec = int(temp_dec_hex, 16)  # 70

            pi_id = int(pi_id_hex, 16)  # 1
            temp = temp_int + temp_dec / 100  # 25.70
            ttl = int(ttl_hex, 16)  # 5

            for item in current_data:
                if item[0] == pi_id:
                    if ttl >= item[2]:
                        item[1] = temp
                        item[2] = ttl
                    break  # 找到对应项后退出循环


def sensor(sensor_msg):
    # Initialise the BMP280
    bus = SMBus(1)
    bmp280 = BMP280(i2c_dev=bus)
    print("BMP280 OK")

    while True:
        # 获取当前温度
        temperature = round(bmp280.get_temperature(), 2)

        sensor_msg.put(temperature)
        time.sleep(5)


class ScanDelegate(DefaultDelegate):
    def __init__(self):
        DefaultDelegate.__init__(self)
        self.hex_data = None

    def handleDiscovery(self, dev, isNewDev, isNewData):
        if dev.addr.lower() in {mac.lower() for mac in target_macs}:

            # 查找 (255, 'Manufacturer', '01194605')
            target_tuple = next((item for item in dev.getScanData() if item[0] == 255 and item[1] == 'Manufacturer'), None)

            if target_tuple:
                self.hex_data = target_tuple[2]
                # 提取出 '7a7377696e62'
                hex_data = target_tuple[2]
                print(f"Device_Addr: {dev.addr} | RSSI: {dev.rssi} dBm | Data: {hex_data}")

            else:
                print("未找到指定的元组")


def ble_scan(f_msg):
    scan_delegate = ScanDelegate()
    scanner = Scanner().withDelegate(scan_delegate)
    print(f"Start sacning....")
    try:
        while True:
            scanner.scan(0.8)  # 每次扫描5秒（非阻塞）
            if scan_delegate.hex_data is not None:
                f_msg.put(scan_delegate.hex_data)
                # 重置值以便下次扫描
                scan_delegate.hex_data = None
    except KeyboardInterrupt:
        print("\n扫描已停止")


def main():
    subprocess.run(['sudo', 'hciconfig', 'hci0', 'down'])
    time.sleep(1)
    subprocess.run(['sudo', 'hciconfig', 'hci0', 'up'])

    sensor_msg = queue.Queue()
    f_msg = queue.Queue()

    sensor_thread = threading.Thread(target=sensor, args=(sensor_msg,))
    sensor_thread.start()

    ble_scan_thread = threading.Thread(target=ble_scan, args=(f_msg,))
    ble_scan_thread.start()

    local_update_thread = threading.Thread(target=local_update, args=(f_msg,sensor_msg))
    local_update_thread.start()
    
    update_cloud_thread = threading.Thread(target=update_cloud, args=())
    update_cloud_thread.start()


if __name__ == "__main__":
    main()
