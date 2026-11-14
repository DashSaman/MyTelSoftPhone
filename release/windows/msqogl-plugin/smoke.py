"""Exercise real SDK plugin discovery/creation without SIP or audio hardware."""
import ctypes as C
import argparse
import json
import os
from pathlib import Path
import shutil
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--runtime", type=Path, help="Packaged directory containing mediastreamer2.dll and Qt6*.dll")
parser.add_argument("--plugin-dir", type=Path, help="Explicit module directory for build verification")
args = parser.parse_args()
sdk = Path(r"C:\Users\HajSaman\Documents\GLM\SoftPhone\win\sdk\linphone-sdk\win64")
runtime = args.runtime.resolve() if args.runtime else sdk / "bin"
qt = runtime if args.runtime else Path(r"C:\Qt\6.10.0\msvc2022_64\bin")
handles = [os.add_dll_directory(str(p)) for p in {runtime, qt}]
os.environ["PATH"] = str(qt) + os.pathsep + str(runtime) + os.pathsep + os.environ["PATH"]
ms = C.CDLL(str(runtime / "mediastreamer2.dll"))

def api(name, result, *args):
    fn = getattr(ms, name)
    fn.restype = result
    fn.argtypes = args
    return fn

new = api("ms_factory_new", C.c_void_p)
destroy = api("ms_factory_destroy", None, C.c_void_p)
load = api("ms_factory_load_plugins", C.c_int, C.c_void_p, C.c_char_p)
lookup = api("ms_factory_lookup_filter_by_name", C.c_void_p, C.c_void_p, C.c_char_p)
create = api("ms_factory_create_filter_from_name", C.c_void_p, C.c_void_p, C.c_char_p)
free_filter = api("ms_filter_destroy", None, C.c_void_p)
factory = new()
if not factory:
    raise RuntimeError("Could not create media factory")
isolated = tempfile.TemporaryDirectory(prefix="mytell-msqogl-")
try:
    directory = args.plugin_dir
    if args.runtime:
        directory = runtime / "lib/mediastreamer/plugins"
    # Use the actual SDK directory-discovery function on this exact deployed
    # module, isolated from audio plugins that require hardware/VoIP managers.
    if directory:
        shutil.copy2(directory / "libmsqogl.dll", isolated.name)
    count = load(factory, os.fsencode(isolated.name))
    found = bool(lookup(factory, b"MSQOGL"))
    print(json.dumps({"plugins_loaded": count, "MSQOGL_registered": found}), flush=True)
    if not found:
        raise SystemExit(1)
    # Init/uninit validates the actual SDK filter ABI, without a GL window.
    for _ in range(50):
        instance = create(factory, b"MSQOGL")
        if not instance:
            raise RuntimeError("Registered renderer could not be instantiated")
        free_filter(instance)
    print("PASS: MSQOGL discovery plus 50 real filter create/destroy cycles", flush=True)
finally:
    destroy(factory)
    isolated.cleanup()
