"""Force the app upload offset to the factory partition at 0x20000.

The espressif32 platform builder computes ESP32_APP_OFFSET twice:
  1. frameworks/espidf.py asks IDF's parttool for the default boot
     partition (factory -> 0x20000) -- correct.
  2. builder/main.py:_parse_partitions() re-derives it from partitions.csv
     with a naive parser that picks the ota_0 row and mis-handles blank
     offsets (-> 0x210000). It runs as a PreAction of "checkprogsize",
     i.e. AFTER every config script, so it clobbers value (1) and
     board_upload.offset_address alike.
The board then boot-loops: the flashed partition table has no app
partition at 0x210000. This pre-upload action runs after the clobber and
restores the offset the flashed partition table actually boots from,
taken from IDF's own flasher_args.json when available.
"""

import json
import os

Import("env")  # noqa: F821  (SCons construction environment)

FACTORY_APP_OFFSET = "0x20000"


def _offset_from_flasher_args(build_dir):
    args_path = os.path.join(build_dir, "flasher_args.json")
    try:
        with open(args_path, encoding="utf-8") as fp:
            flasher_args = json.load(fp)
        offset = flasher_args["app"]["offset"]
        return offset if isinstance(offset, str) and offset.startswith("0x") \
            else None
    except (OSError, ValueError, KeyError):
        return None


def _force_app_offset(source, target, env):  # noqa: ARG001
    offset = _offset_from_flasher_args(env.subst("$BUILD_DIR")) \
        or FACTORY_APP_OFFSET
    previous = env.subst("$ESP32_APP_OFFSET")
    if previous != offset:
        print("fix_app_upload_offset: ESP32_APP_OFFSET %s -> %s "
              "(factory app partition)" % (previous or "<unset>", offset))
    env.Replace(ESP32_APP_OFFSET=offset)


env.AddPreAction("upload", _force_app_offset)
