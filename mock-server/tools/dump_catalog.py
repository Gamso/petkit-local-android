"""Regenerate src/main/resources/catalog.json from a petkit-local checkout.

The mock exposes, per model, exactly the entity keys and action keys the real
server would: they are read here from petkit-local's own definitions
(`ha/categories.py`, `ha/entities/*.py`, `ha/commands.py`) rather than retyped.

Usage (from a petkit-local checkout with its requirements installed):

    python mock-server/tools/dump_catalog.py <petkit-local>/addon <git-ref> \
        mock-server/src/main/resources/catalog.json
"""
import json
import sys

sys.path.insert(0, sys.argv[1])

from petkit_local.app_api.serializers import (  # noqa: E402
    VALUE_COMPONENTS, accessory_actions, device_kind,
)
from petkit_local.devices.base import Device  # noqa: E402
from petkit_local.ha.categories import get_entities_for_device  # noqa: E402
from petkit_local.ha.commands import DESTRUCTIVE_ACTIONS, device_actions  # noqa: E402
from petkit_local.ha.entities.ble import get_ble_entities  # noqa: E402
from petkit_local.utils.const import device_display_name  # noqa: E402

MODELS = ["t3", "t4", "t5", "t6", "t7", "d4", "d4s", "d4h", "d4sh", "w7h", "w5", "ctw3", "k3"]
# Bluetooth accessories have no device type to select entities on.
BLE = {"w5", "ctw3", "k3"}


def model(codename: str) -> dict:
    device = Device(device_type=codename, petkit_id=1)
    entities = get_ble_entities(codename) if codename in BLE else get_entities_for_device(device)
    # An accessory's buttons are frames sent through its parent, not `ALL_ACTIONS`
    # entries, so the real server offers them by a rule of their own.
    actions = accessory_actions(entities) if codename in BLE else device_actions(entities)
    return {
        "type": codename,
        "model": device_display_name(codename),
        "kind": device_kind(device),
        "is_camera": bool(device.is_camera),
        "entities": [{
            "key": e.key, "name": e.name, "component": e.component,
            "value_path": e.value_path,
            "unit": e.unit or None, "device_class": e.device_class or None,
            "category": e.entity_category or None,
            "options": list(e.options) if e.options else None,
            "option_values": list(e.option_values) if e.option_values else None,
            "settable": e.is_settable,
            "min": e.min_value, "max": e.max_value, "step": e.step,
        } for e in entities if e.component in VALUE_COMPONENTS],
        # Destructive last, as the real server orders them.
        "actions": sorted(({"key": e.key, "name": e.name,
                            "destructive": e.key in DESTRUCTIVE_ACTIONS} for e in actions),
                          key=lambda a: a["destructive"]),
    }


catalog = {"source": sys.argv[2], "models": {t: model(t) for t in MODELS}}
with open(sys.argv[3], "w", encoding="utf-8", newline="\n") as f:
    json.dump(catalog, f, indent=1, ensure_ascii=False)
    f.write("\n")
