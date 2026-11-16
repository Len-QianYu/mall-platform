"""Maintain redistributable product photo fixtures; never connects to the database.

Normal clones use committed images and the Java importer, so Python/Pillow and
network access are needed only when maintaining or replacing these assets.
"""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import html
import io
import json
from pathlib import Path
import re
import sys
import time
import urllib.parse
import urllib.error
import urllib.request
import uuid
import zlib
from xml.sax.saxutils import escape

API_ROOT = Path(__file__).resolve().parents[1]
ROOT = API_ROOT / "demo" / "product-images"
CACHE = API_ROOT / "target" / "demo-image-cache"
MANIFEST = ROOT / "manifest.json"
USER_AGENT = "MallPlatformDemoAssets/1.0 (educational portfolio fixtures; Wikimedia Commons attribution retained)"
COMMONS_API = "https://commons.wikimedia.org/w/api.php"
BAD_TITLE = re.compile(r"screenshot|diagram|infographic|logo|icon|map of|slide\b|microscop|x-ray|page \d|\.pdf", re.I)


def all_kinds(catalog):
    return [*catalog["items"], *catalog.get("additionalImageItems", [])]


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + "." + uuid.uuid4().hex + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def plain(value: str) -> str:
    return " ".join(html.unescape(re.sub(r"<[^>]+>", " ", value or "")).split())


def download(url: str) -> bytes:
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname not in {
        "commons.wikimedia.org", "upload.wikimedia.org", "thumb.wikimedia.org"
    }:
        raise ValueError("Unexpected image source host")
    error = None
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=30) as response:
                data = response.read(12 * 1024 * 1024 + 1)
            if len(data) > 12 * 1024 * 1024:
                raise ValueError("Image source exceeds the download size limit")
            return data
        except Exception as exception:
            error = exception
            if attempt < 3:
                if isinstance(exception, urllib.error.HTTPError) and exception.code == 429:
                    delay = exception.headers.get("Retry-After", "20")
                    time.sleep(min(45, int(delay) if delay.isdigit() else 20))
                else:
                    time.sleep(1 + attempt * 2)
    raise RuntimeError(f"Download failed: {url}: {error}")


def candidates(query: str):
    cache = CACHE / ("query-" + hashlib.sha256(query.encode()).hexdigest()[:16] + ".json")
    if cache.exists():
        return read_json(cache)
    parameters = {
        "action": "query", "generator": "search", "gsrsearch": "filetype:bitmap " + query,
        "gsrnamespace": 6, "gsrlimit": 20, "prop": "imageinfo",
        "iiprop": "url|size|mime|extmetadata", "iiurlwidth": 600, "format": "json",
    }
    payload = json.loads(download(COMMONS_API + "?" + urllib.parse.urlencode(parameters)))
    if "error" in payload:
        raise RuntimeError(str(payload["error"]))
    pages = sorted(payload.get("query", {}).get("pages", {}).values(), key=lambda page: page.get("index", 999))
    result = []
    for page in pages:
        if BAD_TITLE.search(page["title"]):
            continue
        info = page.get("imageinfo", [{}])[0]
        metadata = info.get("extmetadata", {})
        license_name = plain(metadata.get("LicenseShortName", {}).get("value", ""))
        if not re.fullmatch(r"CC BY(?:-SA)? [1-4]\.0|CC0(?: 1\.0)?|Public domain", license_name, re.I):
            continue
        if info.get("mime") not in {"image/jpeg", "image/png", "image/webp"}:
            continue
        if min(info.get("width", 0), info.get("height", 0)) < 250:
            continue
        license_url = metadata.get("LicenseUrl", {}).get("value", "")
        if not license_url:
            license_url = "https://creativecommons.org/publicdomain/mark/1.0/" if license_name.lower() == "public domain" else "https://creativecommons.org/publicdomain/zero/1.0/"
        result.append({
            "title": page["title"], "sourceUrl": info["descriptionurl"],
            "downloadUrl": info.get("thumburl", info["url"]), "originalUrl": info["url"],
            "license": license_name, "licenseUrl": license_url,
            "author": plain(metadata.get("Artist", {}).get("value", "Unknown; see source page")),
            "description": plain(metadata.get("ImageDescription", {}).get("value", ""))[:500],
        })
    write_json(cache, result)
    return result


