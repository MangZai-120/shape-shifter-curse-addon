import json
from pathlib import Path
import re
from tempfile import TemporaryDirectory
import unittest
from zipfile import ZipFile

from migrate_namespace_pack import convert


class NamespacePackTest(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.pack = self.root / "pack"
        self.out = self.root / "result.zip"
        self.put("pack.mcmeta", {"pack": {"pack_format": 15, "description": "test"}})

    def put(self, name, value):
        file = self.pack / name
        file.parent.mkdir(exist_ok=True, parents=True)
        file.write_bytes(value if isinstance(value, bytes) else json.dumps(value).encode())
        return file

    def test_mixed_pack(self):
        self.put("assets/my_addon/sounds.json", {"a": {"sounds": ["my_addon:a"]}})
        self.put("assets/ssc_addon/sounds.json", {"b": {"sounds": ["other:b"]}})
        self.put("assets/my_addon/lang/en_us.json", {"item.my_addon.a": "A"})
        self.put("assets/ssc_addon/lang/en_us.json", {"item.ssc_addon.b": "B"})
        self.put("assets/my_addon/ssc_form_model/origins.origin.my_addon.form_old.json",
                 {"texture": "my_addon:textures/old.png"})
        self.put("assets/orif-defaults/furs/my_addon.form_old.json", {"hidden": []})
        self.put("assets/my_addon/textures/old.png", b"\x89PNG\r\n\xff")
        self.put("data/ssca_neomagic/rules/defaults.json", {"cost": 13})
        fn = self.put("data/other/functions/test.mcfunction",
                      b"function #my_addon:f\nsay other.my_addon:keep\n")
        source_zip = self.root / "input.zip"
        with ZipFile(source_zip, "w") as archive:
            for path in self.pack.rglob("*"):
                if path.is_file():
                    archive.write(path, path.relative_to(self.pack).as_posix())
        original = source_zip.read_bytes()
        self.assertEqual(convert(source_zip, self.out), 8)
        self.assertEqual(source_zip.read_bytes(), original)
        with ZipFile(self.out) as result:
            self.assertEqual(json.loads(result.read("assets/ssc_addon/sounds.json")),
                             {"a": {"sounds": ["ssc_addon:a"]}, "b": {"sounds": ["other:b"]}})
            self.assertEqual(json.loads(result.read("assets/ssc_addon/lang/en_us.json")),
                             {"item.ssc_addon.a": "A", "item.ssc_addon.b": "B"})
            model = "assets/ssc_addon/ssc_form_model/origins.origin.ssc_addon.form_old.json"
            self.assertEqual(json.loads(result.read(model))["texture"], "ssc_addon:textures/old.png")
            self.assertIn("assets/orif-defaults/furs/ssc_addon.form_old.json", result.namelist())
            self.assertEqual(result.read("assets/ssc_addon/textures/old.png"), b"\x89PNG\r\n\xff")
            self.assertEqual(json.loads(result.read("data/ssc_addon/rules/defaults.json")), {"cost": 13})
            self.assertEqual(result.read(fn.relative_to(self.pack).as_posix()),
                             b"function #ssc_addon:f\nsay other.my_addon:keep\n")

    def test_duplicate_keys_after_rename(self):
        file = self.put("assets/ssc_addon/lang/en_us.json",
                        {"item.my_addon.a": "A", "item.ssc_addon.a": "B"})
        original = file.read_bytes()
        with self.assertRaisesRegex(ValueError, "item.ssc_addon.a"):
            convert(self.pack, self.out)
        self.assertFalse(self.out.exists())
        self.assertEqual(file.read_bytes(), original)

    def test_conflicting_files(self):
        for ns, value in (("my_addon", 2), ("ssca_neomagic", 3)):
            self.put(f"data/{ns}/rules/defaults.json", {"cost": value})
        with self.assertRaisesRegex(ValueError, "defaults.json"):
            convert(self.pack, self.out)
        self.assertFalse(self.out.exists())

    def test_overwrite_refused(self):
        self.out.write_bytes(b"keep")
        with self.assertRaises(FileExistsError):
            convert(self.pack, self.out)
        self.assertEqual(self.out.read_bytes(), b"keep")

    def test_identical_files_can_coexist(self):
        for ns in ("ssc_addon", "my_addon"):
            self.put(f"data/{ns}/rules/defaults.json", {"source": f"{ns}:test"})
        self.assertEqual(convert(self.pack, self.out), 2)

    def test_unrepresentable_filter(self):
        self.put("pack.mcmeta", {"pack": {}, "filter": {"block": [{"namespace": "^my_addon$"}]}})
        with self.assertRaisesRegex(ValueError, "filter.block"):
            convert(self.pack, self.out)
        self.assertFalse(self.out.exists())

    def test_embedded_dots_in_resource_paths_and_sound_events(self):
        sounds = {"fx.my_addon.chime": {"subtitle": "subtitles.my_addon.chime", "sounds": [
            "other:fx.my_addon.chime", {"name": "my_addon:fx.my_addon.chime"},
            {"name": "fx.my_addon.chime", "type": "event"}]}}
        self.put("assets/other/sounds.json", sounds)
        for namespace in ("my_addon", "other"):
            self.put(f"assets/{namespace}/sounds/fx.my_addon.chime.ogg", b"audio")
        self.put("assets/other/models/item/demo.json", {"textures": {"layer0": "other:item/x.my_addon.y"}})
        self.put("data/other/functions/demo.mcfunction",
                 b"playsound other:fx.my_addon.chime\nplaysound my_addon:fx.my_addon.chime\n")
        convert(self.pack, self.out)
        with ZipFile(self.out) as result:
            events = json.loads(result.read("assets/other/sounds.json"))
            event = events["fx.my_addon.chime"]
            self.assertEqual(event["subtitle"], "subtitles.ssc_addon.chime")
            self.assertEqual(event["sounds"], ["other:fx.my_addon.chime", {"name": "ssc_addon:fx.my_addon.chime"},
                                              {"name": "fx.my_addon.chime", "type": "event"}])
            for sound in [event["sounds"][0], event["sounds"][1]["name"]]:
                ns, path = sound.split(":")
                self.assertIn(f"assets/{ns}/sounds/{path}.ogg", result.namelist())
            model = json.loads(result.read("assets/other/models/item/demo.json"))
            self.assertEqual(model["textures"]["layer0"], "other:item/x.my_addon.y")
            self.assertEqual(result.read("data/other/functions/demo.mcfunction"),
                             b"playsound other:fx.my_addon.chime\nplaysound ssc_addon:fx.my_addon.chime\n")

    def test_render_assets_keep_default_namespace_paths_and_inherited_texture_slots(self):
        parent = "item.my_addon.base"
        icon = "icon.my_addon.red"
        slot = "paint.ssca_neomagic.red"
        self.put(f"assets/minecraft/models/{parent}.json", {
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {"north": {"texture": "#" + slot}}}]})
        self.put(f"assets/minecraft/textures/{icon}.png", b"default texture")
        self.put("assets/my_addon/textures/item/old.png", b"addon texture")
        self.put("assets/my_addon/models/item/old.json", {})
        self.put("assets/other/models/item/demo.json", {
            "parent": parent, "textures": {slot: icon, "layer0": "my_addon:item/old", "my_addon:slot": "#" + slot},
            "loader_label": {"translate": "model.my_addon.label"},
            "overrides": [{"predicate": {"ssca_neomagic:active": 1}, "model": "my_addon:item/old"}]})
        self.put("assets/other/blockstates/demo.json", {"variants": {"": {"model": parent}}})
        self.put("assets/other/particles/demo.json", {"textures": [icon, "my_addon:item/old"]})
        convert(self.pack, self.out)
        with ZipFile(self.out) as result:
            model = json.loads(result.read("assets/other/models/item/demo.json"))
            base = json.loads(result.read(f"assets/minecraft/models/{model['parent']}.json"))
            face = base["elements"][0]["faces"]["north"]["texture"]
            self.assertEqual(face, "#" + slot)
            self.assertEqual(result.read(f"assets/minecraft/textures/{model['textures'][face[1:]]}.png"), b"default texture")
            self.assertEqual(model["textures"]["layer0"], "ssc_addon:item/old")
            self.assertEqual(model["textures"]["my_addon:slot"], "#" + slot)
            self.assertEqual(model["loader_label"], {"translate": "model.ssc_addon.label"})
            self.assertEqual(model["overrides"], [{"predicate": {"ssc_addon:active": 1}, "model": "ssc_addon:item/old"}])
            self.assertIn("assets/ssc_addon/models/item/old.json", result.namelist())
            self.assertIn("assets/ssc_addon/textures/item/old.png", result.namelist())
            state = json.loads(result.read("assets/other/blockstates/demo.json"))
            self.assertEqual(state["variants"][""]["model"], parent)
            particles = json.loads(result.read("assets/other/particles/demo.json"))
            self.assertEqual(particles["textures"], [icon, "ssc_addon:item/old"])

    def test_atlas_sources_keep_sprite_names_and_resolve_moved_textures(self):
        name = "assets/minecraft/atlases/blocks.json"
        self.put("assets/minecraft/textures/icon.my_addon.red.png", b"base")
        self.put("assets/my_addon/textures/item/old.png", b"overlay")
        original = [
            {"type": "single", "resource": "icon.my_addon.red"},
            {"type": "minecraft:single", "resource": "my_addon:item/old", "sprite": "sprite.my_addon.old"},
            {"type": "directory", "source": "items.my_addon.red", "prefix": "items.my_addon.red/"},
            {"type": "unstitch", "resource": "ssca_neomagic:item/old", "regions": [{"sprite": "cut.my_addon.red"}]},
            {"type": "paletted_permutations", "textures": ["icon.my_addon.red"], "palette_key": "my_addon:palette",
             "permutations": {"shade.my_addon.blue": "ssca_neomagic:palette/blue"}}]
        self.put(name, {"sources": original})
        convert(self.pack, self.out)
        with ZipFile(self.out) as result:
            sources = json.loads(result.read(name))["sources"]
            self.assertEqual(sources[0], original[0])
            self.assertIn("assets/minecraft/textures/" + sources[0]["resource"] + ".png", result.namelist())
            self.assertEqual(sources[1], {"type": "minecraft:single", "resource": "ssc_addon:item/old",
                                          "sprite": "sprite.my_addon.old"})
            self.assertIn("assets/ssc_addon/textures/item/old.png", result.namelist())
            self.assertEqual(sources[2], original[2])
            self.assertEqual(sources[3], {**original[3], "resource": "ssc_addon:item/old"})
            self.assertEqual(sources[4], {**original[4], "palette_key": "ssc_addon:palette",
                                          "permutations": {"shade.my_addon.blue": "ssc_addon:palette/blue"}})

    def test_atlas_filter_matches_original_sprite_paths(self):
        name = "assets/minecraft/atlases/blocks.json"
        source = {"type": "minecraft:filter", "pattern": {"namespace": "^minecraft$", "path": "^icon.my_addon.red$"}}
        self.put(name, {"sources": [source]})
        convert(self.pack, self.out)
        with ZipFile(self.out) as result:
            self.assertEqual(json.loads(result.read(name))["sources"], [source])

    def test_atlas_filter_limited_to_one_merged_namespace_reports_its_location(self):
        name = "assets/minecraft/atlases/blocks.json"
        self.put(name, {"sources": [{"type": "filter", "pattern": {"namespace": "^my_addon$"}}]})
        with self.assertRaisesRegex(ValueError, re.escape(name + " sources[0].pattern")):
            convert(self.pack, self.out)
        self.assertFalse(self.out.exists())

    def test_metadata_components_and_exact_model_filters(self):
        description = [{"text": "pack.my_addon.title", "font": "my_addon:font.my_addon.title"},
                       {"translate": "pack.my_addon.title",
                       "with": [{"translate": "pack.ssca_neomagic.name"}]}]
        rule = r"^ssc_form_model/origins\.origin\.my_addon\.form_allay_sp\.json$"
        fur_rule = r"^furs/my_addon\.form_allay_sp\.json$"
        sound_rule = r"^sounds/fx\.my_addon\.chime\.ogg$"
        self.put("pack.mcmeta", {"pack": {"description": description}, "filter": {"block": [
            {"path": rule}, {"namespace": "^orif-defaults$", "path": fur_rule}, {"path": sound_rule}]}})
        self.put("assets/other/lang/en_us.json", {"pack.my_addon.title": "%s", "pack.ssca_neomagic.name": "pack"})
        self.put("assets/my_addon/ssc_form_model/origins.origin.my_addon.form_allay_sp.json", {})
        self.put("assets/orif-defaults/furs/my_addon.form_allay_sp.json", {})
        convert(self.pack, self.out)
        with ZipFile(self.out) as result:
            meta = json.loads(result.read("pack.mcmeta"))
            language = json.loads(result.read("assets/other/lang/en_us.json"))
            title, translated = meta["pack"]["description"]
            self.assertEqual(title["text"], "pack.my_addon.title")
            self.assertEqual(title["font"], "ssc_addon:font.my_addon.title")
            self.assertIn(translated["translate"], language)
            self.assertIn(translated["with"][0]["translate"], language)
            rules = meta["filter"]["block"]
            for prefix, index in (("assets/ssc_addon/", 0), ("assets/orif-defaults/", 1)):
                path = next(n[len(prefix):] for n in result.namelist() if n.startswith(prefix))
                self.assertIsNotNone(re.search(rules[index]["path"], path))
            self.assertEqual(rules[2]["path"], sound_rule)

    def test_ambiguous_path_and_namespace_substring_filters_require_review(self):
        for rule in ({"namespace": "^my_"}, {"path": r".*my_addon\.form_.*"}):
            with self.subTest(rule=rule):
                self.put("pack.mcmeta", {"pack": {}, "filter": {"block": [rule]}})
                with self.assertRaisesRegex(ValueError, r"filter\.block"):
                    convert(self.pack, self.out)
                self.assertFalse(self.out.exists())

    def test_overlay_directory_is_an_author_supplied_name(self):
        metadata = self.put("pack.mcmeta", {"pack": {}, "overlays": {
            "entries": [{"formats": 18, "directory": "my_addon"}]}})
        self.put("my_addon/assets/my_addon/textures/item.png", b"overlay")
        convert(self.pack, self.out)
        with ZipFile(self.out) as result:
            self.assertEqual(result.read("pack.mcmeta"), metadata.read_bytes())
            self.assertEqual(result.read("my_addon/assets/ssc_addon/textures/item.png"), b"overlay")

    def test_ambiguous_sound_event(self):
        for ns, clip in (("ssc_addon", "first"), ("my_addon", "different")):
            self.put(f"assets/{ns}/sounds.json", {"bell": {"sounds": [f"{ns}:{clip}"]}})
        with self.assertRaisesRegex(ValueError, "bell"):
            convert(self.pack, self.out)
        self.assertFalse(self.out.exists())


if __name__ == "__main__":
    unittest.main()
