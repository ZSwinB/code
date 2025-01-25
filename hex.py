import base64

def hex_to_base64(hex_str):
    # 将十六进制字符串转换为字节
    byte_data = bytes.fromhex(hex_str)
    # 将字节转换为Base64编码
    base64_data = base64.b64encode(byte_data).decode('utf-8')
    return base64_data

# 示例使用
hex_string = "61c295ee322e1427600c935c3eacc9949d712491ee4a69e30464b3e0cd39d25e"  # 替换为您的Hex值
base64_result = hex_to_base64(hex_string)
print(f"Base64: {base64_result}")
