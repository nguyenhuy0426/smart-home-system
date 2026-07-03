# Access-node host safety tests

These tests exercise authorization and protocol validation without actuating GPIO or requiring
RFID/fingerprint hardware.

```sh
cmake -S test -B /tmp/access-node-tests
cmake --build /tmp/access-node-tests
ctest --test-dir /tmp/access-node-tests --output-on-failure
```

Covered cases: unknown card, valid allowlisted credential, missing hardware, malformed TZM1026
length/checksum, stale TZM1026 response, and MFRC522 UID BCC/CRC validation.
