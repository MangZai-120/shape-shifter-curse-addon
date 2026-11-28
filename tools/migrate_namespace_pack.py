"""将 SSCA 的旧资源包或数据包转换为使用 ssc_addon 命名空间的新 ZIP。"""

import argparse
from collections import defaultdict
from contextlib import ExitStack
from copy import deepcopy
import json
from pathlib import Path
import re
import stat
from zipfile import BadZipFile, ZIP_DEFLATED, ZipFile


LEGACY = {"my_addon", "ssca_neomagic"}
CANONICAL = "ssc_addon"
TOKENS = r"(?:my_addon|ssca_neomagic)"
DOTTED_KEY = re.compile(r"(?<![A-Za-z0-9_:/-])" + TOKENS + r"(?=\.(?![A-Za-z0-9_.-]+:))")
REFERENCE = re.compile(
    r"(?<![A-Za-z0-9_.:/-])(?P<resource>[a-z0-9_.-]+:[a-z0-9_./-]+|[a-z0-9_.-]+/[a-z0-9_./-]+)"
    r"|" + DOTTED_KEY.pattern)
TEXT_TYPES = {".mcfunction", ".properties", ".fsh", ".vsh", ".glsl"}
RENDER_FIELDS = {
    "models": {"parent", "textures", "elements", "overrides"},
    "blockstates": {"variants", "multipart"},
    "particles": {"textures"},
}


def model_filename(path):
    parent, slash, name = path.rpartition("/")
    return parent + slash + DOTTED_KEY.sub(CANONICAL, name)


def resource_id(text):
    namespace, colon, path = text.partition(":")
    if not colon:
        namespace, path = "", text
    if path.startswith("ssc_form_model/") or (namespace == "orif-defaults" and path.startswith("furs/")):
        path = model_filename(path)
    return (CANONICAL if namespace in LEGACY else namespace) + colon + path


def rewrite(text):
    if text in LEGACY:
        return CANONICAL
    # Consume a whole resource ID before looking for translation keys in the same string.
    # Dots are legal in namespaces and paths, including third-party sound filenames.
    return REFERENCE.sub(lambda m: resource_id(m.group()) if m.group("resource") else CANONICAL, text)


def pack_path(name):
    parts = name.split("/")
    if any(part in ("", ".", "..") or "\\" in part or ":" in part for part in parts):
        raise ValueError(f"无效的包内路径 {name!r}")
    for index, part in enumerate(parts[:-2]):
        if part in ("assets", "data"):
            if parts[index + 1] in LEGACY:
                parts[index + 1] = CANONICAL
            if part == "assets" and (parts[index + 2] == "ssc_form_model"
                                     or parts[index + 1:index + 3] == ["orif-defaults", "furs"]):
                parts[-1] = model_filename(parts[-1])
            break
    return "/".join(parts)


def json_map(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"JSON 存在重复键 {key!r}")
        result[key] = value
    return result


def merge_entry(target, key, value):
    if key in target and json.dumps(target[key], sort_keys=True) != json.dumps(value, sort_keys=True):
        raise ValueError(f"合并后的键 {key!r} 有多个不同定义")
    target[key] = value


def rewrite_render_id(text):
    # Model parents, texture slots and particle sprite names can contain dots.
    # Only the namespace of an explicitly qualified resource ID can change.
    namespace, colon, path = text.partition(":")
    return CANONICAL + colon + path if colon and namespace in LEGACY else text


def rewrite_json(value, rewrite_string=rewrite):
    if isinstance(value, str):
        return rewrite_string(value)
    if isinstance(value, list):
        return [rewrite_json(item, rewrite_string) for item in value]
    if isinstance(value, dict):
        result = {}
        for key, item in value.items():
            merge_entry(result, rewrite_string(key), rewrite_json(item, rewrite_string))
        return result
    return value


def rewrite_render_definition(document, category):
    if not isinstance(document, dict):
        return rewrite_json(document)
    result = {}
    for key, value in document.items():
        if category == "models" and key == "textures" and isinstance(value, dict):
            # A texture slot belongs to the model and may be supplied by a child in another pack.
            result[key] = {slot: rewrite_json(sprite, rewrite_render_id) for slot, sprite in value.items()}
        else:
            convert = rewrite_render_id if key in RENDER_FIELDS[category] else rewrite
            merge_entry(result, rewrite(key), rewrite_json(value, convert))
    return result


