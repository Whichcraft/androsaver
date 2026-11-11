import contextlib
import io
import os
import unittest
from unittest import mock

from core import regression_tester as runner


class RegressionRunnerTests(unittest.TestCase):
    def test_runner_restores_temporary_state_on_success_and_failure(self):
        for failure in (None, AssertionError('bad registry'), RuntimeError('import failed')):
            for existing_driver in (None, 'existing-driver'):
                with self.subTest(failure=failure, driver=existing_driver), contextlib.ExitStack() as stack:
                    stack.enter_context(mock.patch.dict(os.environ))
                    if existing_driver is None:
                        os.environ.pop('SDL_VIDEODRIVER', None)
                    else:
                        os.environ['SDL_VIDEODRIVER'] = existing_driver
                    stack.enter_context(mock.patch.object(runner.pygame, 'init'))
                    stack.enter_context(mock.patch.object(runner.pygame.display, 'set_mode'))
                    quit_sdl = stack.enter_context(mock.patch.object(runner.pygame, 'quit'))
                    stack.enter_context(mock.patch.object(runner, 'test_registry_order', side_effect=failure))
                    stack.enter_context(mock.patch.object(runner, 'test_all_effects_inherit_base'))
                    stack.enter_context(mock.patch.object(runner, 'test_all_files_registered'))
                    with contextlib.redirect_stdout(io.StringIO()):
                        if failure:
                            with self.assertRaises(SystemExit) as caught:
                                runner.run_all_tests()
                            self.assertEqual(caught.exception.code, 1)
                        else:
                            runner.run_all_tests()
                    self.assertEqual(os.environ.get('SDL_VIDEODRIVER'), existing_driver)
                    quit_sdl.assert_called_once_with()
