import base64

# Base64 encoded string
base64_encoded = "SLjQJIwRs99UVHre5T1FwXl3Mnct9wNzrbfmG2UGdYE="

# Decode the Base64 string to bytes
decoded_bytes = base64.b64decode(base64_encoded)

# Convert bytes to hex
hex_representation = decoded_bytes.hex()

print(hex_representation)
