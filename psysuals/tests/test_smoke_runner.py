import contextlib
import io
import os
import unittest
from unittest import mock

import smoke_test
from core import regression_tester


class SmokeRunnerTests(unittest.TestCase):
    def test_smoke_runner_restores_state_on_success_and_effect_failure(self):
        for failing in (False, True):
            with self.subTest(failing=failing), contextlib.ExitStack() as stack:
                stack.enter_context(mock.patch.dict(os.environ))
                os.environ.pop('SDL_VIDEODRIVER', None)
                stack.enter_context(mock.patch.object(smoke_test, 'test_imports', return_value=[]))
                def effects(modes):
                    if failing:
                        raise SystemExit(1)
                stack.enter_context(mock.patch.object(smoke_test, 'test_effects', side_effect=effects))
                for name in ('test_registry_order', 'test_all_effects_inherit_base', 'test_all_files_registered'):
                    stack.enter_context(mock.patch.object(regression_tester, name))
                quit_sdl = stack.enter_context(mock.patch.object(smoke_test.pygame, 'quit'))
                with contextlib.redirect_stdout(io.StringIO()):
                    if failing:
                        with self.assertRaises(SystemExit) as caught:
                            smoke_test.run_smoke_tests()
                        self.assertEqual(caught.exception.code, 1)
                    else:
                        smoke_test.run_smoke_tests()
                self.assertNotIn('SDL_VIDEODRIVER', os.environ)
                quit_sdl.assert_called_once_with()