def photo_bytes(source):
    from PIL import Image, ImageOps

    cache = CACHE / ("image-" + hashlib.sha256(source["originalUrl"].encode()).hexdigest()[:20])
    if cache.exists():
        data = cache.read_bytes()
    else:
        url = source["downloadUrl"]
        # Small originals use thumbnail_unscaled URLs, which Commons may rate-limit.
        # Request supported thumbnail sizes through its API instead of retrying originals.
        if "/thumb/" not in urllib.parse.urlsplit(url).path:
            for width in [320, 120]:
                parameters = {
                    "action": "query", "titles": source["title"], "prop": "imageinfo",
                    "iiprop": "url", "iiurlwidth": width, "format": "json",
                }
                payload = json.loads(download(COMMONS_API + "?" + urllib.parse.urlencode(parameters)))
                pages = list(payload.get("query", {}).get("pages", {}).values())
                if len(pages) != 1 or not pages[0].get("imageinfo"):
                    raise ValueError("Reviewed thumbnail is unavailable: " + source["title"])
                url = pages[0]["imageinfo"][0].get("thumburl", url)
                if "/thumb/" in urllib.parse.urlsplit(url).path:
                    break
            else:
                raise ValueError("Reviewed source has no supported thumbnail: " + source["title"])
        data = download(url)
        cache.parent.mkdir(parents=True, exist_ok=True)
        cache.write_bytes(data)
    with Image.open(io.BytesIO(data)) as original:
        image = ImageOps.exif_transpose(original).convert("RGBA")
        if max(image.size) < 350:
            image = ImageOps.contain(image, (500, 500), Image.Resampling.LANCZOS)
        image.thumbnail((560, 560), Image.Resampling.LANCZOS)
        canvas = Image.new("RGB", (600, 600), "white")
        canvas.paste(image, ((600 - image.width) // 2, (600 - image.height) // 2), image)
        output = io.BytesIO()
        canvas.save(output, "WEBP", quality=82, method=6)
        return output.getvalue()


def approved_source(title):
    """Resolve a reviewed Commons file; search ranking never decides an import."""
    for path in sorted(CACHE.glob("query-*.json")):
        for source in read_json(path):
            if source["title"] == title:
                return source
    cache = CACHE / ("approved-source-" + hashlib.sha256(title.encode()).hexdigest()[:20] + ".json")
    if cache.is_file():
        return read_json(cache)
    parameters = {
        "action": "query", "titles": title, "prop": "imageinfo",
        "iiprop": "url|size|mime|extmetadata", "iiurlwidth": 600, "format": "json",
    }
    payload = json.loads(download(COMMONS_API + "?" + urllib.parse.urlencode(parameters)))
    pages = list(payload.get("query", {}).get("pages", {}).values())
    if len(pages) != 1 or pages[0].get("title") != title or not pages[0].get("imageinfo"):
        raise ValueError("Approved source is unavailable; review a replacement explicitly: " + title)
    info = pages[0]["imageinfo"][0]
    metadata = info.get("extmetadata", {})
    license_name = plain(metadata.get("LicenseShortName", {}).get("value", ""))
    if (not re.fullmatch(r"CC BY(?:-SA)? [1-4]\.0|CC0(?: 1\.0)?|Public domain", license_name, re.I)
            or info.get("mime") not in {"image/jpeg", "image/png", "image/webp"}
            or min(info.get("width", 0), info.get("height", 0)) < 250):
        raise ValueError("Approved source has an unsupported license, format or size: " + title)
    license_url = metadata.get("LicenseUrl", {}).get("value", "")
    if not license_url:
        license_url = "https://creativecommons.org/publicdomain/mark/1.0/" if license_name.lower() == "public domain" else "https://creativecommons.org/publicdomain/zero/1.0/"
    source = {
        "title": title, "sourceUrl": info["descriptionurl"],
        "downloadUrl": info.get("thumburl", info["url"]), "originalUrl": info["url"],
        "license": license_name, "licenseUrl": license_url,
        "author": plain(metadata.get("Artist", {}).get("value", "Unknown; see source page")),
        "description": plain(metadata.get("ImageDescription", {}).get("value", ""))[:500],
    }
    write_json(cache, source)
    return source


def chinese_font(size):
    from PIL import ImageFont

    for filename in [
        "C:/Windows/Fonts/msyh.ttc", "/System/Library/Fonts/PingFang.ttc",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
    ]:
        if Path(filename).is_file():
            return ImageFont.truetype(filename, size)
    raise RuntimeError("A Chinese font is required to render book covers and the preview (Microsoft YaHei or Noto Sans CJK).")


def book_cover(kind, variant):
    """Typeset an original SVG cover and its WebP export with the exact book title."""
    from PIL import Image, ImageDraw

    colors = kind["imageSource"]["colors"]
    color = colors[variant]
    canvas = Image.new("RGB", (600, 600), "white")
    draw = ImageDraw.Draw(canvas)
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="600" height="600" viewBox="0 0 600 600">',
           '<rect width="600" height="600" fill="white"/>']

    def rect(box, fill):
        draw.rectangle(box, fill=fill)
        x0, y0, x1, y1 = box
        svg.append(f'<rect x="{x0}" y="{y0}" width="{x1-x0}" height="{y1-y0}" fill="{fill}"/>')

    def line(points, fill, width=2):
        draw.line(points, fill=fill, width=width)
        svg.append(f'<polyline points="{" ".join(f"{x},{y}" for x,y in points)}" fill="none" stroke="{fill}" stroke-width="{width}"/>')

    def label(x, y, value, size, fill):
        draw.text((x, y), value, font=chinese_font(size), fill=fill)
        svg.append(f'<text x="{x}" y="{y+size}" font-family="Microsoft YaHei, Noto Sans CJK SC, sans-serif" font-size="{size}" fill="{fill}">{escape(value)}</text>')

    rect((155, 85, 463, 526), "#edf0f4")
    rect((147, 69, 445, 510), "#e8e2d6")
    for y in range(81, 510, 7):
        line([(147, y), (159, y)], "#d4cebf", 1)
    rect((159, 61, 453, 502), color)
    rect((159, 61, 177, 502), "#263849")
    rect((194, 91, 238, 98), "#f3ca7b")
    label(194, 110, kind["imageSource"]["topic"], 13, "#f3ca7b")
    title = kind["item"].strip("《》")
    title_font = chinese_font(34)
    lines, current = [], ""
    for character in title:
        if current and title_font.getlength(current + character) > 227:
            lines.append(current.rstrip())
            current = character.lstrip()
        else:
            current += character
    if current:
        lines.append(current)
    for index, value in enumerate(lines):
        label(194, 150 + index * 48, value, 34, "#ffffff")
    top = max(340, 170 + len(lines) * 48)
    for index, width in enumerate([165, 107, 188, 137]):
        line([(194, top + index * 15), (194 + width, top + index * 15)], "#9eb4c2", 3)
    label(194, 453, "演示样图", 17, "#f3ca7b")
    svg.append("</svg>")
    vector = ROOT / "book-covers" / f"{kind['slug']}-{variant + 1}.svg"
    vector.parent.mkdir(parents=True, exist_ok=True)
    vector.write_text("\n".join(svg) + "\n", encoding="utf-8")
    output = io.BytesIO()
    canvas.save(output, "WEBP", quality=88, method=6)
    source_path = vector.relative_to(ROOT).as_posix()
    source = {
        "title": f"Original demo cover: {kind['item']} / {variant + 1}",
        "sourceUrl": source_path, "originalUrl": source_path,
        "license": "CC0 1.0", "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/",
        "author": "Mall Platform contributors", "description": "Original typeset demonstration cover; not an official publisher cover.",
        "modifications": "Original SVG cover exported to a white 600x600 WebP canvas; the exact product title is printed on the cover.",
    }
    return source, output.getvalue()


def fetch_kind(kind):
    selection = kind.get("imageSource", {})
    if selection.get("type") not in {"commons", "book-cover"}:
        raise ValueError("Missing reviewed image sources: " + kind["slug"])
    if selection["type"] == "commons" and len(selection.get("titles", [])) != 3:
        raise ValueError("Expected three explicitly reviewed sources: " + kind["slug"])
    photos = []
    for variant in range(3):
        if selection["type"] == "book-cover":
            source, data = book_cover(kind, variant)
        else:
            source = approved_source(selection["titles"][variant])
            data = photo_bytes(source)
            source = {**source, "modifications": "Resized and letterboxed on a white 600x600 canvas; encoded as WebP."}
        digest = hashlib.sha256(data).hexdigest()
        filename = f"demo-{kind['slug']}-{variant + 1}-{digest[:12]}.webp"
        (ROOT / "assets").mkdir(parents=True, exist_ok=True)
        (ROOT / "assets" / filename).write_bytes(data)
        photos.append({**source, "file": filename, "sha256": digest, "bytes": len(data),
                       "width": 600, "height": 600, "productItem": kind["item"], "sourceType": selection["type"]})
    return {"slug": kind["slug"], "photos": photos}


def verify(catalog, manifest):
    if catalog["version"] != 1 or catalog["variantsPerItem"] != 3 or len(catalog["items"]) != 48:
        raise ValueError("Expected 48 product kinds and three variants each")
    kinds = all_kinds(catalog)
    by_slug = {kind["slug"]: kind for kind in manifest["items"]}
    if manifest["version"] != 1 or len(by_slug) != len(kinds) or len(manifest["items"]) != len(kinds):
        raise ValueError("Image manifest is incomplete")
    files = set()
    slugs = set()
    for kind in kinds:
        if kind["slug"] in slugs or not re.fullmatch(r"[a-z0-9-]+", kind["slug"]):
            raise ValueError("Duplicate or invalid product kind")
        slugs.add(kind["slug"])
        photos = by_slug[kind["slug"]]["photos"]
        if len(photos) != 3:
            raise ValueError("Expected three photos for " + kind["slug"])
        for index, photo in enumerate(photos):
            filename = photo["file"]
            if filename != f"demo-{kind['slug']}-{index + 1}-{photo['sha256'][:12]}.webp" or filename in files:
                raise ValueError("Invalid asset filename")
            selection = kind.get("imageSource", {})
            if photo.get("productItem") != kind["item"] or photo.get("sourceType") != selection.get("type"):
                raise ValueError("Image does not belong to this product: " + filename)
            if selection.get("type") == "commons" and photo["title"] != selection["titles"][index]:
                raise ValueError("Image is not an explicitly approved source: " + filename)
            if selection.get("type") == "book-cover":
                vector = ROOT / "book-covers" / f"{kind['slug']}-{index + 1}.svg"
                if not vector.is_file() or not all(escape(value) in vector.read_text(encoding="utf-8") for value in kind["item"].strip("《》")):
                    raise ValueError("Missing matching book cover source: " + filename)
            data = (ROOT / "assets" / filename).read_bytes()
            if hashlib.sha256(data).hexdigest() != photo["sha256"] or len(data) != photo["bytes"]:
                raise ValueError("Asset checksum mismatch: " + filename)
            if not photo["author"] or not photo["licenseUrl"] or not photo["sourceUrl"]:
                raise ValueError("Missing attribution: " + filename)
            files.add(filename)
    if len(files) != len(kinds) * 3:
        raise ValueError("Image asset count does not match the catalog")
    for kind in catalog["items"]:
        for legacy in kind["legacy"]:
            image_kind = next(row for row in kinds if row["slug"] == legacy.get("imageSlug", kind["slug"]))
            if "imageSlug" in legacy and image_kind["item"] != legacy["name"]:
                raise ValueError("Legacy product cover does not match its name")


def sql_string(value):
    return "'" + str(value).replace("'", "''") + "'"


def image_url(kind, manifest, product_id):
    legacy = next((row for row in kind.get("legacy", []) if row["id"] == product_id), {})
    slug = legacy.get("imageSlug", kind["slug"])
    photos = next(row["photos"] for row in manifest["items"] if row["slug"] == slug)
    variant = zlib.crc32((str(product_id) + "#image").encode()) % 3
    return "/images/" + photos[variant]["file"]


def replace_block(path, start, end, body):
    text = path.read_text(encoding="utf-8-sig")
    if text.count(start) != 1 or text.count(end) != 1:
        raise ValueError("Missing generated block markers in " + str(path))
    head, tail = text.split(start, 1)
    _, tail = tail.split(end, 1)
    path.write_text(head + start + "\n" + body.rstrip() + "\n" + end + tail, encoding="utf-8")


def render(catalog, manifest):
    verify(catalog, manifest)
    by_slug = {kind["slug"]: kind["photos"] for kind in manifest["items"]}
    attribution = [
        "# 商品演示图片来源与许可", "",
        "这些照片用作商品类型样图，不对应实际在售 SKU，品牌与摄影作者没有为本项目背书。",
        "照片来自 Wikimedia Commons，按 catalog.json 中逐张核对后的 File 标题导入，不使用搜索结果排序自动选图。",
        "代码许可不替代照片各自的许可；每张衍生图片继续采用下表中的许可。",
        "照片保留完整画面，缩放并置于 600×600 白色画布，转换为 WebP。",
        "图书采用本项目原创的标题封面（标注“演示样图”，CC0），书名与商品一致；不是出版社的正式封面。SVG 源文件见 book-covers/。", "",
        "| 商品类型 | 本地文件 | 作者 | 来源 | 图片许可 |", "|---|---|---|---|---|",
    ]
    for kind in all_kinds(catalog):
        for photo in by_slug[kind["slug"]]:
            author = photo["author"].replace("|", "\\|")
            attribution.append(f"| {kind['item']} | [{photo['file']}](assets/{photo['file']}) | {author} | [原图页面]({photo['sourceUrl']}) | [{photo['license']}]({photo['licenseUrl']}) |")
    retired_path = ROOT / "retired-manifest.json"
    if retired_path.is_file():
        attribution += ["", "## 已停用的旧素材", "", "仅保留旧文件与许可记录，用于识别和修复既有演示数据；新导入不再使用这些映射。", "",
                        "| 旧图片组 | 本地文件 | 作者 | 来源 | 图片许可 |", "|---|---|---|---|---|"]
        for group in read_json(retired_path)["items"]:
            for photo in group["photos"]:
                author = photo["author"].replace("|", "\\|")
                attribution.append(f"| {group['slug']} | [{photo['file']}](assets/{photo['file']}) | {author} | [原图页面]({photo['sourceUrl']}) | [{photo['license']}]({photo['licenseUrl']}) |")
    (ROOT / "ATTRIBUTION.md").write_text("\n".join(attribution) + "\n", encoding="utf-8")

    pool_rows = []
    for index, kind in enumerate(catalog["items"]):
        paths = ["/images/" + photo["file"] for photo in by_slug[kind["slug"]]]
        row = "SELECT " + ", ".join(sql_string(value) for value in [kind["item"], *paths])
        if index == 0:
            row = "SELECT " + ", ".join(f"{sql_string(value)} AS `{column}`" for value, column in zip([kind["item"], *paths], ["image_item", "image0", "image1", "image2"]))
        pool_rows.append("    " + row)
    replace_block(API_ROOT / "src/main/resources/db/bench-product.sql", "-- BEGIN DEMO IMAGE POOL", "-- END DEMO IMAGE POOL", "\n    UNION ALL\n".join(pool_rows))

    rows = ["-- Generated from demo/product-images/catalog.json and manifest.json; maintain with prepare_product_images.py."]
    for kind in catalog["items"]:
        for legacy in kind["legacy"]:
            rows.append("UPDATE `product` SET `main_image` = " + sql_string(image_url(kind, manifest, legacy["id"]))
                        + f" WHERE `id` = {legacy['id']} AND `name` = {sql_string(legacy['name'])} AND (`main_image` IS NULL OR `main_image` = '');")
    rows += ["", "-- 48 curated demonstration products; IDs 101..148 are outside the benchmark range.",
             "INSERT INTO `product` (`id`, `name`, `subtitle`, `description`, `category`, `price`, `stock`, `sales`, `main_image`, `status`, `created_at`) VALUES"]
    for index, kind in enumerate(catalog["items"]):
        product_id = 101 + index
        description = kind["item"] + "适合日常使用与选购。" + kind["subtitle"] + "。请按个人需求选择，使用和保养请参考产品说明。"
        values = [str(product_id), sql_string(kind["item"]), sql_string(kind["subtitle"]), sql_string(description),
                  sql_string(kind["category"]), kind["price"], "100", str(20 + index * 7),
                  sql_string(image_url(kind, manifest, product_id)), "1", "'2026-11-02 10:00:00'"]
        rows.append("(" + ", ".join(values) + ")" + (";" if index == 47 else ","))
    snapshot_conditions = [f"(`oi`.`id` = {snapshot['id']} AND `oi`.`product_id` = {snapshot['productId']} AND `oi`.`product_name` = {sql_string(snapshot['name'])} AND `oi`.`created_at` = {sql_string(snapshot['createdAt'])})" for snapshot in catalog["legacyOrderItems"]]
    rows += ["", "-- Order images are snapshots: only initialize the original demo order items here.",
             "UPDATE `order_item` AS `oi` JOIN `product` AS `p` ON `p`.`id` = `oi`.`product_id`",
             "SET `oi`.`product_image` = `p`.`main_image`",
             "WHERE `oi`.`product_name` = `p`.`name` AND (`oi`.`product_image` IS NULL OR `oi`.`product_image` = '')",
             "  AND (" + "\n    OR ".join(snapshot_conditions) + ");"]
    replace_block(API_ROOT / "src/main/resources/db/data.sql", "-- BEGIN DEMO PRODUCT IMAGES", "-- END DEMO PRODUCT IMAGES", "\n".join(rows))

    from PIL import Image, ImageDraw, ImageFont

    kinds = all_kinds(catalog)
    sheet = Image.new("RGB", (960, len(kinds) * 170), "#edf1f5")
    draw = ImageDraw.Draw(sheet)
    font = chinese_font(18)
    title_font = ImageFont.load_default()
    for index, kind in enumerate(kinds):
        top = index * 170
        draw.text((12, top + 55), kind["item"], font=font, fill="#111827")
        draw.text((12, top + 85), kind["slug"], font=title_font, fill="#374151")
        for variant, photo in enumerate(by_slug[kind["slug"]]):
            with Image.open(ROOT / "assets" / photo["file"]) as image:
                image.thumbnail((152, 152))
                sheet.paste(image, (270 + variant * 210, top + 8))
    sheet.save(ROOT / "preview.jpg", quality=85)
    print(f"Verified 48 product kinds / {len(kinds) * 3} images including dedicated legacy covers; rendered SQL mappings, attribution and preview.", flush=True)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fetch", action="store_true", help="download licensed photo fixtures; maintainer-only network operation")
    parser.add_argument("--verify", action="store_true", help="validate committed files without network")
    parser.add_argument("--only", help="fetch one catalog slug while retaining other completed kinds")
    parser.add_argument("--search", help="list licensed Commons candidates for manual review; never selects or imports them")
    args = parser.parse_args()
    catalog = read_json(ROOT / "catalog.json")
    if args.search:
        for source in candidates(args.search):
            print(json.dumps(source, ensure_ascii=False))
        return
    if args.only and (not args.fetch or args.only not in {kind["slug"] for kind in all_kinds(catalog)}):
        parser.error("--only requires --fetch and an existing catalog slug")
    manifest = read_json(MANIFEST) if MANIFEST.exists() else {"version": 1, "items": []}
    if args.fetch:
        CACHE.mkdir(parents=True, exist_ok=True)
        existing = {kind["slug"]: kind for kind in manifest["items"]}
        kinds = all_kinds(catalog)
        jobs = [kind for kind in kinds if not args.only or kind["slug"] == args.only]
        failures = []
        with ThreadPoolExecutor(max_workers=2) as pool:
            pending = {pool.submit(fetch_kind, kind): kind for kind in jobs}
            for future in as_completed(pending):
                kind = pending[future]
                try:
                    existing[kind["slug"]] = future.result()
                    manifest["items"] = [existing[row["slug"]] for row in kinds if row["slug"] in existing]
                    write_json(MANIFEST, manifest)
                    print("Prepared: " + kind["slug"] + " (" + str(len(existing)) + "/" + str(len(kinds)) + ")", flush=True)
                except Exception as exception:
                    failures.append(str(exception))
                    print("Needs attention: " + str(exception), flush=True)
        if failures:
            raise RuntimeError("Photo preparation incomplete: " + str(len(failures)) + " kinds")
    if args.verify:
        verify(catalog, manifest)
        print("Committed photo checksums and attribution are valid.")
    else:
        render(catalog, manifest)


if __name__ == "__main__":
    try:
        main()
    except Exception as exception:
        print(str(exception), file=sys.stderr)
        sys.exit(1)
