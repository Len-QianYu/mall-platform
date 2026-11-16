# 商品演示图片

`catalog.json` 按商品名称固定已经核对的素材文件，`manifest.json` 记录导入图片的商品类型、来源、许可和 SHA-256。`preview.jpg` 可用于对照商品名称检查全部图片。

在仓库根目录的 PowerShell 中预览和执行导入：

```powershell
& .\api\tools\seed-product-images.ps1 -DryRun
& .\api\tools\seed-product-images.ps1
```

导入会先校验并上传图片，再通过数据库事务补齐或修正图片路径，最后清理商品缓存。既有演示图片可以更新；手动上传的图片、范围外的商品、名称已修改的商品和非原始演示订单快照会保留。重复执行没有待修正的数据时不会更新记录。

48 个演示商品类型各有三张图片，另外两种原始图书有独立封面。图书图片是本项目原创的封面示意图，注明“演示样图”，实际书名与商品一致；SVG 源文件位于 `book-covers/`。图片许可见 [ATTRIBUTION.md](ATTRIBUTION.md)。`retired-manifest.json` 保留旧图片的来源记录，供导入器识别已经导入的旧演示路径和修正错图；历史文件继续保留。

维护素材时，在 `api/` 下运行：

```powershell
python tools/prepare_product_images.py --verify
python tools/prepare_product_images.py --search '搜索词'
python tools/prepare_product_images.py --fetch --only toner
```

搜索只列出候选素材。查看画面、确认类型和许可后，更新 `catalog.json` 中对应的 `imageSource.titles`，再生成图片。脚本不会自动选择搜索结果。正常导入使用仓库图片，仅素材维护需要 Python、Pillow、中文字体和网络。

数据库修复回归测试使用本机 MySQL 的独立随机数据库，完成后自动删除；不连接 RocketMQ：

```powershell
$env:DEMO_IMAGE_INTEGRATION_TESTS = 'true'
mvn.cmd test '-Dtest=DemoImageCatalogRepairTests,DemoImageImportRepairIntegrationTests'
Remove-Item Env:\DEMO_IMAGE_INTEGRATION_TESTS
```
