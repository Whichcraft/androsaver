#!/usr/bin/env python3
import sys
import numpy as np
import pygame
import os

def test_imports():
    print("Checking imports...")
    try:
        import psysualizer
        import config
        from effects import MODES
        print("✅ Core imports OK")
        return MODES
    except Exception as e:
        print(f"❌ Import failed: {e}")
        sys.exit(1)

def test_effects(MODES):
    print(f"Checking {len(MODES)} effects...")
    import config
    pygame.init()
    screen = pygame.display.set_mode((320, 240))
    config.WIDTH, config.HEIGHT = screen.get_size()
    config._INITIALIZED = True
    waveform = np.zeros(config.BLOCK_SIZE, dtype=np.float32)
    fft = np.zeros(config.BLOCK_SIZE // 2, dtype=np.float32)
    
    passed = 0
    failed = 0
    for name, VisCls in MODES:
        vis = None
        try:
            vis = VisCls()
            screen.fill((0, 0, 0))
            vis.draw(screen, waveform, fft, 0.0, 0)
            print(f"  ✅ {name:12} instantiated and rendered")
            passed += 1
        except Exception as e:
            print(f"  ❌ {name:12} FAILED: {e}")
            failed += 1
        finally:
            if vis is not None and hasattr(vis, "release"):
                vis.release()
            
    print(f"\nResults: {passed} passed, {failed} failed")
    if failed > 0:
        sys.exit(1)

def run_smoke_tests():
    os.environ['SDL_VIDEODRIVER'] = 'dummy'
    modes = test_imports()
    test_effects(modes)
    
    print("\nRunning regression checks...")
    from core.regression_tester import test_registry_order, test_all_effects_inherit_base, test_all_files_registered
    test_registry_order()
    test_all_effects_inherit_base()
    test_all_files_registered()
    
    print("\n✅ All smoke and regression tests PASSED")

if __name__ == "__main__":
    run_smoke_tests()
