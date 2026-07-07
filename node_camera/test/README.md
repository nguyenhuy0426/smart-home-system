# Camera protocol host tests

The tests exercise RTSP request rejection, SDP identity metadata, session timeout,
RFC 2435 JPEG fragmentation/header state, malformed JPEG rejection, constant-time
authorization comparison, and bounded provisioning parsing without camera hardware.

Run with:

```sh
cmake -S test -B /tmp/node-camera-tests
cmake --build /tmp/node-camera-tests
ctest --test-dir /tmp/node-camera-tests --output-on-failure
```
