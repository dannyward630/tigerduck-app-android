import importlib.util
import pathlib
import sys
import tempfile
import unittest

_SPEC = importlib.util.spec_from_file_location(
    "sync_localizations", pathlib.Path(__file__).with_name("sync_localizations.py")
)
sync = importlib.util.module_from_spec(_SPEC)
sys.modules[_SPEC.name] = sync
_SPEC.loader.exec_module(sync)


class SyncIntoResDirTest(unittest.TestCase):
    """The Gradle build's call: strings into a resource directory of its own, from app-translation's source."""

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.res = pathlib.Path(self._tmp.name) / "res"
        self.addCleanup(self._tmp.cleanup)

    def test_strings_land_in_the_res_dir(self):
        self.assertEqual(0, sync.main(["--res-dir", str(self.res)]))

        self.assertTrue((self.res / "values" / "strings.xml").exists())
        self.assertTrue((self.res / "values-zh-rTW" / "strings.xml").exists())
        self.assertFalse((self.res / "xml").exists())

    def test_the_app_also_gets_its_locale_list(self):
        self.assertEqual(0, sync.main(["--res-dir", str(self.res), "--locales-config"]))

        config = (self.res / "xml" / "locales_config.xml").read_text(encoding="utf-8")
        self.assertIn('<locale android:name="zh-TW" />', config)
        self.assertIn('<locale android:name="en" />', config)

    def test_nothing_is_written_into_the_source_tree(self):
        sync.main(["--res-dir", str(self.res), "--locales-config"])

        self.assertFalse((sync.ROOT / "app" / "src" / "main" / "res" / "xml" / "locales_config.xml").exists())
        self.assertFalse((sync.ROOT / "app-translation" / "generated" / "android").exists())


if __name__ == "__main__":
    unittest.main()
