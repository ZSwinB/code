import sys
import bluetooth
import time
from smbus2 import SMBus
from bmp280 import BMP280
import subprocess

sock = bluetooth.BluetoothSocket(bluetooth.L2CAP)

raspi_ID = 1
# bt_addr = "FC:A9:F5:8A:87:0C"
bt_addr = "B8:27:EB:2C:69:C7"
port = 0x1001

print("Trying to connect to {} on PSM 0x{}...".format(bt_addr, port))
sock.connect((bt_addr, port))
print("Connected.")

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
    output = subprocess.check_output(['hcitool', 'rssi', bt_addr])
    output_str = output.decode('utf-8').strip()  # 去掉换行符和多余空格
    parts = output_str.split()
    rssi_str = parts[-1]

    message = str(raspi_ID) + "; " + str(temperature) + "; " + rssi_str + "; " + formatted_time
    sock.send(message)
    print("Data send: " + message)

    time.sleep(5)

sock.close()
