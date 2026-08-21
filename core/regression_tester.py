#!/usr/bin/env python3
import os
import glob
import importlib
import inspect
import pygame
import config
from effects.base import Effect
from effects import MODES

def test_registry_order():
    """Ensure Spectrum and Waterfall are the last two entries."""
    print("Checking mode registry order...")
    names = [m[0] for m in MODES]
    assert names[-1] == "Waterfall", f"Waterfall must be last, found {names[-1]}"
    assert names[-2] == "Spectrum", f"Spectrum must be second-to-last, found {names[-2]}"
    print("  ✅ Order OK")

def test_all_effects_inherit_base():
    """Verify every registered effect class inherits from Effect."""
    print("Checking effect inheritance...")
    for name, VisCls in MODES:
        assert issubclass(VisCls, Effect), f"Effect '{name}' does not inherit from Effect base class"
    print("  ✅ Inheritance OK")

def test_all_files_registered():
    """Fail if there are python files in effects/ that are not in MODES."""
    print("Checking for unregistered effect files...")
    registered_classes = {m[1].__name__ for m in MODES}
    # Some classes might be imported with aliases or be helper files
    
    core_dir = os.path.dirname(os.path.abspath(__file__))
    effects_dir = os.path.join(os.path.dirname(core_dir), "effects")
    files = glob.glob(os.path.join(effects_dir, "*.py"))
    unregistered = []
    for f in files:
        basename = os.path.basename(f)
        if basename.startswith("__") or basename in ("base.py", "utils.py", "palette.py"):
            continue
            
        # Check if the class inside is registered
        module_name = f"effects.{basename[:-3]}"
        try:
            module = importlib.import_module(module_name)
        except ImportError as e:
            print(f"  ❌ Failed to import {module_name}: {e}")
            unregistered.append(module_name)
            continue

        for name, obj in inspect.getmembers(module, inspect.isclass):
            if obj.__module__ == module_name and issubclass(obj, Effect) and obj is not Effect:
                if obj.__name__ not in registered_classes:
                    print(f"  ❌ Class '{obj.__name__}' in {basename} is not registered in MODES")
                    unregistered.append(f"{basename}:{obj.__name__}")
    
    if unregistered:
        raise AssertionError(f"Unregistered or unimportable effect modules found: {unregistered}")
    print("  ✅ Scan complete")

def run_all_tests():
    # Setup pygame for instantiation tests
    os.environ['SDL_VIDEODRIVER'] = 'dummy'
    pygame.init()
    pygame.display.set_mode((1, 1))
    config.WIDTH, config.HEIGHT = 800, 600
    config._INITIALIZED = True
    
    try:
        test_registry_order()
        test_all_effects_inherit_base()
        test_all_files_registered()
        print("\n✅ All regression checks PASSED")
    except AssertionError as e:
        print(f"\n❌ REGRESSION FAILURE: {e}")
        exit(1)
    except Exception as e:
        print(f"\n❌ ERROR during testing: {e}")
        exit(1)

if __name__ == "__main__":
    run_all_tests()
