
# 这个项目可以实现蓝牙的伪mesh网络，或者使用传统的scatternet。实现方法是将数据信息通过生产商信息的保留字段传输，通过带有信息返回检测（一个设备不会重复发两条相同的信息）的广播进行组网。经过树莓派4b的实现效果良好。
Sensor network for office premises(with BLE)

## Scatter Network

- This Scatter Network is implemented by Classical Bluetooth. 
- All nodes can read temperature data from the BME280 sensor.

### Master Node
- The master node should be the first to run, waiting for connections from relay nodes and slave nodes.
- The master node is responsible for pushing collected data to the cloud platform.

### Relay Node
- A relay node should run after the master node. 
- It first connects to the master node or relay node. and then waits for connections from other relay nodes or slave nodes.
- Once it receives data, it forwards the data to the upper master node or relay node.
- The `upper_master_addr` should be updated to the BD address of the next upper node.

### Slave Node
- Reads data from the BME280 sensor and sends it to the upper node.
- The `bt_addr` should be updated to the BD address of the next upper node.

---

##  BLE Mesh Network

### BLE Node
Run `BLE_node.py` with `sudo python3 BLE_node.py` on all nodes except the gateway (primary node). Note that the `target_macs` should be changed to the BD Addresses in your network, and change `raspi_id = 5` to your planned ID, e.g. `raspi_id = 2`.

### BLE Gateway (Primary Node)
Run `BLE_gateway.py` with `sudo python3 BLE_gateway.py`. Remember to change `raspi_id` to your planned ID, e.g. `raspi_id = 2`.