def check_filter_namespace(rule, location):
    if not isinstance(rule, dict) or not isinstance(rule.get("namespace", ""), str):
        raise ValueError(f"{location} 的 namespace 必须是正则字符串")
    namespace = rule.get("namespace", ".*")
    # Both pack filters and atlas filters use Minecraft's BlockEntry (Matcher.find).
    if len({bool(re.search(namespace, ns)) for ns in LEGACY | {CANONICAL}}) > 1:
        raise ValueError(f"资源过滤规则命中附属命名空间，合并后过滤范围可能改变，请先手工复核 {location}")
    return namespace


def rewrite_atlas_source(source, location):
    if isinstance(source, dict):
        kind = source.get("type")
        if kind in ("filter", "minecraft:filter"):
            check_filter_namespace(source.get("pattern"), location + ".pattern")
            # The atlas filter matches sprite paths, whose filenames do not move.
            return source
        if isinstance(kind, str) and kind.removeprefix("minecraft:") in {
            "single", "directory", "unstitch", "paletted_permutations"
        }:
            return rewrite_json(source, rewrite_render_id)
    return rewrite_json(source)


def encoded_json(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def rewrite_component(component):
    if isinstance(component, list):
        return [rewrite_component(child) for child in component]
    if isinstance(component, dict):
        result = {key: rewrite_component(value) for key, value in component.items()}
        if isinstance(component.get("translate"), str):
            result["translate"] = rewrite(component["translate"])
        if isinstance(component.get("font"), str):
            result["font"] = resource_id(component["font"])
        return result
    return component


def rewrite_sounds(events):
    if not isinstance(events, dict):
        raise ValueError("sounds.json 的根必须是事件对象")
    for event in events.values():
        if not isinstance(event, dict):
            raise ValueError("sounds.json 的事件必须是对象")
        if isinstance(event.get("subtitle"), str):
            event["subtitle"] = rewrite(event["subtitle"])
        for index, sound in enumerate(event.get("sounds", [])):
            if isinstance(sound, str):
                event["sounds"][index] = resource_id(sound)
            elif isinstance(sound, dict) and isinstance(sound.get("name"), str):
                sound["name"] = resource_id(sound["name"])


def filter_path(pattern, namespace):
    if not any(old in pattern for old in LEGACY):
        return pattern
    # Only filenames inside these two model directories change. Other anchored
    # directory filters (e.g. sounds/fx.my_addon.ogg) keep their original meaning.
    directory = re.match(r"\^([a-z0-9_-]+)/", pattern)
    if directory and "|" not in pattern:
        if directory[1] not in ("ssc_form_model", "furs"):
            return pattern
        if directory[1] == "furs" and not re.search(namespace, "orif-defaults"):
            return pattern
        literal = re.fullmatch(r"\^([a-z0-9_/-]+)/((?:[a-z0-9_-]|\\\.)+)\$", pattern)
        if literal and (directory[1] == "ssc_form_model" or namespace == "^orif-defaults$"):
            name = literal[2].replace(r"\.", ".")
            renamed = model_filename(name).replace(".", r"\.")
            return f"^{literal[1]}/{renamed}$"
    raise ValueError(f"请手工复核 filter.block.path 的旧模型文件名正则：{pattern}")


def rewrite_metadata(metadata):
    if not isinstance(metadata, dict) or not isinstance(metadata.get("pack"), dict):
        raise ValueError("缺少 pack 元数据")
    pack = metadata["pack"]
    if "description" in pack:
        pack["description"] = rewrite_component(pack["description"])
    filters = metadata.get("filter", {})
    if not isinstance(filters, dict) or not isinstance(filters.get("block", []), list):
        raise ValueError("filter.block 必须是规则列表")
    for rule in filters.get("block", []):
        namespace = check_filter_namespace(rule, "filter.block")
        if "path" in rule:
            if not isinstance(rule["path"], str):
                raise ValueError("filter.block.path 必须是正则字符串")
            rule["path"] = filter_path(rule["path"], namespace)


def convert_file(name, data):
    suffix = Path(name).suffix.lower()
    if suffix in (".json", ".mcmeta"):
        original = json.loads(data, object_pairs_hook=json_map)
        if name == "pack.mcmeta":
            updated = deepcopy(original)
            rewrite_metadata(updated)
        elif re.search(r"(?:^|/)assets/[^/]+/sounds\.json$", name):
            updated = deepcopy(original)
            rewrite_sounds(updated)
        elif match := re.search(r"(?:^|/)assets/[^/]+/(models|blockstates|particles)/.+\.json$", name):
            updated = rewrite_render_definition(original, match[1])
        elif (re.search(r"(?:^|/)assets/[^/]+/atlases/.+\.json$", name)
              and isinstance(original, dict) and isinstance(original.get("sources"), list)):
            updated = rewrite_json({key: value for key, value in original.items() if key != "sources"})
            updated["sources"] = [rewrite_atlas_source(source, f"{name} sources[{index}]")
                                  for index, source in enumerate(original["sources"])]
        else:
            updated = rewrite_json(original)
        return data if updated == original else encoded_json(updated)
    if suffix in TEXT_TYPES:
        text = data.decode("utf-8")
        return rewrite(text).encode("utf-8")
    return data


def merge_files(name, sources, read):
    converted = [convert_file(path, read(path)) for path in sources]
    if all(data == converted[0] for data in converted):
        return converted[0]
    if re.search(r"(?:^|/)assets/[^/]+/(?:sounds\.json|lang/[^/]+\.json)$", name):
        result = {}
        for data in converted:
            properties = json.loads(data, object_pairs_hook=json_map)
            if not isinstance(properties, dict):
                raise ValueError("声音与语言文件的根必须是 JSON 对象")
            for key, value in properties.items():
                merge_entry(result, key, value)
        return encoded_json(result)
    if name.endswith((".json", ".mcmeta")):
        documents = [json.dumps(json.loads(data), sort_keys=True) for data in converted]
        if len(set(documents)) == 1:
            return converted[0]
    raise ValueError("多个文件映射到同一路径且内容不同，请手工合并")


def convert(source, output):
    source, output = Path(source).resolve(), Path(output)
    destination = output.resolve()
    if source == destination or (source.is_dir() and source in destination.parents):
        raise ValueError("请将新 ZIP 保存在输入包之外")
    with ExitStack() as stack:
        if source.is_dir():
            paths = []
            for path in source.rglob("*"):
                if path.is_symlink():
                    raise ValueError(f"请先解除包内符号链接 {path}")
                if path.is_file():
                    paths.append(path.relative_to(source).as_posix())
            def read(name):
                return (source / name).read_bytes()
        else:
            archive = stack.enter_context(ZipFile(source))
            paths = []
            for info in archive.infolist():
                if stat.S_ISLNK(info.external_attr >> 16):
                    raise ValueError(f"请先解除包内符号链接 {info.filename}")
                if not info.is_dir():
                    paths.append(info.filename)
            read = archive.read
        if "pack.mcmeta" not in paths or len(set(paths)) != len(paths):
            raise ValueError("输入包的根目录必须含 pack.mcmeta，且路径不能重复")
        targets = defaultdict(list)
        for path in sorted(paths):
            targets[pack_path(path)].append(path)
        for target in targets:
            prefix = target
            while "/" in prefix:
                prefix = prefix.rsplit("/", 1)[0]
                if prefix in targets:
                    raise ValueError(f"路径同时用作文件和目录：{prefix}")
        # Exclusive creation protects both the input and existing output files.
        with output.open("xb") as stream:
            try:
                with ZipFile(stream, "w", ZIP_DEFLATED) as result:
                    for target, paths in sorted(targets.items()):
                        try:
                            result.writestr(target, merge_files(target, paths, read))
                        except (ValueError, re.error) as err:
                            raise ValueError(f"{', '.join(paths)} → {target}: {err}") from err
            except BaseException:
                stream.close()
                output.unlink()
                raise
    return len(targets)


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=(
        "转换 assets/data 路径、模型描述名、JSON 与 mcfunction 等 UTF-8 文本里的 ID。"
        "同路径声音和翻译按键合并；不一致的定义会报错。二进制内容原样复制，"
        "结构 NBT 或自定义脚本中的旧 ID 需作者检查。修改前后两个包只启用一份。"))
    parser.add_argument("source", help="含 pack.mcmeta 的目录或 ZIP")
    parser.add_argument("output", help="尚不存在的新 ZIP 文件")
    args = parser.parse_args()
    try:
        count = convert(args.source, args.output)
    except (OSError, ValueError, BadZipFile, RuntimeError, NotImplementedError) as err:
        parser.exit(1, f"转换失败：{err}\n")
    print(f"已写入 {count} 个文件：{args.output}")


if __name__ == "__main__":
    main()
