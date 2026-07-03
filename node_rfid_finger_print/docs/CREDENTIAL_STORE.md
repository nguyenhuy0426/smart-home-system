# Access credential store

The firmware fails closed unless the `access_auth` NVS namespace contains all of the following:

| NVS key | Type | Meaning |
|---|---|---|
| `hmac_key` | blob, exactly 32 bytes | Per-home random HMAC-SHA256 key; an all-zero key is rejected. |
| `rfid_count` | `u8`, 0–32 | Number of RFID allowlist hashes. |
| `fp_count` | `u8`, 0–32 | Number of fingerprint allowlist hashes. |
| `rfid00` … `rfid31` | string | `sha256:` plus 64 lowercase hexadecimal HMAC characters. |
| `fp00` … `fp31` | string | Same hash format for fingerprint page IDs. |

RFID hashes are HMAC-SHA256 over the validated 4/7/10-byte ISO14443A UID. Fingerprint hashes are
HMAC-SHA256 over the two-byte big-endian TZM1026 page ID. Raw UIDs and fingerprint templates are not
stored or transmitted.

The key and hashes must be provisioned by a trusted offline NVS image or a future authenticated
gateway enrollment flow. The firmware deliberately has no default key, default credential, serial
backdoor, or automatic enrollment path. If the namespace, key, counts, or any entry is malformed,
all credentials are denied and the relay stays OFF.

Production provisioning must enable encrypted NVS/flash encryption and must never commit a generated
credential CSV or key material to source control.
