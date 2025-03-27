import bluetooth
import threading
import sys
import queue
from smbus2 import SMBus
from bmp280 import BMP280
import time
import subprocess

def lower_master(client_sock, address, q_msg):
    try:
        print(f"Connected to {address}")
        while True:
            data = client_sock.recv(1024)
            if not data:
                break
            print(f"Received from {address}: {data.decode()}")
            q_msg.put(data)
    except Exception as e:
        print(f"Error with {address}: {e}")
    finally:
        client_sock.close()
        print(f"Connection closed: {address}")


def forwarding(upper_master_addr, upper_master_port, q_msg):
    upper_sock = bluetooth.BluetoothSocket(bluetooth.L2CAP)

    print("Trying to connect to {} on PSM 0x{}...".format(upper_master_addr, upper_master_port))
    upper_sock.connect((upper_master_addr, upper_master_port))
    print("Connected to upper node.")
    # q_sock.put(upper_sock)

    while True:
        lower_msg = q_msg.get()
        if lower_msg:
            upper_sock.send(lower_msg)
        else:
            break


def sensor(upper_master_addr, raspi_ID, q_msg):
    # Initialise the BMP280
    bus = SMBus(1)
    bmp280 = BMP280(i2c_dev=bus)
    print("BMP280 OK")

    while True:
        # 获取当前温度
        temperature = round(bmp280.get_temperature(), 2)

        # 获取当前时间
        timestamp = time.time()  # 获取当前时间戳
        local_time = time.localtime(timestamp)  # 转换为本地时间
        formatted_time = time.strftime("%Y-%m-%d %H:%M:%S", local_time)  # 格式化输出

        # get rssi
        output = subprocess.check_output(['hcitool', 'rssi', upper_master_addr])
        output_str = output.decode('utf-8').strip()  # 去掉换行符和多余空格
        parts = output_str.split()
        rssi_str = parts[-1]

        message = str(raspi_ID) + "; " + str(temperature) + "; " + rssi_str + "; " + formatted_time
        q_msg.put(message)

        time.sleep(5)



def main():

    upper_master_addr = "B8:27:EB:F9:14:5A"  # Raspi 4
    upper_master_port = 0x1001
    raspi_ID = 1

    q_msg = queue.Queue()

    sensor_thread = threading.Thread(target=sensor, args=(upper_master_addr, raspi_ID, q_msg))
    sensor_thread.start()

    forwarding_thread = threading.Thread(target=forwarding,
                                         args=(upper_master_addr, upper_master_port, q_msg))
    forwarding_thread.start()

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
            client_thread = threading.Thread(target=lower_master, args=(client_sock, address, q_msg))
            client_thread.start()
    except KeyboardInterrupt:
        print("Shutting down...")
    finally:
        server_sock.close()


if __name__ == "__main__":
    main()
