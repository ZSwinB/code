

import time
import bluetooth
import threading
import requests
import json
import queue
from smbus2 import SMBus
from bmp280 import BMP280
import subprocess

def update_cloud(q_msg):
    ACCESS_TOKEN = "WkZASQU06aDnTxwugCa2"  # 令牌，若要修改
    # ACCESS_TOKEN_2 = "olbTWTFpzOX2YE6s8UfA"
    # ACCESS_TOKEN_3 = "expkkdfk7xzydzgz0nyi"
    # ACCESS_TOKEN_4 = "Nk1PY2kRTPQayocbhb1c"
    # ACCESS_TOKEN_5 = "bftH7ComBcI0MIxWKv1W"

    THINGSBOARD_HOST = "https://eu.thingsboard.cloud"

    # TOKEN_MAPPING = {
    #     "1": ACCESS_TOKEN_1,
    #     "2": ACCESS_TOKEN_2,
    #     "3": ACCESS_TOKEN_3,
    #     "4": ACCESS_TOKEN_4,
    #     "5": ACCESS_TOKEN_5
    # }

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

    while True:
        message = q_msg.get()
        if not message:
            continue

        parts = message.split(';')
        raspi_id = parts[0]

        # data_temperature = {"temperature": float(parts[1])}
        # data_rssi    = {"RSSI": int(parts[2])}

        # 根据 raspi_id 获取对应的 ACCESS_TOKEN
        if raspi_id in temperature_mapping:
            # ACCESS_TOKEN = TOKEN_MAPPING[raspi_id]

            data_temperature = {temperature_mapping[raspi_id]: float(parts[1])}
            data_rssi    = {rssi_mapping[raspi_id]: int(parts[2])}

            # 生成 TELEMETRY_URL
            TELEMETRY_URL = f"{THINGSBOARD_HOST}/api/v1/{ACCESS_TOKEN}/telemetry"

            # 发送数据到 ThingsBoard
            headers = {"Content-Type": "application/json"}
            response = requests.post(TELEMETRY_URL, data=json.dumps(data_temperature), headers=headers)
            response = requests.post(TELEMETRY_URL, data=json.dumps(data_rssi), headers=headers)
            # 仅检查响应
            # if response.status_code == 200:
            #     print("Data sent successfully:", data_temperature)
            # else:
            #     print("Failed to send data. Status code:", response.status_code)

        else:
            print(f"Unknown raspi_id: {raspi_id}")


def lower_master(client_sock, address, q_msg):
    try:
        print(f"Connected to {address}")
        while True:
            data = client_sock.recv(1024)
            if not data:
                break
            print(f"Received from {address}: {data.decode()}")
            q_msg.put(data.decode())
    except Exception as e:
        print(f"Error with {address}: {e}")
    finally:
        client_sock.close()
        print(f"Connection closed: {address}")

def master_sensor(raspi_ID, q_msg):
    # Initialise the BMP280
    bus = SMBus(1)
    bmp280 = BMP280(i2c_dev=bus)
    # temperature = round(bmp280.get_temperature(), 2)
    print("BMP280 OK")

    while True:
        # 获取当前温度
        temperature = round(bmp280.get_temperature(), 2)

        # 获取当前时间
        timestamp = time.time()  # 获取当前时间戳
        local_time = time.localtime(timestamp)  # 转换为本地时间
        formatted_time = time.strftime("%Y-%m-%d %H:%M:%S", local_time)  # 格式化输出

        message = str(raspi_ID) + "; " + str(temperature) + "; " + "100" + "; " + formatted_time
        q_msg.put(message)
        
        subprocess.run(["bluetoothctl", "discoverable", "on"])

        time.sleep(5)

def main():
    q_msg = queue.Queue()
    raspi_ID = 4

    sensor_thread = threading.Thread(target=master_sensor, args=(raspi_ID, q_msg))
    sensor_thread.start()

    cloud_thread = threading.Thread(target=update_cloud, args=(q_msg, ))
    cloud_thread.start()

    server_sock = bluetooth.BluetoothSocket(bluetooth.L2CAP)
    server_sock.bind(("", bluetooth.PORT_ANY))
    server_sock.listen(2)  # 允许同时两个连接
    port = server_sock.getsockname()[1]
    print(f"Master listening on port {port}")

    #     # 设置蓝牙名称和可见性（可选）
    #     bluetooth.advertise_service(
    #         server_sock,
    #         "MasterDevice",
    #         service_id="00001101-0000-1000-8000-00805F9B34FB",
    #         service_classes=["00001101-0000-1000-8000-00805F9B34FB"],
    #         profiles=[bluetooth.SERIAL_PORT_PROFILE]
    #     )

    try:
        while True:
            client_sock, address = server_sock.accept()
            print(f"Accepted connection from {address}")
            # 为每个连接启动新线程
            client_thread = threading.Thread(target=lower_master, args=(client_sock, address,q_msg))
            client_thread.start()
    except KeyboardInterrupt:
        print("Shutting down...")
    finally:
        server_sock.close()


if __name__ == "__main__":
    main()


